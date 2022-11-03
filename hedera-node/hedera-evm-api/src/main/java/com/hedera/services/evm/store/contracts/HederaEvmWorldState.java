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
package com.hedera.services.evm.store.contracts;

import com.hedera.services.evm.accounts.AccountAccessor;
import com.hedera.services.evm.contracts.execution.EvmProperties;
import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class HederaEvmWorldState implements HederaEvmMutableWorldState {

    private final HederaEvmEntityAccess hederaEvmEntityAccess;
    private final EvmProperties evmProperties;
    private final AbstractCodeCache abstractCodeCache;

    AccountAccessor accountAccessor;

    protected HederaEvmWorldState(
            HederaEvmEntityAccess hederaEvmEntityAccess,
            EvmProperties evmProperties,
            AbstractCodeCache abstractCodeCache) {
        this.hederaEvmEntityAccess = hederaEvmEntityAccess;
        this.evmProperties = evmProperties;
        this.abstractCodeCache = abstractCodeCache;
    }

    protected HederaEvmWorldState(
            HederaEvmEntityAccess hederaEvmEntityAccess,
            EvmProperties evmProperties,
            AbstractCodeCache abstractCodeCache,
            AccountAccessor accountAccessor) {
        this(hederaEvmEntityAccess, evmProperties, abstractCodeCache);
        this.accountAccessor = accountAccessor;
    }

    public Account get(final Address address) {
        if (address == null) {
            return null;
        }
        if (hederaEvmEntityAccess.isTokenAccount(address)
                && evmProperties.isRedirectTokenCallsEnabled()) {
            return new HederaEvmWorldStateTokenAccount(address);
        }
        if (!hederaEvmEntityAccess.isUsable(address)) {
            return null;
        }
        final long balance = hederaEvmEntityAccess.getBalance(address);
        return new WorldStateAccount(
                address, Wei.of(balance), abstractCodeCache, hederaEvmEntityAccess);
    }

    @Override
    public Hash rootHash() {
        return Hash.EMPTY;
    }

    @Override
    public Hash frontierRootHash() {
        return rootHash();
    }

    @Override
    public Stream<StreamableAccount> streamAccounts(Bytes32 startKeyHash, int limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public HederaEvmWorldUpdater updater() {
        return new Updater(accountAccessor);
    }

    public static class Updater implements HederaEvmWorldUpdater {

        AccountAccessor accountAccessor;

        protected Updater(AccountAccessor accountAccessor) {
            this.accountAccessor = accountAccessor;
        }

        @Override
        public long getSbhRefund() {
            return 0;
        }

        @Override
        public EvmAccount createAccount(Address address, long nonce, Wei balance) {
            return null;
        }

        @Override
        public EvmAccount getAccount(Address address) {
            return null;
        }

        @Override
        public void deleteAccount(Address address) {}

        @Override
        public Collection<? extends Account> getTouchedAccounts() {
            return null;
        }

        @Override
        public Collection<Address> getDeletedAccountAddresses() {
            return null;
        }

        @Override
        public void revert() {}

        @Override
        public void commit() {}

        @Override
        public Optional<WorldUpdater> parentUpdater() {
            return Optional.empty();
        }

        @Override
        public WorldUpdater updater() {
            return new AbstractLedgerEvmWorldUpdater(accountAccessor);
        }

        @Override
        public Account get(Address address) {
            return null;
        }
    }
}
