/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows.record;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public interface DeleteCapableTransactionRecordBuilder extends SingleTransactionRecordBuilder {
    /**
     * Gets number of deleted accounts in this transaction.
     * @return number of deleted accounts in this transaction
     */
    int getNumberOfDeletedAccounts();

    /**
     * Gets the beneficiary account ID for deleted account ID.
     * @return the beneficiary account ID of deleted account ID
     */
    @Nullable
    AccountID getDeletedAccountBeneficiaryFor(@NonNull final AccountID deletedAccountID);

    /**
     * Adds a beneficiary for a deleted account.
     * @param deletedAccountID the deleted account ID
     * @param beneficiaryForDeletedAccount the beneficiary account ID
     * @return the builder
     */
    @NonNull
    SingleTransactionRecordBuilder addBeneficiaryForDeletedAccount(
            @NonNull AccountID deletedAccountID, @NonNull AccountID beneficiaryForDeletedAccount);
}
