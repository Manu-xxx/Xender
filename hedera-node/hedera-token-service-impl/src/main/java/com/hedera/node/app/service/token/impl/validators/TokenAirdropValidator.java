/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_AIRDROP_WITH_FALLBACK_ROYALTY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsableForAliasedId;
import static com.hedera.node.app.service.token.impl.validators.CryptoTransferValidator.validateTokenTransfers;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenAirdropValidator {

    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenAirdropValidator() {
        // For Dagger injection
    }

    /**
     * Performs pure checks that validates basic fields in the token airdrop transaction.
     *
     * @param op the token airdrop transaction body
     * @throws PreCheckException if any of the checks fail
     */
    public void pureChecks(@NonNull final TokenAirdropTransactionBody op) throws PreCheckException {
        final var tokenTransfers = op.tokenTransfers();
        // If there is not exactly one debit we throw an exception
        for (var tokenTransfer : tokenTransfers) {
            if (tokenTransfer.transfers().isEmpty()) {
                // NFT transfers, skip this check
                continue;
            }
            List<AccountAmount> negativeTransfers = tokenTransfer.transfers().stream()
                    .filter(fungibleTransfer -> fungibleTransfer.amount() < 0)
                    .toList();
            if (negativeTransfers.size() != 1) {
                throw new PreCheckException(INVALID_TRANSACTION_BODY);
            }
        }
        validateTokenTransfers(op.tokenTransfers(), CryptoTransferValidator.AllowanceStrategy.ALLOWANCES_REJECTED);
    }

    public void validateSemantics(
            @NonNull final HandleContext context,
            @NonNull final TokenAirdropTransactionBody op,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelStore,
            @NonNull final ReadableNftStore nftStore) {
        var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        validateTrue(
                op.tokenTransfers().size() <= tokensConfig.maxAllowedAirdropTransfersPerTx(),
                TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED);

        for (final var xfers : op.tokenTransfers()) {
            final var tokenId = xfers.tokenOrThrow();
            final var token = getIfUsable(tokenId, tokenStore);

            // process fungible token transfers if any.
            // PureChecks validates there is only one debit, so findFirst should return one item
            if (!xfers.transfers().isEmpty()) {
                final var senderAccountAmount = xfers.transfers().stream()
                        .filter(item -> item.amount() < 0)
                        .findFirst();
                final var senderId = senderAccountAmount.orElseThrow().accountIDOrThrow();
                final var senderAccount =
                        getIfUsableForAliasedId(senderId, accountStore, context.expiryValidator(), INVALID_ACCOUNT_ID);
                // 1. Validate token associations
                validateFungibleTransfers(
                        context.payer(), senderAccount, tokenId, senderAccountAmount.get(), tokenRelStore);
            }

            // process non-fungible tokens transfers if any
            if (!xfers.nftTransfers().isEmpty()) {
                for (var transfer : xfers.nftTransfers()) {
                    final var receiver = transfer.receiverAccountID();
                    if (!isExemptFromCustomFees(token, receiver)) {
                        validateTrue(
                                tokenHasNoRoyaltyWithFallbackFee(token.tokenId(), tokenStore),
                                TOKEN_AIRDROP_WITH_FALLBACK_ROYALTY);
                    }
                }

                // 1. validate NFT transfers
                final var nftTransfer = xfers.nftTransfers().stream().findFirst();
                final var senderId = nftTransfer.orElseThrow().senderAccountIDOrThrow();
                final var senderAccount = accountStore.getAliasedAccountById(senderId);
                validateTrue(senderAccount != null, INVALID_ACCOUNT_ID);
                validateNftTransfers(
                        context.payer(),
                        senderAccount,
                        tokenId,
                        xfers.nftTransfers(),
                        tokenRelStore,
                        tokenStore,
                        nftStore);
            }
        }
    }

    /**
     * When we do an airdrop we need to check if there are custom fees that needs to be paid by the receiver.
     * If there are, an error is returned from the HAPI call.
     * However, there is an exception to this rule - if the receiver is the fee collector or the treasury account
     * they are exempt from paying the custom fees thus we don't need to check if there are custom fees.
     * This method returns if the receiver is the fee collector or the treasury account.
     */
    private static boolean isExemptFromCustomFees(Token token, AccountID receiverId) {
        return token.customFees().stream()
                .anyMatch(customFee -> CustomFeeExemptions.isPayerExempt(token, customFee, receiverId));
    }

    public boolean tokenHasNoRoyaltyWithFallbackFee(TokenID tokenId, ReadableTokenStore tokenStore) {
        final var token = getIfUsable(tokenId, tokenStore);
        if (token.tokenType().equals(TokenType.NON_FUNGIBLE_UNIQUE)) {
            for (var fee : token.customFees()) {
                if (fee.hasRoyaltyFee() && requireNonNull(fee.royaltyFee()).hasFallbackFee()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void validateFungibleTransfers(
            final AccountID payer,
            final Account senderAccount,
            final TokenID tokenId,
            final AccountAmount senderAmount,
            final ReadableTokenRelationStore tokenRelStore) {
        // validate association and account frozen
        final var tokenRel = getIfUsable(senderAccount.accountIdOrThrow(), tokenId, tokenRelStore);
        validateTrue(tokenRel.balance() >= Math.abs(senderAmount.amount()), INSUFFICIENT_TOKEN_BALANCE);
    }

    private void validateNftTransfers(
            @NonNull final AccountID payer,
            @NonNull final Account senderAccount,
            @NonNull final TokenID tokenId,
            @NonNull final List<NftTransfer> nftTransfers,
            @NonNull final ReadableTokenRelationStore tokenRelStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final ReadableNftStore nftStore) {
        // validate association and account frozen
        getIfUsable(senderAccount.accountIdOrThrow(), tokenId, tokenRelStore);

        final var token = tokenStore.get(tokenId);
        validateTrue(token != null, INVALID_TOKEN_ID);

        for (NftTransfer nftTransfer : nftTransfers) {
            final var nft = nftStore.get(tokenId, nftTransfer.serialNumber());
            validateTrue(nft != null, INVALID_NFT_ID);
            // owner of nft should match the sender in transfer list
            if (nft.hasOwnerId()) {
                validateTrue(nft.ownerId() != null, INVALID_NFT_ID);
                validateTrue(nft.ownerId().equals(senderAccount.accountId()), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            } else {
                final var treasuryId = token.treasuryAccountId();
                validateTrue(treasuryId != null, INVALID_ACCOUNT_ID);
                validateTrue(treasuryId.equals(senderAccount.accountId()), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
            }
        }
    }
}
