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

package com.hedera.node.app.fees;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Interface for fee calculation. Currently, it is only used to compute payments for Queries. It
 * will be enhanced to be used for transactions as well in the future.
 */
public interface FeeAccumulator {
    /**
     * Computes the required fees for the given query using the given readable states, the pre-determined functionality
     * of the query, and the estimated current consensus time.
     *
     * @param query the query
     * @param now the estimated current consensus time
     * @param readableStoreFactory the readable states (this parameter will probably become obsolete in the future)
     * @return the fees for the query, assuming it has the given functionality
     * @throws IllegalArgumentException if the functionality is not some kind of query.
     */
    @NonNull
    FeeObject computePayment(
            @NonNull Query query, @NonNull Instant now, @NonNull ReadableStoreFactory readableStoreFactory);

    /**
     * Computes the required fees for a given transaction.
     *
     * @param transaction the {@link Transaction} to compute fees for
     * @param payerKey the {@link Key} of the payer
     * @param now the estimated current consensus time
     * @return the fees for the transaction
     */
    @NonNull
    FeeObject computePayment(@NonNull Transaction transaction, @NonNull Key payerKey, @NonNull Instant now);
}
