/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.eventhandling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.components.transaction.system.ConsensusSystemTransactionManager;
import com.swirlds.platform.components.transaction.system.PreconsensusSystemTransactionManager;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.ConsensusHandlingMetrics;
import com.swirlds.platform.metrics.ConsensusMetrics;
import com.swirlds.platform.metrics.SwirldStateMetrics;
import com.swirlds.platform.stats.CycleTimingStat;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.system.transaction.SwirldTransaction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public abstract class AbstractEventHandlerTests {

    private static final int NUM_NODES = 10;

    protected NodeId selfId;
    protected Metrics metrics;
    protected SwirldStateMetrics ssStats;
    protected ConsensusMetrics consensusMetrics;
    protected ConsensusHandlingMetrics consensusHandlingMetrics;
    protected ConsensusSystemTransactionManager consensusSystemTransactionManager;
    protected Supplier<Instant> consEstimateSupplier;
    protected Random random;

    protected void setup() {
        selfId = new NodeId(0L);
        metrics = new NoOpMetrics();
        ssStats = mock(SwirldStateMetrics.class);
        consensusMetrics = mock(ConsensusMetrics.class);
        consensusHandlingMetrics = mock(ConsensusHandlingMetrics.class);
        when(consensusHandlingMetrics.getConsCycleStat()).thenReturn(mock(CycleTimingStat.class));
        consensusSystemTransactionManager = mock(ConsensusSystemTransactionManager.class);
        consEstimateSupplier = Instant::now;
        random = ThreadLocalRandom.current();
    }

    /**
     * Create mock events, some with mock transactions and some with no transactions.
     *
     * @param numEvents
     * 		the number of events to create
     * @param numTransactions
     * 		the number of transactions in each event that has transactions
     * @param includeEmptyEvents
     * 		true if empty events should be created
     * @return list of events
     */
    protected List<EventImpl> createEvents(
            final int numEvents, final int numTransactions, final boolean includeEmptyEvents) {
        final List<EventImpl> events = new ArrayList<>(numEvents);
        int numEmptyEvents = 0;
        if (includeEmptyEvents) {
            numEmptyEvents = numEvents / 3;
        }
        for (int i = 0; i < numEvents; i++) {
            final EventImpl event = mock(EventImpl.class);
            if (i > numEmptyEvents) {
                addTransactionsToEvent(event, numTransactions);
            } else {
                when(event.getTransactions()).thenReturn(new ConsensusTransactionImpl[0]);
                when(event.isEmpty()).thenReturn(true);
            }
            when(event.getCreatorId()).thenReturn(new NodeId(random.nextInt(NUM_NODES)));
            when(event.getConsensusTimestamp()).thenReturn(Instant.now());
            final boolean isConsensus = random.nextBoolean();
            when(event.isConsensus()).thenReturn(isConsensus);
            if (isConsensus) {
                when(event.getConsensusTimestamp()).thenReturn(Instant.now());
            }
            events.add(event);
        }
        return events;
    }

    private void addTransactionsToEvent(final EventImpl event, final int numTransactions) {
        final SwirldTransaction[] tx = new SwirldTransaction[numTransactions];
        for (int j = 0; j < numTransactions; j++) {
            tx[j] = mock(SwirldTransaction.class);
        }
        when(event.getTransactions()).thenReturn(tx);
    }
}
