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

package com.swirlds.platform.wiring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.StateSigner;
import com.swirlds.platform.builder.ApplicationCallbacks;
import com.swirlds.platform.builder.PlatformBuildingBlocks;
import com.swirlds.platform.builder.PlatformComponentBuilder;
import com.swirlds.platform.components.AppNotifier;
import com.swirlds.platform.components.EventWindowManager;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.components.appcomm.LatestCompleteStateNotifier;
import com.swirlds.platform.components.consensus.ConsensusEngine;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.event.preconsensus.durability.RoundDurabilityBuffer;
import com.swirlds.platform.event.runninghash.RunningEventHasher;
import com.swirlds.platform.event.signing.SelfEventSigner;
import com.swirlds.platform.event.stale.DefaultStaleEventDetector;
import com.swirlds.platform.event.stale.TransactionResubmitter;
import com.swirlds.platform.event.stream.ConsensusEventStream;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.TransactionPrehandler;
import com.swirlds.platform.pool.TransactionPool;
import com.swirlds.platform.publisher.PlatformPublisher;
import com.swirlds.platform.state.hasher.StateHasher;
import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.nexus.LatestCompleteStateNexus;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.SignedStateSentinel;
import com.swirlds.platform.state.signed.StateGarbageCollector;
import com.swirlds.platform.state.signed.StateSignatureCollector;
import com.swirlds.platform.state.snapshot.StateSnapshotManager;
import com.swirlds.platform.system.events.BirthRoundMigrationShim;
import com.swirlds.platform.system.status.PlatformStatusNexus;
import com.swirlds.platform.system.status.StatusStateMachine;
import com.swirlds.platform.util.HashLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlatformWiring}
 */
class PlatformWiringTests {
    @Test
    @DisplayName("Assert that all input wires are bound to something")
    void testBindings() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final ApplicationCallbacks applicationCallbacks = new ApplicationCallbacks(x -> {}, x -> {}, x -> {});

        final PlatformWiring wiring = new PlatformWiring(platformContext, applicationCallbacks);

        final PlatformComponentBuilder componentBuilder =
                new PlatformComponentBuilder(mock(PlatformBuildingBlocks.class));

        componentBuilder
                .withEventHasher(mock(EventHasher.class))
                .withInternalEventValidator(mock(InternalEventValidator.class))
                .withEventDeduplicator(mock(EventDeduplicator.class))
                .withEventSignatureValidator(mock(EventSignatureValidator.class))
                .withStateGarbageCollector(mock(StateGarbageCollector.class))
                .withSelfEventSigner(mock(SelfEventSigner.class))
                .withOrphanBuffer(mock(OrphanBuffer.class))
                .withRunningEventHasher(mock(RunningEventHasher.class))
                .withEventCreationManager(mock(EventCreationManager.class))
                .withConsensusEngine(mock(ConsensusEngine.class))
                .withConsensusEventStream(mock(ConsensusEventStream.class))
                .withPcesSequencer(mock(PcesSequencer.class))
                .withRoundDurabilityBuffer(mock(RoundDurabilityBuffer.class))
                .withStatusStateMachine(mock(StatusStateMachine.class))
                .withTransactionPrehandler(mock(TransactionPrehandler.class))
                .withPcesWriter(mock(PcesWriter.class))
                .withSignedStateSentinel(mock(SignedStateSentinel.class))
                .withIssDetector(mock(IssDetector.class))
                .withStateHasher(mock(StateHasher.class))
                .withStaleEventDetector(mock(DefaultStaleEventDetector.class))
                .withTransactionResubmitter(mock(TransactionResubmitter.class))
                .withTransactionPool(mock(TransactionPool.class))
                .withStateSnapshotManager(mock(StateSnapshotManager.class));

        // Gossip is a special case, it's not like other components.
        // Currently we just have a facade between gossip and the wiring framework.
        // In the future when gossip is refactored to operate within the wiring
        // framework like other components, such things will not be needed.
        componentBuilder.withGossip(
                (model, eventInput, eventWindowInput, eventOutput, startInput, stopInput, clearInput) -> {
                    eventInput.bindConsumer(event -> {});
                    eventWindowInput.bindConsumer(eventWindow -> {});
                    startInput.bindConsumer(noInput -> {});
                    stopInput.bindConsumer(noInput -> {});
                    clearInput.bindConsumer(noInput -> {});
                });

        wiring.bind(
                componentBuilder,
                mock(StateSigner.class),
                mock(PcesReplayer.class),
                mock(StateSignatureCollector.class),
                mock(EventWindowManager.class),
                mock(ConsensusRoundHandler.class),
                mock(IssHandler.class),
                mock(HashLogger.class),
                mock(BirthRoundMigrationShim.class),
                mock(LatestCompleteStateNotifier.class),
                mock(SignedStateNexus.class),
                mock(LatestCompleteStateNexus.class),
                mock(SavedStateController.class),
                mock(AppNotifier.class),
                mock(PlatformPublisher.class),
                mock(PlatformStatusNexus.class));

        assertFalse(wiring.getModel().checkForUnboundInputWires());
    }
}
