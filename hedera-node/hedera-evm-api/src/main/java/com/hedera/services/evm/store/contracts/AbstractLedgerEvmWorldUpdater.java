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
import com.hedera.services.evm.store.models.UpdatedHederaEvmAccount;
import java.util.Collection;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class AbstractLedgerEvmWorldUpdater implements WorldUpdater {

    protected final AccountAccessor accountAccessor;

    protected AbstractLedgerEvmWorldUpdater(AccountAccessor accountAccessor) {
        this.accountAccessor = accountAccessor;
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
        return null;
    }

    @Override
    public Account get(Address address) {
        return new UpdatedHederaEvmAccount(accountAccessor.exists(address));
    }
}
