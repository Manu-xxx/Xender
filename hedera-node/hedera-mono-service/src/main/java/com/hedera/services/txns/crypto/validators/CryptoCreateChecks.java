/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto.validators;

import static com.hedera.services.ethereum.EthTxSigs.recoverAddressFromPubKey;
import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALIAS_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_REQUIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.STAKING_NOT_ENABLED;

import com.google.protobuf.ByteString;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Arrays;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CryptoCreateChecks {
    static final int MAX_CHARGEABLE_AUTO_ASSOCIATIONS = 5000;
    private final GlobalDynamicProperties dynamicProperties;
    private final OptionValidator validator;
    private final Supplier<AccountStorageAdapter> accounts;
    private final NodeInfo nodeInfo;
    private final AliasManager aliasManager;

    @Inject
    public CryptoCreateChecks(
            final GlobalDynamicProperties dynamicProperties,
            final OptionValidator validator,
            final Supplier<AccountStorageAdapter> accounts,
            final NodeInfo nodeInfo,
            final AliasManager aliasManager) {
        this.dynamicProperties = dynamicProperties;
        this.validator = validator;
        this.accounts = accounts;
        this.nodeInfo = nodeInfo;
        this.aliasManager = aliasManager;
    }

    @SuppressWarnings("java:S1874")
    public ResponseCodeEnum cryptoCreateValidation(final CryptoCreateTransactionBody op) {

        var memoValidity = validator.memoCheck(op.getMemo());
        if (memoValidity != OK) {
            return memoValidity;
        }

        var kayAliasAndEvmAddressCombinationsValidity =
                validateKeyAliasAndEvmAddressCombinations(op);
        if (kayAliasAndEvmAddressCombinationsValidity != OK) {
            return kayAliasAndEvmAddressCombinationsValidity;
        }

        if (op.getInitialBalance() < 0L) {
            return INVALID_INITIAL_BALANCE;
        }
        if (!op.hasAutoRenewPeriod()) {
            return INVALID_RENEWAL_PERIOD;
        }
        if (!validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod())) {
            return AUTORENEW_DURATION_NOT_IN_RANGE;
        }
        if (op.getSendRecordThreshold() < 0L) {
            return INVALID_SEND_RECORD_THRESHOLD;
        }
        if (op.getReceiveRecordThreshold() < 0L) {
            return INVALID_RECEIVE_RECORD_THRESHOLD;
        }
        if (tooManyAutoAssociations(op.getMaxAutomaticTokenAssociations())) {
            return REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
        }
        if (op.hasProxyAccountID()
                && !op.getProxyAccountID().equals(AccountID.getDefaultInstance())) {
            return PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
        }
        final var stakedIdCase = op.getStakedIdCase().name();
        final var electsStakingId = hasStakedId(stakedIdCase);
        if (!dynamicProperties.isStakingEnabled() && (electsStakingId || op.getDeclineReward())) {
            return STAKING_NOT_ENABLED;
        }
        if (electsStakingId
                && !validator.isValidStakedId(
                        stakedIdCase,
                        op.getStakedAccountId(),
                        op.getStakedNodeId(),
                        accounts.get(),
                        nodeInfo)) {
            return INVALID_STAKING_ID;
        }
        return OK;
    }

    private ResponseCodeEnum tryToRecoverEVMAddressAndCheckValidity(final byte[] key) {
        var recoveredEVMAddress = recoverAddressFromPubKey(key);
        if (recoveredEVMAddress != null) {
            return isUsedAsAliasCheck(ByteString.copyFrom(recoveredEVMAddress));
        }
        return OK;
    }

    private ResponseCodeEnum validateKey(final CryptoCreateTransactionBody op) {
        if (!validator.hasGoodEncoding(op.getKey())) {
            return BAD_ENCODING;
        }
        var fcKey = asFcKeyUnchecked(op.getKey());
        if (fcKey.isEmpty()) {
            return KEY_REQUIRED;
        }
        if (!fcKey.isValid()) {
            return INVALID_ADMIN_KEY;
        }
        return OK;
    }

    private ResponseCodeEnum validateEcdsaKey(final ByteString ecdsaKey, final byte[] evmAddress) {
        if (ecdsaKey.isEmpty()) {
            return INVALID_ADMIN_KEY;
        }
        final var recoveredEvmAddress = recoverAddressFromPubKey(ecdsaKey.toByteArray());
        if (!Arrays.equals(recoveredEvmAddress, evmAddress)) {
            return INVALID_ALIAS_KEY;
        }
        return OK;
    }

    private boolean tooManyAutoAssociations(final int n) {
        return n > MAX_CHARGEABLE_AUTO_ASSOCIATIONS
                || (dynamicProperties.areTokenAssociationsLimited()
                        && n > dynamicProperties.maxTokensPerAccount());
    }

    private ResponseCodeEnum isUsedAsAliasCheck(final ByteString alias) {
        if (!aliasManager.lookupIdBy(alias).equals(MISSING_NUM)) {
            return INVALID_ALIAS_KEY;
        }
        return OK;
    }

    private ResponseCodeEnum validateKeyAliasAndEvmAddressCombinations(
            final CryptoCreateTransactionBody op) {
        if (onlyKeyProvided(op)) {
            return validateOnlyKeyProvidedCase(op);
        } else if (keyAndAliasProvided(op)) {
            return validateKeyAndAliasProvidedCase(op);
        } else if (keyAndAliasAndEvmAddressProvided(op)) {
            return validateKeyAndAliasAndEVMAddressProvidedCase(op);
        } else if (keyAndEvmAddressProvided(op)) {
            return validateKeyAndEvmAddressProvidedCase(op);
        } else if (noKeyAndNoAliasAndNoEvmAddressProvided(op)) {
            return KEY_REQUIRED;
        } else if (onlyAliasProvided(op)) {
            return validateOnlyAliasProvidedCase(op);
        } else if (aliasAndEvmAddressProvided(op)) {
            return validateAliasAndEvmAddressProvidedCase(op);
        } else if (onlyEvmAddressProvided(op)) {
            return validateOnlyEvmAddressProvidedCase(op);
        }
        return OK;
    }

    private static boolean onlyKeyProvided(CryptoCreateTransactionBody op) {
        return op.hasKey() && op.getAlias().isEmpty() && op.getEvmAddress().isEmpty();
    }

    private static boolean keyAndAliasProvided(CryptoCreateTransactionBody op) {
        return op.hasKey() && !op.getAlias().isEmpty() && op.getEvmAddress().isEmpty();
    }

    private static boolean keyAndAliasAndEvmAddressProvided(CryptoCreateTransactionBody op) {
        return op.hasKey() && !op.getAlias().isEmpty() && !op.getEvmAddress().isEmpty();
    }

    private static boolean keyAndEvmAddressProvided(CryptoCreateTransactionBody op) {
        return op.hasKey() && op.getAlias().isEmpty() && !op.getEvmAddress().isEmpty();
    }

    private static boolean noKeyAndNoAliasAndNoEvmAddressProvided(CryptoCreateTransactionBody op) {
        return !op.hasKey() && op.getAlias().isEmpty() && op.getEvmAddress().isEmpty();
    }

    private static boolean onlyAliasProvided(CryptoCreateTransactionBody op) {
        return !op.hasKey() && !op.getAlias().isEmpty() && op.getEvmAddress().isEmpty();
    }

    private static boolean aliasAndEvmAddressProvided(CryptoCreateTransactionBody op) {
        return !op.hasKey() && !op.getAlias().isEmpty() && !op.getEvmAddress().isEmpty();
    }

    private static boolean onlyEvmAddressProvided(CryptoCreateTransactionBody op) {
        return !op.hasKey() && op.getAlias().isEmpty() && !op.getEvmAddress().isEmpty();
    }

    private ResponseCodeEnum validateOnlyKeyProvidedCase(final CryptoCreateTransactionBody op) {
        final var keyValidity = validateKey(op);
        if (keyValidity != OK) {
            return keyValidity;
        }

        if (!op.getKey().getECDSASecp256K1().isEmpty()) {
            var isKeyUsedAsAliasValidity = isUsedAsAliasCheck(op.getKey().getECDSASecp256K1());

            if (isKeyUsedAsAliasValidity != OK) {
                return isKeyUsedAsAliasValidity;
            }

            return tryToRecoverEVMAddressAndCheckValidity(
                    op.getKey().getECDSASecp256K1().toByteArray());
        }
        return OK;
    }

    private ResponseCodeEnum validateKeyAndAliasProvidedCase(final CryptoCreateTransactionBody op) {
        if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
            return NOT_SUPPORTED;
        }
        final var keyValidity = validateKey(op);
        if (keyValidity != OK) {
            return keyValidity;
        }

        var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());
        var key = op.getKey();
        if ((!key.getEd25519().isEmpty() || !key.getECDSASecp256K1().isEmpty())
                && !key.equals(keyFromAlias)) {
            return INVALID_ALIAS_KEY;
        }

        var isAliasUsedCheck = isUsedAsAliasCheck(op.getAlias());

        if (isAliasUsedCheck != OK) {
            return isAliasUsedCheck;
        }

        if (!keyFromAlias.getECDSASecp256K1().isEmpty()) {

            return tryToRecoverEVMAddressAndCheckValidity(
                    keyFromAlias.getECDSASecp256K1().toByteArray());
        }

        return OK;
    }

    private ResponseCodeEnum validateKeyAndEvmAddressProvidedCase(
            final CryptoCreateTransactionBody op) {
        if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
            return NOT_SUPPORTED;
        }
        final var keyValidity = validateKey(op);
        if (keyValidity != OK) {
            return keyValidity;
        }

        var isEvmAddressUsedCheck = isUsedAsAliasCheck(op.getEvmAddress());
        if (isEvmAddressUsedCheck != OK) {
            return isEvmAddressUsedCheck;
        }

        final var ecdsaKeyValidity =
                validateEcdsaKey(op.getKey().getECDSASecp256K1(), op.getEvmAddress().toByteArray());
        if (ecdsaKeyValidity != OK) {
            return ecdsaKeyValidity;
        }
        return OK;
    }

    private ResponseCodeEnum validateKeyAndAliasAndEVMAddressProvidedCase(
            final CryptoCreateTransactionBody op) {
        if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
            return NOT_SUPPORTED;
        }
        final var keyValidity = validateKey(op);
        if (keyValidity != OK) {
            return keyValidity;
        }

        var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());
        if (!op.getKey().equals(keyFromAlias)) {
            return INVALID_ALIAS_KEY;
        }

        var isEvmAddressUsedCheck = isUsedAsAliasCheck(op.getEvmAddress());
        if (isEvmAddressUsedCheck != OK) {
            return isEvmAddressUsedCheck;
        }

        final var ecdsaKeyValidity =
                validateEcdsaKey(op.getKey().getECDSASecp256K1(), op.getEvmAddress().toByteArray());
        if (ecdsaKeyValidity != OK) {
            return ecdsaKeyValidity;
        }
        return OK;
    }

    private ResponseCodeEnum validateOnlyAliasProvidedCase(final CryptoCreateTransactionBody op) {
        if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
            return NOT_SUPPORTED;
        }
        var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());
        if (!validator.hasGoodEncoding(keyFromAlias)) {
            return BAD_ENCODING;
        }
        var fcKey = asFcKeyUnchecked(keyFromAlias);
        if (fcKey.isEmpty()) {
            return KEY_REQUIRED;
        }

        var isAliasUsedCheck = isUsedAsAliasCheck(op.getAlias());

        if (isAliasUsedCheck != OK) {
            return isAliasUsedCheck;
        }

        if (!keyFromAlias.getECDSASecp256K1().isEmpty()) {

            return tryToRecoverEVMAddressAndCheckValidity(
                    keyFromAlias.getECDSASecp256K1().toByteArray());
        }
        return OK;
    }

    private ResponseCodeEnum validateAliasAndEvmAddressProvidedCase(
            final CryptoCreateTransactionBody op) {
        if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
            return NOT_SUPPORTED;
        }

        var isEvmAddressUsedCheck = isUsedAsAliasCheck(op.getEvmAddress());
        if (isEvmAddressUsedCheck != OK) {
            return isEvmAddressUsedCheck;
        }

        var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());
        final var ecdsaKeyValidity =
                validateEcdsaKey(
                        keyFromAlias.getECDSASecp256K1(), op.getEvmAddress().toByteArray());
        if (ecdsaKeyValidity != OK) {
            return ecdsaKeyValidity;
        }
        return OK;
    }

    private ResponseCodeEnum validateOnlyEvmAddressProvidedCase(
            final CryptoCreateTransactionBody op) {
        if (!dynamicProperties.isCryptoCreateWithAliasEnabled()) {
            return NOT_SUPPORTED;
        }
        if (!dynamicProperties.isLazyCreationEnabled()) {
            return NOT_SUPPORTED;
        }

        var isEvmAddressUsedCheck = isUsedAsAliasCheck(op.getEvmAddress());
        if (isEvmAddressUsedCheck != OK) {
            return isEvmAddressUsedCheck;
        }
        return OK;
    }
}
