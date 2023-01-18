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
package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.*;
import static com.hedera.node.app.service.token.impl.util.AliasUtils.MISSING_NUM;
import static com.hedera.node.app.service.token.impl.util.AliasUtils.fromMirror;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.service.token.impl.entity.AccountBuilderImpl;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Accounts.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableAccountStore implements AccountKeyLookup {
    /** The underlying data storage class that holds the account data. */
    private final ReadableKVState<Long, MerkleAccount> accountState;
    /** The underlying data storage class that holds the aliases data built from the state. */
    private final ReadableKVState<String, Long> aliases;

    /**
     * Create a new {@link ReadableAccountStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableAccountStore(@NonNull final ReadableStates states) {
        this.accountState = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    /** {@inheritDoc} */
    @Override
    public KeyOrLookupFailureReason getKey(final AccountID idOrAlias) {
        final var account = getAccountLeaf(idOrAlias);
        if (account.isEmpty()) {
            return withFailureReason(INVALID_ACCOUNT_ID);
        }
        return validateKey(account.get().getAccountKey(), false);
    }

    /** {@inheritDoc} */
    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(
            @NonNull final AccountID idOrAlias) {
        final var account = getAccountLeaf(idOrAlias);
        if (account.isEmpty()) {
            return withFailureReason(INVALID_ACCOUNT_ID);
        }

        final var responseIgnoringSigReq = validateKey(account.get().getAccountKey(), false);
        if (responseIgnoringSigReq.failed() || account.get().isReceiverSigRequired()) {
            return responseIgnoringSigReq;
        } else {
            return PRESENT_BUT_NOT_REQUIRED;
        }
    }

    /** {@inheritDoc} */
    @Override
    public KeyOrLookupFailureReason getKey(@NonNull final ContractID idOrAlias) {
        final var optContract = getAccountLeaf(asAccount(idOrAlias));
        if (optContract.isEmpty()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        }
        final var contract = optContract.get();
        if (contract.isDeleted() || !contract.isSmartContract()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        }
        return validateKey(contract.getAccountKey(), true);
    }

    /** {@inheritDoc} */
    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(
            @NonNull final ContractID idOrAlias) {
        final var optContract = getAccountLeaf(asAccount(idOrAlias));
        if (optContract.isEmpty()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        }

        final var contract = optContract.get();
        if (contract.isDeleted() || !contract.isSmartContract()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        }

        final var responseIgnoringSigReq = validateKey(contract.getAccountKey(), true);
        if (responseIgnoringSigReq.failed() || contract.isReceiverSigRequired()) {
            return responseIgnoringSigReq;
        }
        return PRESENT_BUT_NOT_REQUIRED;
    }

    /**
     * Returns the {@link Account} for a given {@link AccountID}
     *
     * @param idOrAlias the {@code AccountID} which {@code Account is requested}
     * @return an {@link Optional} with the {@code Account}, if it was found, an empty {@code
     *     Optional} otherwise
     */
    public Optional<Account> getAccount(@NonNull final AccountID idOrAlias) {
        return getAccountLeaf(idOrAlias).map(accountLeaf -> mapAccount(idOrAlias, accountLeaf));
    }

    /**
     * Returns the account leaf for the given account number. If the account doesn't exist returns
     * {@code Optional.empty()}
     *
     * @param id given account number
     * @return merkle leaf for the given account number
     */
    private Optional<HederaAccount> getAccountLeaf(final AccountID id) {
        final var accountNum = getAccountNum(id);
        if (accountNum.equals(MISSING_NUM)) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountState.get(accountNum));
    }

    /**
     * Get account number if the provided account id is an alias. If not, returns the account's
     * number
     *
     * @param id provided account id
     * @return account number
     */
    private Long getAccountNum(final AccountID id) {
        if (isAlias(id)) {
            final var alias = id.getAlias();
            if (alias.size() == EVM_ADDRESS_SIZE) {
                final var evmAddress = alias.toByteArray();
                if (isMirror(evmAddress)) {
                    return fromMirror(evmAddress);
                }
            }

            final var ret = aliases.get(alias.toStringUtf8());
            return ret == null ? MISSING_NUM : ret;
        }
        return id.getAccountNum();
    }

    private KeyOrLookupFailureReason validateKey(
            @Nullable final JKey key, final boolean isContractKey) {
        if (key == null || key.isEmpty()) {
            if (isContractKey) {
                return withFailureReason(MODIFYING_IMMUTABLE_CONTRACT);
            }
            return withFailureReason(ACCOUNT_IS_IMMUTABLE);
        } else if (isContractKey && key instanceof JContractIDKey) {
            return withFailureReason(MODIFYING_IMMUTABLE_CONTRACT);
        } else {
            return withKey(key);
        }
    }

    private Account mapAccount(final AccountID idOrAlias, final HederaAccount account) {
        final var builder =
                new AccountBuilderImpl()
                        .key(account.getAccountKey())
                        .expiry(account.getExpiry())
                        .balance(account.getBalance())
                        .memo(account.getMemo())
                        .deleted(account.isDeleted())
                        .receiverSigRequired(account.isReceiverSigRequired())
                        .numberOfOwnedNfts(account.getNftsOwned())
                        .maxAutoAssociations(account.getMaxAutomaticAssociations())
                        .usedAutoAssociations(account.getUsedAutoAssociations())
                        .numAssociations(account.getNumAssociations())
                        .numPositiveBalances(account.getNumPositiveBalances())
                        .ethereumNonce(account.getEthereumNonce())
                        .stakedToMe(account.getStakedToMe())
                        .stakePeriodStart(account.getStakePeriodStart())
                        .stakedNum(account.totalStake())
                        .declineReward(account.isDeclinedReward())
                        .stakeAtStartOfLastRewardedPeriod(account.getStakePeriodStart())
                        .autoRenewSecs(account.getAutoRenewSecs())
                        .accountNumber(idOrAlias.getAccountNum())
                        .isSmartContract(account.isSmartContract());
        if (account.getAutoRenewAccount() != null) {
            builder.autoRenewAccountNumber(account.getAutoRenewAccount().num());
        }
        if (account.getAlias() != null) {
            builder.alias(account.getAlias().toByteArray());
        }
        return builder.build();
    }
}
