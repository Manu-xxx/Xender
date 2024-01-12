/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.records;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.spi.workflows.record.DeleteCapableTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

public interface ContractOperationRecordBuilder extends DeleteCapableTransactionRecordBuilder {
    /**
     * Sets the transaction fee.
     *
     * @param transactionFee the new transaction fee
     * @return the updated {@link ContractOperationRecordBuilder}
     */
    ContractOperationRecordBuilder transactionFee(long transactionFee);

    /**
     * Updates this record builder to include the standard contract fields from the given outcome.
     *
     * @param outcome the EVM transaction outcome
     * @return this updated builder
     */
    default ContractOperationRecordBuilder withCommonFieldsSetFrom(@NonNull final CallOutcome outcome) {
        transactionFee(transactionFee() + outcome.tinybarGasCost());
        if (outcome.actions() != null) {
            addContractActions(outcome.actions(), false);
        }
        if (outcome.hasStateChanges()) {
            addContractStateChanges(requireNonNull(outcome.stateChanges()), false);
        }
        return this;
    }

    /**
     * Updates this record builder to include contract actions.
     *
     * @param contractActions the contract actions
     * @param isMigration whether these actions are exported as part of a system-initiated migration of some kind
     * @return this builder
     */
    @NonNull
    ContractOperationRecordBuilder addContractActions(@NonNull ContractActions contractActions, boolean isMigration);

    /**
     * Updates this record builder to include contract bytecode.
     *
     * @param contractBytecode the contract bytecode
     * @param isMigration whether this bytecode is exported as part of a system-initiated migration of some kind
     * @return this builder
     */
    @NonNull
    ContractOperationRecordBuilder addContractBytecode(@NonNull ContractBytecode contractBytecode, boolean isMigration);

    /**
     * Updates this record builder to include contract state changes.
     *
     * @param contractStateChanges the contract state changes
     * @param isMigration whether these state changes are exported as part of a system-initiated migration of some kind
     * @return this builder
     */
    @NonNull
    ContractOperationRecordBuilder addContractStateChanges(
            @NonNull ContractStateChanges contractStateChanges, boolean isMigration);
}
