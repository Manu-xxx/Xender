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

package com.hedera.node.app.service.contract.impl.records;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Exposes the record customizations needed for a HAPI contract call transaction.
 */
public interface ContractCallRecordBuilder extends GasFeeRecordBuilder {

    /**
     * Tracks the final status of a top-level contract call.
     *
     * @param status the final status of the contract call
     * @return this builder
     */
    @NonNull
    ContractCallRecordBuilder status(@NonNull ResponseCodeEnum status);

    /**
     * Tracks the contract id called.
     *
     * @param contractId the {@link ContractID} called
     * @return this builder
     */
    @NonNull
    ContractCallRecordBuilder contractID(@Nullable ContractID contractId);

    /**
     * Tracks the result of a top-level contract call.
     *
     * @param result the {@link ContractFunctionResult} of the contract call
     * @return this builder
     */
    @NonNull
    ContractCallRecordBuilder contractCallResult(@Nullable ContractFunctionResult result);

    /**
     * Tracks the transaction contained in child records resulting from the contract call.
     *
     * @param txn the transaction
     * @return this builder
     */
    @NonNull
    ContractCallRecordBuilder transaction(@NonNull final Transaction txn);
}
