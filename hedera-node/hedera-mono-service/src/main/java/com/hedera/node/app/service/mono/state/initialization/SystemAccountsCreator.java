/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.initialization;

import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.migration.MigrationRecordsManager;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.system.address.AddressBook;
import java.time.Instant;

public interface SystemAccountsCreator {
    /**
     * Called in ServicesMain#init(Platform, NodeId) to ensure the network has all expected system
     * accounts, especially when starting from genesis; not really a migration path, may be better
     * placed in {@code com.hedera.node.app.service.mono.ServicesState#internalInit()}.
     *
     * @param backingAccounts the ledger accounts
     * @param addressBook the current address book
     */
    void ensureSystemAccounts(BackingStore<AccountID, HederaAccount> backingAccounts, AddressBook addressBook);

    /**
     * Called in {@link MigrationRecordsManager#publishMigrationRecords(Instant)} to ensure that
     * event replay from a round one state will export the same synthetic records that would occur
     * when starting from genesis without a restart.
     */
    void ensureSynthRecordsPresentOnFirstEverTransaction();
}
