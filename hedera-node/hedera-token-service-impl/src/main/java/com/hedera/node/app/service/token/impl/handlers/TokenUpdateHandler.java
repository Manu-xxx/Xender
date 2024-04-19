/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.hapi.fees.usage.crypto.CryptoOpsUsage.txnEstimateFactory;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.key.KeyUtils.isValid;
import static com.hedera.node.app.spi.validation.AttributeValidator.isKeyRemoval;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.base.TokenKeyValidation;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.fees.calculation.token.txns.TokenUpdateResourceUsage;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.util.TokenKeysHelper.TokenKeys;
import com.hedera.node.app.service.token.impl.validators.TokenUpdateValidator;
import com.hedera.node.app.service.token.records.TokenUpdateRecordBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides the state transition for token update.
 */
@Singleton
public class TokenUpdateHandler extends BaseTokenHandler implements TransactionHandler {
    private final TokenUpdateValidator tokenUpdateValidator;
    private static final Set<TokenKeys> TOKEN_KEYS = EnumSet.allOf(TokenKeys.class);

    @Inject
    public TokenUpdateHandler(@NonNull final TokenUpdateValidator tokenUpdateValidator) {
        this.tokenUpdateValidator = tokenUpdateValidator;
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenUpdateOrThrow();
        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);

        if (op.hasFeeScheduleKey()
                && !isKeyRemoval(op.feeScheduleKey())
                && op.keyVerificationMode() != TokenKeyValidation.NO_VALIDATION) {
            validateTruePreCheck(isValid(op.feeScheduleKey()), INVALID_CUSTOM_FEE_SCHEDULE_KEY);
        }
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenUpdateOrThrow();
        pureChecks(context.body());

        final var tokenId = op.tokenOrThrow();

        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var token = tokenStore.get(tokenId);
        if (token == null) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }
        addRequiredSigners(context, op, token);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenUpdateOrThrow();
        final var tokenId = op.tokenOrThrow();
        final var recordBuilder = context.recordBuilder(TokenUpdateRecordBuilder.class);

        // validate fields that involve config or state
        final var validationResult = tokenUpdateValidator.validateSemantics(context, op);
        // get the resolved expiry meta and token
        final var token = validationResult.token();
        final var resolvedExpiry = validationResult.resolvedExpiryMeta();

        final var accountStore = context.writableStore(WritableAccountStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var tokenStore = context.writableStore(WritableTokenStore.class);
        final var config = context.configuration();
        final var tokensConfig = config.getConfigData(TokensConfig.class);

        // If the operation has treasury change, then we need to check if the new treasury is valid
        // and if the treasury is not already associated with the token, see if it has auto associations
        // enabled and has open slots. If so, auto-associate.
        // We allow existing treasuries to have any nft balances left over, but the new treasury should
        // not have any balances left over. Transfer all balances for the current token to new treasury
        if (op.hasTreasury()) {
            final var existingTreasury = token.treasuryAccountId();
            final var newTreasury = op.treasuryOrThrow();
            final var newTreasuryAccount = getIfUsable(
                    newTreasury, accountStore, context.expiryValidator(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
            final var newTreasuryRel = tokenRelStore.get(newTreasury, tokenId);
            // If there is no treasury relationship, then we need to create one if auto associations are available.
            // If not fail
            if (newTreasuryRel == null) {
                final var newRelation = autoAssociate(newTreasuryAccount, token, accountStore, tokenRelStore, config);
                recordBuilder.addAutomaticTokenAssociation(
                        asTokenAssociation(newRelation.tokenId(), newRelation.accountId()));
            }
            // Treasury can be modified when it owns NFTs when the property "tokens.nfts.useTreasuryWildcards"
            // is enabled.
            if (!tokensConfig.nftsUseTreasuryWildcards() && token.tokenType().equals(NON_FUNGIBLE_UNIQUE)) {
                final var existingTreasuryRel = tokenRelStore.get(existingTreasury, tokenId);
                validateTrue(existingTreasuryRel != null, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
                final var tokenRelBalance = existingTreasuryRel.balance();
                validateTrue(tokenRelBalance == 0, CURRENT_TREASURY_STILL_OWNS_NFTS);
            }

            if (!newTreasury.equals(existingTreasury)) {
                final var existingTreasuryAccount = getIfUsable(
                        existingTreasury, accountStore, context.expiryValidator(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

                updateTreasuryTitles(existingTreasuryAccount, newTreasuryAccount, token, accountStore, tokenRelStore);
                // If the token is fungible, transfer fungible balance to new treasury
                // If it is non-fungible token transfer the ownership of the NFTs from old treasury to new treasury
                transferTokensToNewTreasury(existingTreasury, newTreasury, token, tokenRelStore, accountStore);
            }
        }
        final var tokenBuilder = customizeToken(token, resolvedExpiry, op);
        tokenStore.put(tokenBuilder.build());
        recordBuilder.tokenType(token.tokenType());
    }

    /**
     * Transfer tokens from old treasury to new treasury if the token is fungible. If the token is non-fungible,
     * transfer the ownership of the NFTs from old treasury to new treasury
     *
     * @param oldTreasury   old treasury account
     * @param newTreasury   new treasury account
     * @param token         token
     * @param tokenRelStore token relationship store
     * @param accountStore  account store
     */
    private void transferTokensToNewTreasury(
            final AccountID oldTreasury,
            final AccountID newTreasury,
            final Token token,
            final WritableTokenRelationStore tokenRelStore,
            final WritableAccountStore accountStore) {
        final var tokenId = token.tokenId();
        // Validate both accounts are not frozen and have the right keys
        final var oldTreasuryRel = getIfUsable(oldTreasury, tokenId, tokenRelStore);
        final var newTreasuryRel = getIfUsable(newTreasury, tokenId, tokenRelStore);
        if (oldTreasuryRel.balance() > 0) {
            validateFrozenAndKey(oldTreasuryRel);
            validateFrozenAndKey(newTreasuryRel);

            if (token.tokenType().equals(FUNGIBLE_COMMON)) {
                // Transfers fungible balances and updates account's numOfPositiveBalances
                // and puts to modifications on state.
                transferFungibleTokensToTreasury(oldTreasuryRel, newTreasuryRel, tokenRelStore, accountStore);
            } else {
                // Check whether new treasury has balance, if it does throw TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES
                validateTrue(newTreasuryRel.balance() == 0, TRANSACTION_REQUIRES_ZERO_TOKEN_BALANCES);
                // Transfers NFT ownerships and updates account's numOwnedNfts and
                // tokenRelation's balance and puts to modifications on state.
                changeOwnerToNewTreasury(oldTreasuryRel, newTreasuryRel, tokenRelStore, accountStore);
            }
        }
    }

    /**
     * Transfer fungible tokens from old treasury to new treasury. NOTE: This updates account's numOfPositiveBalances
     * and puts to modifications on state.
     *
     * @param fromTreasuryRel old treasury relationship
     * @param toTreasuryRel   new treasury relationship
     * @param tokenRelStore   token relationship store
     * @param accountStore    account store
     */
    private void transferFungibleTokensToTreasury(
            final TokenRelation fromTreasuryRel,
            final TokenRelation toTreasuryRel,
            final WritableTokenRelationStore tokenRelStore,
            final WritableAccountStore accountStore) {
        final var adjustment = fromTreasuryRel.balance();

        final var fromTreasury = accountStore.getAccountById(fromTreasuryRel.accountId());
        final var toTreasury = accountStore.getAccountById(toTreasuryRel.accountId());

        adjustBalance(fromTreasuryRel, fromTreasury, -adjustment, tokenRelStore, accountStore);
        adjustBalance(toTreasuryRel, toTreasury, adjustment, tokenRelStore, accountStore);
    }

    /**
     * Change the ownership of the NFTs from old treasury to new treasury. NOTE: This updates account's numOwnedNfts and
     * tokenRelation's balance and puts to modifications on state.
     *
     * @param fromTreasuryRel old treasury relationship
     * @param toTreasuryRel   new treasury relationship
     * @param tokenRelStore   token relationship store
     * @param accountStore    account store
     */
    private void changeOwnerToNewTreasury(
            final TokenRelation fromTreasuryRel,
            final TokenRelation toTreasuryRel,
            final WritableTokenRelationStore tokenRelStore,
            final WritableAccountStore accountStore) {
        final var fromTreasury = accountStore.getAccountById(fromTreasuryRel.accountId());
        final var toTreasury = accountStore.getAccountById(toTreasuryRel.accountId());

        final var fromRelBalance = fromTreasuryRel.balance();
        final var toRelBalance = toTreasuryRel.balance();

        final var fromNftsOwned = fromTreasury.numberOwnedNfts();
        final var toNftsOwned = toTreasury.numberOwnedNfts();

        final var fromTreasuryCopy = fromTreasury.copyBuilder();
        final var toTreasuryCopy = toTreasury.copyBuilder();
        final var fromRelCopy = fromTreasuryRel.copyBuilder();
        final var toRelCopy = toTreasuryRel.copyBuilder();

        // Update the number of positive balances and number of owned NFTs for old and new treasuries
        final var newFromPositiveBalancesCount =
                fromRelBalance > 0 ? fromTreasury.numberPositiveBalances() - 1 : fromTreasury.numberPositiveBalances();
        final var newToPositiveBalancesCount =
                toRelBalance > 0 ? toTreasury.numberPositiveBalances() + 1 : toTreasury.numberPositiveBalances();
        accountStore.put(fromTreasuryCopy
                .numberPositiveBalances(newFromPositiveBalancesCount)
                .numberOwnedNfts(fromNftsOwned - fromRelBalance)
                .build());
        accountStore.put(toTreasuryCopy
                .numberPositiveBalances(newToPositiveBalancesCount)
                .numberOwnedNfts(toNftsOwned + fromRelBalance)
                .build());
        tokenRelStore.put(fromRelCopy.balance(0).build());
        tokenRelStore.put(toRelCopy.balance(toRelBalance + fromRelBalance).build());
    }

    /**
     * Validate both KYC is granted and token is not frozen on the token.
     *
     * @param tokenRel token relationship
     */
    private void validateFrozenAndKey(final TokenRelation tokenRel) {
        validateTrue(!tokenRel.frozen(), ACCOUNT_FROZEN_FOR_TOKEN);
        validateTrue(tokenRel.kycGranted(), TOKEN_HAS_NO_KYC_KEY);
    }

    /**
     * Build a Token based on the given token update transaction body.
     *
     * @param token          token to be updated
     * @param resolvedExpiry resolved expiry
     * @param op             token update transaction body
     * @return updated token builder
     */
    private Token.Builder customizeToken(
            @NonNull final Token token,
            @NonNull final ExpiryMeta resolvedExpiry,
            @NonNull final TokenUpdateTransactionBody op) {
        final var copyToken = token.copyBuilder();
        // All these keys are validated in validateSemantics
        // If these keys did not exist on the token already, they can't be changed on update
        updateKeys(op, token, copyToken);
        updateExpiryFields(op, resolvedExpiry, copyToken);
        updateTokenAttributes(op, copyToken, token);
        return copyToken;
    }

    /**
     * Updates token name, token symbol, token metadata, token memo and token treasury if they are present in the token
     * update transaction body.
     *
     * @param op            token update transaction body
     * @param builder       token builder
     * @param originalToken original token
     */
    private void updateTokenAttributes(
            final TokenUpdateTransactionBody op, final Token.Builder builder, final Token originalToken) {
        if (op.symbol() != null && op.symbol().length() > 0) {
            builder.symbol(op.symbol());
        }
        if (op.name() != null && op.name().length() > 0) {
            builder.name(op.name());
        }
        if (op.hasMemo()) {
            builder.memo(op.memo());
        }
        if (op.hasMetadata()) {
            builder.metadata(op.metadata());
        }
        if (op.hasTreasury() && !op.treasuryOrThrow().equals(originalToken.treasuryAccountId())) {
            builder.treasuryAccountId(op.treasuryOrThrow());
        }
    }

    /**
     * Updates expiry fields of the token if they are present in the token update transaction body.
     *
     * @param op             token update transaction body
     * @param resolvedExpiry resolved expiry
     * @param builder        token builder
     */
    private void updateExpiryFields(
            final TokenUpdateTransactionBody op, final ExpiryMeta resolvedExpiry, final Token.Builder builder) {
        if (op.hasExpiry()) {
            builder.expirationSecond(resolvedExpiry.expiry());
        }
        if (op.hasAutoRenewPeriod()) {
            builder.autoRenewSeconds(resolvedExpiry.autoRenewPeriod());
        }
        if (op.hasAutoRenewAccount()) {
            builder.autoRenewAccountId(resolvedExpiry.autoRenewAccountId());
        }
    }

    /**
     * Updates keys of the token if they are present in the token update transaction body. All keys can be updates only
     * if they had already existed on the token. These keys can't be updated if they were not added during creation.
     *
     * @param op            token update transaction body
     * @param originalToken original token
     * @param builder       token builder
     */
    private void updateKeys(
            final TokenUpdateTransactionBody op, final Token originalToken, final Token.Builder builder) {
        TOKEN_KEYS.forEach(key -> key.updateKey(op, originalToken, builder));
    }

    /**
     * Add all signature requirements for TokenUpdateTx note: those requirements drastically changed after HIP-540
     *
     * @param context       pre handle context
     * @param op            token update transaction body
     * @param originalToken original token
     */
    private void addRequiredSigners(
            @NonNull PreHandleContext context,
            @NonNull final TokenUpdateTransactionBody op,
            @NonNull final Token originalToken)
            throws PreCheckException {
        if (op.hasAdminKey()) {
            if (isValid(op.adminKey())) {
                // if op admin key is valid we require the signature
                context.requireKey(op.adminKeyOrThrow());
            }
            if (originalToken.hasAdminKey()) {
                // if it's not, case is that the current admin key wants to
                // set itself to an invalid key, hence we require only the current admin key signature
                context.requireKey(originalToken.adminKey());
            }
        }

        if (op.hasTreasury()) {
            context.requireKeyOrThrow(op.treasuryOrThrow(), INVALID_ACCOUNT_ID);
        }
        if (op.hasAutoRenewAccount()) {
            context.requireKeyOrThrow(op.autoRenewAccountOrThrow(), INVALID_AUTORENEW_ACCOUNT);
        }

        // if we remove the admin key or any of the low priority keys, we should allow
        // only the admin key to sign.
        if (containsKeyRemoval(op)) {
            validateTruePreCheck(originalToken.hasAdminKey(), INVALID_SIGNATURE);
            context.requireKey(originalToken.adminKey());
            return;
        }

        List<Key> lowPriorityKeys;
        if (originalToken.hasAdminKey()) {
            // if we update any of the low priority keys, we should allow either admin key
            // or the respective low priority key to sign.
            if (noOtherFieldThanLowPriorityKeyOrMetadataWillBeUpdated(op)) {
                lowPriorityKeys = getAllRequiredLowPrioritySigners(originalToken, op);
                Key requiredKey;
                if (lowPriorityKeys.isEmpty()) {
                    requiredKey = originalToken.adminKey();
                } else {
                    final Key lowPriorityKeyList = Key.newBuilder()
                            .keyList(KeyList.newBuilder().keys(lowPriorityKeys).build())
                            .build();
                    final List<Key> keysRequired = List.of(lowPriorityKeyList, originalToken.adminKey());
                    // with this we will require either admin key signature or all low priority keys signatures
                    // (we can update several low priority keys with one TokenUpdate, so we will require all of these
                    // keys
                    // to sign)
                    requiredKey = Key.newBuilder()
                            .thresholdKey(ThresholdKey.newBuilder()
                                    .keys(KeyList.newBuilder()
                                            .keys(keysRequired)
                                            .build())
                                    .threshold(1)
                                    .build())
                            .build();
                }

                context.requireKey(requiredKey);
            } else if (!isExpiryOnlyUpdateOp(op)) {
                // if we update any other field, we need the current admin key to sign
                // note: for expiry only op admin key is not required
                context.requireKey(originalToken.adminKey());
            }
        } else {
            lowPriorityKeys = getAllRequiredLowPrioritySigners(originalToken, op);
            final Key lowPriorityKeyList = Key.newBuilder()
                    .keyList(KeyList.newBuilder().keys(lowPriorityKeys).build())
                    .build();
            context.requireKey(lowPriorityKeyList);
        }
    }

    private List<Key> getAllRequiredLowPrioritySigners(
            @NonNull final Token originalToken, @NonNull final TokenUpdateTransactionBody op) {
        List<Key> lowPriorityKeys = new ArrayList<>();
        addLowPrioritySigner(lowPriorityKeys, originalToken.hasWipeKey(), originalToken.wipeKey(), op.hasWipeKey());
        addLowPrioritySigner(lowPriorityKeys, originalToken.hasKycKey(), originalToken.kycKey(), op.hasKycKey());
        addLowPrioritySigner(
                lowPriorityKeys, originalToken.hasSupplyKey(), originalToken.supplyKey(), op.hasSupplyKey());
        addLowPrioritySigner(
                lowPriorityKeys, originalToken.hasFreezeKey(), originalToken.freezeKey(), op.hasFreezeKey());
        addLowPrioritySigner(
                lowPriorityKeys,
                originalToken.hasFeeScheduleKey(),
                originalToken.feeScheduleKey(),
                op.hasFeeScheduleKey());
        addLowPrioritySigner(lowPriorityKeys, originalToken.hasPauseKey(), originalToken.pauseKey(), op.hasPauseKey());
        addLowPrioritySigner(
                lowPriorityKeys, originalToken.hasMetadataKey(), originalToken.metadataKey(), op.hasMetadataKey());
        addLowPrioritySigner(
                lowPriorityKeys, originalToken.hasMetadataKey(), originalToken.metadataKey(), op.hasMetadata());

        return lowPriorityKeys;
    }

    private void addLowPrioritySigner(
            @NonNull List<Key> lowPriorityKeys,
            final boolean tokenHasKey,
            @NonNull final Key originalKey,
            final boolean updateHasField) {
        if (updateHasField) {
            if (tokenHasKey) {
                lowPriorityKeys.add(originalKey);
            }
        }
    }

    /**
     * If there is a change in treasury account, update the treasury titles of the old and new treasury accounts. NOTE :
     * This updated the numberTreasuryTitles on old and new treasury accounts. And also updates new treasury
     * relationship to not be frozen
     *
     * @param existingTreasuryAccount existing treasury account
     * @param newTreasuryAccount      new treasury account
     * @param originalToken           original token
     * @param accountStore            account store
     * @param tokenRelStore           token relation store
     */
    private void updateTreasuryTitles(
            @NonNull final Account existingTreasuryAccount,
            @NonNull final Account newTreasuryAccount,
            @NonNull final Token originalToken,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final WritableTokenRelationStore tokenRelStore) {
        final var newTokenRelation = tokenRelStore.get(newTreasuryAccount.accountId(), originalToken.tokenId());
        final var newRelCopy = newTokenRelation.copyBuilder();

        if (originalToken.hasFreezeKey()) {
            newRelCopy.frozen(false);
        }
        if (originalToken.hasKycKey()) {
            newRelCopy.kycGranted(true);
        }

        final var existingTreasuryTitles = existingTreasuryAccount.numberTreasuryTitles();
        final var newTreasuryAccountTitles = newTreasuryAccount.numberTreasuryTitles();
        final var copyOldTreasury =
                existingTreasuryAccount.copyBuilder().numberTreasuryTitles(existingTreasuryTitles - 1);
        final var copyNewTreasury = newTreasuryAccount.copyBuilder().numberTreasuryTitles(newTreasuryAccountTitles + 1);

        accountStore.put(copyOldTreasury.build());
        accountStore.put(copyNewTreasury.build());
        tokenRelStore.put(newRelCopy.build());
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        requireNonNull(feeContext);
        final var body = feeContext.body();
        final var op = body.tokenUpdateOrThrow();
        final var readableStore = feeContext.readableStore(ReadableTokenStore.class);
        final var token = readableStore.get(op.tokenOrThrow());

        return feeContext.feeCalculator(SubType.DEFAULT).legacyCalculate(sigValueObj -> new TokenUpdateResourceUsage(
                        txnEstimateFactory)
                .usageGiven(fromPbj(body), sigValueObj, token));
    }
}
