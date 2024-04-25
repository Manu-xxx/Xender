/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.stale;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A default implementation of {@link TransactionResubmitter}.
 */
public class DefaultTransactionResubmitter implements TransactionResubmitter {

    // TODO unit test

    private final TransactionPool transactionPool;

    /**
     * Constructor.
     *
     * @param transactionPool the transaction pool
     */
    public DefaultTransactionResubmitter(@NonNull final TransactionPool transactionPool) {
        this.transactionPool = Objects.requireNonNull(transactionPool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resubmitStaleTransactions(@NonNull final GossipEvent event) {
        for (final ConsensusTransactionImpl transaction : event.getHashedData().getTransactions()) {
            if (transaction.isSystem()) {
                transactionPool.submitTransaction(transaction, true);
            }
        }
    }
}
