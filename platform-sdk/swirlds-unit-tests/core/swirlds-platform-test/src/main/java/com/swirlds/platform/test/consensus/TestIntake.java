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

package com.swirlds.platform.test.consensus;

import static com.swirlds.common.wiring.wires.SolderType.INJECT;
import static com.swirlds.platform.consensus.SyntheticSnapshot.GENESIS_SNAPSHOT;
import static com.swirlds.platform.event.AncientMode.GENERATION_THRESHOLD;
import static org.mockito.Mockito.mock;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.wiring.component.ComponentWiring;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.components.DefaultEventWindowManager;
import com.swirlds.platform.components.EventWindowManager;
import com.swirlds.platform.components.consensus.ConsensusEngine;
import com.swirlds.platform.components.consensus.DefaultConsensusEngine;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.hashing.DefaultEventHasher;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.NoOpIntakeEventCounter;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import com.swirlds.platform.test.fixtures.event.IndexedEvent;
import com.swirlds.platform.wiring.OrphanBufferWiring;
import com.swirlds.platform.wiring.components.PassThroughWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

/**
 * Event intake with consensus and shadowgraph, used for testing
 */
public class TestIntake {
    private final ConsensusOutput output;

    private final ComponentWiring<EventHasher, GossipEvent> hasherWiring;
    private final OrphanBufferWiring orphanBufferWiring;
    private final ComponentWiring<ConsensusEngine, List<ConsensusRound>> consensusEngineWiring;
    private final WiringModel model;
    private final int roundsNonAncient;

    /**
     * @param platformContext the platform context used to configure this intake.
     * @param addressBook     the address book used by this intake
     */
    public TestIntake(@NonNull final PlatformContext platformContext, @NonNull final AddressBook addressBook) {
        final NodeId selfId = new NodeId(0);
        roundsNonAncient = platformContext
                .getConfiguration()
                .getConfigData(ConsensusConfig.class)
                .roundsNonAncient();

        final Time time = Time.getCurrent();
        output = new ConsensusOutput(time);

        model = WiringModel.create(platformContext, mock(ForkJoinPool.class));

        hasherWiring = new ComponentWiring<>(model, EventHasher.class, directScheduler("eventHasher"));
        final EventHasher eventHasher = new DefaultEventHasher(platformContext);
        hasherWiring.bind(eventHasher);

        final PassThroughWiring<GossipEvent> postHashCollectorWiring =
                new PassThroughWiring(model, "GossipEvent", "postHashCollector", TaskSchedulerType.DIRECT);

        final IntakeEventCounter intakeEventCounter = new NoOpIntakeEventCounter();
        final OrphanBuffer orphanBuffer = new OrphanBuffer(platformContext, intakeEventCounter);
        orphanBufferWiring = OrphanBufferWiring.create(directScheduler("orphanBuffer"));
        orphanBufferWiring.bind(orphanBuffer);

        final ConsensusEngine consensusEngine = new DefaultConsensusEngine(platformContext, addressBook, selfId);

        consensusEngineWiring = new ComponentWiring<>(model, ConsensusEngine.class, directScheduler("consensusEngine"));
        consensusEngineWiring.bind(consensusEngine);

        final ComponentWiring<EventWindowManager, NonAncientEventWindow> eventWindowManagerWiring =
                new ComponentWiring<>(model, EventWindowManager.class, directScheduler("eventWindowManager"));
        eventWindowManagerWiring.bind(new DefaultEventWindowManager());

        hasherWiring.getOutputWire().solderTo(postHashCollectorWiring.getInputWire());
        postHashCollectorWiring.getOutputWire().solderTo(orphanBufferWiring.eventInput());
        orphanBufferWiring.eventOutput().solderTo(consensusEngineWiring.getInputWire(ConsensusEngine::addEvent));

        final OutputWire<ConsensusRound> consensusRoundOutputWire = consensusEngineWiring.getSplitOutput();
        consensusRoundOutputWire.solderTo(
                eventWindowManagerWiring.getInputWire(EventWindowManager::extractEventWindow));
        consensusRoundOutputWire.solderTo("consensusOutputTestTool", "round output", output::consensusRound);

        eventWindowManagerWiring.getOutputWire().solderTo(orphanBufferWiring.nonAncientEventWindowInput(), INJECT);

        // Ensure unsoldered wires are created.
        hasherWiring.getInputWire(EventHasher::hashEvent);

        // Make sure this unsoldered wire is properly built
        consensusEngineWiring.getInputWire(ConsensusEngine::outOfBandSnapshotUpdate);

        model.start();
    }

    /**
     * Link an event to its parents and add it to consensus and shadowgraph
     *
     * @param event the event to add
     */
    public void addEvent(@NonNull final GossipEvent event) {
        hasherWiring.getInputWire(EventHasher::hashEvent).put(event);
        output.eventAdded(event);
    }

    /**
     * Same as {@link #addEvent(GossipEvent)} but for a list of events
     */
    public void addEvents(@NonNull final List<IndexedEvent> events) {
        for (final IndexedEvent event : events) {
            addEvent(event.getBaseEvent());
        }
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

    public void loadSnapshot(@NonNull final ConsensusSnapshot snapshot) {

        // FUTURE WORK: remove the fourth variable setting useBirthRound to false when we switch from comparing
        // minGenNonAncient to comparing birthRound to minRoundNonAncient.  Until then, it is always false in
        // production.

        final NonAncientEventWindow eventWindow = new NonAncientEventWindow(
                snapshot.round(),
                snapshot.getMinimumGenerationNonAncient(roundsNonAncient),
                snapshot.getMinimumGenerationNonAncient(roundsNonAncient),
                GENERATION_THRESHOLD);

        orphanBufferWiring.nonAncientEventWindowInput().put(eventWindow);
        consensusEngineWiring
                .getInputWire(ConsensusEngine::outOfBandSnapshotUpdate)
                .put(snapshot);
    }

    public @NonNull ConsensusOutput getOutput() {
        return output;
    }

    public void reset() {
        loadSnapshot(GENESIS_SNAPSHOT);
        output.clear();
    }

    public <X> TaskScheduler<X> directScheduler(final String name) {
        return model.schedulerBuilder(name)
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();
    }
}
