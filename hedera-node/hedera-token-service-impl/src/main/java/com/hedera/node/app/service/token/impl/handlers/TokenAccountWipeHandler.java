/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_WIPING_AMOUNT;
import static com.hedera.node.app.service.token.impl.validators.TokenSupplyChangeOpsValidator.burnPureChecks;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.util.TokenHandlerHelper;
import com.hedera.node.app.service.token.impl.validators.TokenSupplyChangeOpsValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_ACCOUNT_WIPE}.
 */
@Singleton
public final class TokenAccountWipeHandler implements TransactionHandler {
    @NonNull
    private final TokenSupplyChangeOpsValidator validator;

    @Inject
    public TokenAccountWipeHandler(@NonNull final TokenSupplyChangeOpsValidator validator) {
        this.validator = validator;
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenWipeOrThrow();
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMeta = tokenStore.getTokenMeta(op.tokenOrElse(TokenID.DEFAULT));
        if (tokenMeta == null) throw new PreCheckException(INVALID_TOKEN_ID);
        if (tokenMeta.hasWipeKey()) {
            context.requireKey(tokenMeta.wipeKey());
        }
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.tokenWipeOrThrow();

        // All the pure checks for burning a token must also be checked for wiping a token
        burnPureChecks(op.amount(), op.serialNumbers(), op.hasToken(), INVALID_WIPING_AMOUNT);

        validateTruePreCheck(op.hasAccount(), INVALID_ACCOUNT_ID);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        // Set up the stores and helper objects needed
        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var nftStore = context.writableStore(WritableNftStore.class);
        final var expiryValidator = context.expiryValidator();

        // Assign relevant variables
        final var txn = context.body();
        final var op = txn.tokenWipeOrThrow();
        final var accountId = op.account();
        final var tokenId = op.token();
        final var fungibleBurnCount = op.amount();
        // Wrapping the serial nums this way de-duplicates the serial nums:
        final var nftSerialNums = new ArrayList<>(new LinkedHashSet<>(op.serialNumbers()));

        // Validate the semantics of the transaction
        final var validated = validateSemantics(
                accountId,
                tokenId,
                fungibleBurnCount,
                nftSerialNums,
                accountStore,
                tokenStore,
                tokenRelStore,
                expiryValidator);
        final var acct = validated.account();
        final var token = validated.token();

        final long newTotalSupply;
        final long newAccountBalance;
        final Account.Builder updatedAcctBuilder = validated.account().copyBuilder();
        if (token.tokenType() == TokenType.FUNGIBLE_COMMON) {
            // Check that the new total supply will not be negative
            newTotalSupply = token.totalSupply() - fungibleBurnCount;
            validateTrue(newTotalSupply >= 0, INVALID_WIPING_AMOUNT);

            // Check that the new token balance will not be negative
            newAccountBalance = validated.accountTokenRel().balance() - fungibleBurnCount;
            validateTrue(newAccountBalance >= 0, INVALID_WIPING_AMOUNT);
        } else {
            // Check that the new total supply will not be negative
            newTotalSupply = token.totalSupply() - nftSerialNums.size();
            validateTrue(newTotalSupply >= 0, INVALID_WIPING_AMOUNT);

            // Load and validate the nfts
            for (final Long nftSerial : nftSerialNums) {
                final var nft = nftStore.get(tokenId, nftSerial);
                validateTrue(nft != null, INVALID_NFT_ID);

                final var nftOwner = nft.ownerNumber();
                validateTrue(nftOwner == accountId.accountNum(), ACCOUNT_DOES_NOT_OWN_WIPED_NFT);
            }

            // Check that the new token balance will not be negative
            newAccountBalance = validated.accountTokenRel().balance() - nftSerialNums.size();
            validateTrue(newAccountBalance >= 0, INVALID_WIPING_AMOUNT);

            // Update the NFT count for the account
            updatedAcctBuilder.numberOwnedNfts(acct.numberOwnedNfts() - nftSerialNums.size());

            // Remove the NFTs
            nftSerialNums.forEach(serialNum -> nftStore.remove(tokenId, serialNum));
        }

        // Finally, record all the changes
        if (newAccountBalance == 0) {
            updatedAcctBuilder.numberPositiveBalances(acct.numberPositiveBalances() - 1);
        }
        accountStore.put(updatedAcctBuilder.build());
        tokenStore.put(token.copyBuilder().totalSupply(newTotalSupply).build());
        tokenRelStore.put(validated
                .accountTokenRel()
                .copyBuilder()
                .balance(newAccountBalance)
                .build());
    }

    private ValidationResult validateSemantics(
            @NonNull final AccountID accountId,
            @NonNull final TokenID tokenId,
            final long fungibleBurnCount,
            @NonNull final List<Long> nftSerialNums,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final ReadableTokenRelationStore tokenRelStore,
            @NonNull final ExpiryValidator expiryValidator) {
        validateTrue(fungibleBurnCount > -1, INVALID_WIPING_AMOUNT);

        final var account = TokenHandlerHelper.getIfUsable(accountId, accountStore, expiryValidator);

        validator.validateWipe(fungibleBurnCount, nftSerialNums);

        final var token = TokenHandlerHelper.getIfUsable(tokenId, tokenStore);
        validateTrue(token.wipeKey() != null, ResponseCodeEnum.TOKEN_HAS_NO_WIPE_KEY);

        final var accountRel = TokenHandlerHelper.getIfUsable(accountId, tokenId, tokenRelStore);
        validateFalse(
                token.treasuryAccountNumber() == accountRel.accountNumber(),
                ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT);

        return new ValidationResult(account, token, accountRel);
    }

    private record ValidationResult(
            @NonNull Account account, @NonNull Token token, @NonNull TokenRelation accountTokenRel) {}
}
