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

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.evm.contracts.execution.StaticProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Accounts.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableAccountStore implements AccountAccess {
    public static final int EVM_ADDRESS_LEN = 20;
    private static final byte[] MIRROR_PREFIX = new byte[12];

    static {
        /* A placeholder to store the 12-byte prefix (4-byte shard and 8-byte realm) that marks an EVM
         * address as a "mirror" address that follows immediately from a <shard>.<realm>.<num> id. */
        System.arraycopy(Longs.toByteArray(StaticProperties.getShard()), 4, MIRROR_PREFIX, 0, 4);
        System.arraycopy(Longs.toByteArray(StaticProperties.getRealm()), 0, MIRROR_PREFIX, 4, 8);
    }

    /** The underlying data storage class that holds the account data. */
    private final ReadableKVState<EntityNumVirtualKey, Account> accountState;
    /** The underlying data storage class that holds the aliases data built from the state. */
    private final ReadableKVState<String, EntityNumValue> aliases;

    /**
     * Create a new {@link ReadableAccountStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableAccountStore(@NonNull final ReadableStates states) {
        this.accountState = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    static boolean isMirror(final Bytes bytes) {
        return bytes.matchesPrefix(MIRROR_PREFIX);
    }

    /**
     * Returns the {@link Account} for a given {@link AccountID}
     *
     * @param accountID the {@code AccountID} which {@code Account is requested}
     * @return an {@link Optional} with the {@code Account}, if it was found, an empty {@code
     *     Optional} otherwise
     */
    @Override
    @Nullable
    public Account getAccountById(@NonNull final AccountID accountID) {
        final var account = getAccountLeaf(accountID);
        return account == null ? null : account;
    }

    /* Helper methods */

    /**
     * Returns the account leaf for the given account id. If the account doesn't exist, returns
     * {@link Optional}.
     *
     * @param id given account number
     * @return merkle leaf for the given account number
     */
    @Nullable
    Account getAccountLeaf(@NonNull final AccountID id) {
        // Get the account number based on the account identifier. It may be null.
        final var accountOneOf = id.account();
        final Long accountNum =
                switch (accountOneOf.kind()) {
                    case ACCOUNT_NUM -> accountOneOf.as();
                    case ALIAS -> {
                        final Bytes alias = accountOneOf.as();
                        if (alias.length() == EVM_ADDRESS_LEN && isMirror(alias)) {
                            yield fromMirror(alias);
                        } else {
                            final var entityNum = aliases.get(alias.asUtf8String());
                            yield entityNum == null ? EntityNumValue.DEFAULT.num() : entityNum.num();
                        }
                    }
                    case UNSET -> EntityNumValue.DEFAULT.num();
                };

        return accountNum == null ? null : accountState.get(EntityNumVirtualKey.fromLong(accountNum));
    }

    /**
     * Returns the contract leaf for the given contract id. If the contract doesn't exist returns
     * {@code Optional.empty()}
     *
     * @param id given contract number
     * @return merkle leaf for the given contract number
     */
    @Nullable
    private Account getContractLeaf(@NonNull final ContractID id) {
        // Get the contract number based on the contract identifier. It may be null.
        final var contractOneOf = id.contract();
        final Long contractNum =
                switch (contractOneOf.kind()) {
                    case CONTRACT_NUM -> contractOneOf.as();
                    case EVM_ADDRESS -> {
                        // If the evm address is of "long-zero" format, then parse out the contract
                        // num from those bytes
                        final Bytes evmAddress = contractOneOf.as();
                        if (isMirror(evmAddress)) {
                            yield numOfMirror(evmAddress);
                        }

                        // The evm address is some kind of alias.
                        var entityNum = aliases.get(evmAddress.asUtf8String());

                        // If we didn't find an alias, we will want to auto-create this account. But
                        // we don't want to auto-create an account if there is already another
                        // account in the system with the same EVM address that we would have auto-created.
                        if (evmAddress.length() > EVM_ADDRESS_LEN && entityNum == null) {
                            // if we don't find entity num for key alias we can try to derive EVM
                            // address from it and look it up
                            final var evmKeyAliasAddress = keyAliasToEVMAddress(evmAddress);
                            if (evmKeyAliasAddress != null) {
                                entityNum = aliases.get(
                                        ByteString.copyFrom(evmKeyAliasAddress).toStringUtf8());
                            }
                        }
                        yield entityNum == null ? EntityNumValue.DEFAULT.num() : entityNum.num();
                    }
                    case UNSET -> EntityNumValue.DEFAULT.num();
                };

        return contractNum == null ? null : accountState.get(EntityNumVirtualKey.fromLong(contractNum));
    }

    private static long numFromEvmAddress(final Bytes bytes) {
        return bytes.getLong(12);
    }

    private static long numOfMirror(final Bytes evmAddress) {
        return evmAddress.getLong(12);
    }

    static Long fromMirror(final Bytes evmAddress) {
        return numFromEvmAddress(evmAddress);
    }

    @Nullable
    private static byte[] keyAliasToEVMAddress(final Bytes alias) {
        // NOTE: This implementation should be fixed when we (finally!) remove
        // JKey. The old JKey class needs a Google protobuf Key, so for now we
        // delegate to AliasManager. But this should be changed, so we don't
        // need AliasManager anymore.
        final var buf = new byte[Math.toIntExact(alias.length())];
        alias.getBytes(0, buf);
        return AliasManager.keyAliasToEVMAddress(ByteString.copyFrom(buf));
    }
}
