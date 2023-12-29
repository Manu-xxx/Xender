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

package com.swirlds.platform.test.consensus;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.mockito.Mockito.mock;

import com.swirlds.base.time.TimeSource;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.metrics.extensions.PhaseTimer;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.ConsensusImpl;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.linking.EventLinker;
import com.swirlds.platform.event.linking.OrphanBufferingLinker;
import com.swirlds.platform.event.linking.ParentFinder;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraphEventObserver;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.observers.EventObserverDispatcher;
import com.swirlds.platform.state.signed.LoadableFromSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.BaseEvent;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import com.swirlds.platform.test.fixtures.event.IndexedEvent;
import com.swirlds.test.framework.config.TestConfigBuilder;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.InstantSource;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/** Event intake with consensus and shadowgraph, used for testing */
public class TestIntake implements LoadableFromSignedState {
    private final ConsensusImpl consensus;
    private final EventLinker linker;
    private final ShadowGraph shadowGraph;
    private final EventIntake intake;
    private final ConsensusOutput output;
    private int numEventsAdded = 0;

    /**
     * See {@link #TestIntake(AddressBook, InstantSource, ConsensusConfig)}
     */
    public TestIntake(@NonNull final AddressBook ab) {
        this(ab, InstantSource.system());
    }

    /**
     * See {@link #TestIntake(AddressBook, InstantSource, ConsensusConfig)}
     */
    public TestIntake(@NonNull final AddressBook ab, @NonNull final InstantSource instantSource) {
        this(ab, instantSource, new TestConfigBuilder().getOrCreateConfig().getConfigData(ConsensusConfig.class));
    }

    /**
     * See {@link #TestIntake(AddressBook, InstantSource, ConsensusConfig)}
     */
    public TestIntake(@NonNull final AddressBook ab, @NonNull final ConsensusConfig consensusConfig) {
        this(ab, InstantSource.system(), consensusConfig);
    }

    /**
     * @param ab the address book used by this intake
     * @param instantSource the time used by this intake
     * @param consensusConfig the consensus config used by this intake
     */
    public TestIntake(
            @NonNull final AddressBook ab,
            @NonNull final InstantSource instantSource,
            @NonNull final ConsensusConfig consensusConfig) {
        output = new ConsensusOutput(instantSource);
        consensus = new ConsensusImpl(consensusConfig, ConsensusUtils.NOOP_CONSENSUS_METRICS, ab);
        shadowGraph = new ShadowGraph(
                InstantSource.system(), mock(SyncMetrics.class), mock(AddressBook.class), new NodeId(0));
        final ParentFinder parentFinder = new ParentFinder(shadowGraph::hashgraphEvent);

        linker = new OrphanBufferingLinker(consensusConfig, parentFinder, 100000, mock(IntakeEventCounter.class));

        final EventObserverDispatcher dispatcher =
                new EventObserverDispatcher(new ShadowGraphEventObserver(shadowGraph, null), output);

        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder().getOrCreateConfig())
                .build();

        intake = new EventIntake(
                platformContext,
                getStaticThreadManager(),
                TimeSource.system(),
                new NodeId(0L), // only used for logging
                linker,
                this::getConsensus,
                ab,
                dispatcher,
                mock(PhaseTimer.class),
                shadowGraph,
                null,
                e -> {},
                mock(IntakeEventCounter.class));
    }

    /**
     * Link an event to its parents and add it to consensus and shadowgraph
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final GossipEvent event) {
        intake.addUnlinkedEvent(event);
        numEventsAdded++;
    }

    /**
     * Same as {@link #addEvent(GossipEvent)}
     *
     * <p>Note: this event won't be the one inserted, intake will create a new instance that will
     * wrap the {@link BaseEvent}
     */
    public void addEvent(@NonNull final EventImpl event) {
        intake.addUnlinkedEvent(event.getBaseEvent());
        numEventsAdded++;
    }

    /** Same as {@link #addEvent(GossipEvent)} but for a list of events */
    public void addEvents(@NonNull final List<IndexedEvent> events) {
        for (final IndexedEvent event : events) {
            addEvent(event.getBaseEvent());
        }
    }

    /** Same as {@link #addEvent(GossipEvent)} but skips the linking and inserts this instance */
    public void addLinkedEvent(@NonNull final EventImpl event) {
        intake.addEvent(event);
        numEventsAdded++;
    }

    /**
     * @return the consensus used by this intake
     */
    public @NonNull Consensus getConsensus() {
        return consensus;
    }

    /**
     * @return the shadowgraph used by this intake
     */
    public @NonNull ShadowGraph getShadowGraph() {
        return shadowGraph;
    }

    /**
     * @return a queue of all rounds that have reached consensus
     */
    public @NonNull Deque<ConsensusRound> getConsensusRounds() {
        return output.getConsensusRounds();
    }

    public @Nullable ConsensusRound getLatestRound() {
        return output.getConsensusRounds().pollLast();
    }

    @Override
    public void loadFromSignedState(@NonNull final SignedState signedState) {
        consensus.loadFromSignedState(signedState);
        shadowGraph.clear();
        shadowGraph.initFromEvents(Arrays.asList(signedState.getEvents()), consensus.getMinRoundGeneration());
    }

    public void loadSnapshot(@NonNull final ConsensusSnapshot snapshot) {
        consensus.loadSnapshot(snapshot);
        linker.updateGenerations(consensus);
        shadowGraph.clear();
        shadowGraph.startFromGeneration(consensus.getMinGenerationNonAncient());
    }

    public int getNumEventsAdded() {
        return numEventsAdded;
    }

    public @NonNull ConsensusOutput getOutput() {
        return output;
    }

    public void reset() {
        consensus.reset();
        shadowGraph.clear();
        output.clear();
        numEventsAdded = 0;
    }
}
