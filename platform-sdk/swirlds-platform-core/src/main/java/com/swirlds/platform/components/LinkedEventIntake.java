/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.AddedEventMetrics;
import com.swirlds.platform.metrics.StaleMetrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * This class is responsible for adding events to {@link Consensus} and notifying event observers.
 */
public class LinkedEventIntake {
    /**
     * A functor that provides access to a {@code Consensus} instance.
     */
    private final Supplier<Consensus> consensusSupplier;

    /**
     * Stores events, expires them, provides event lookup methods
     */
    private final Shadowgraph shadowGraph;

    /**
     * Tracks the number of events from each peer have been received, but aren't yet through the intake pipeline
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * The secondary wire that outputs the keystone event sequence number
     */
    private final StandardOutputWire<Long> keystoneEventSequenceNumberOutput;

    /**
     * Whether or not the linked event intake is paused.
     * <p>
     * When paused, all received events will be tossed into the void
     */
    private boolean paused;

    private final AddedEventMetrics eventAddedMetrics;

    private final StaleMetrics staleMetrics;

    /**
     * Constructor
     *
     * @param platformContext                   the platform context
     * @param selfId                            the ID of the node
     * @param consensusSupplier                 provides the current consensus instance
     * @param shadowGraph                       tracks events in the hashgraph
     * @param intakeEventCounter                tracks the number of events from each peer have been received, but
     *                                          aren't yet through the intake pipeline
     * @param keystoneEventSequenceNumberOutput the secondary wire that outputs the keystone event sequence number
     */
    public LinkedEventIntake(
            @NonNull final PlatformContext platformContext,
            @NonNull final NodeId selfId,
            @NonNull final Supplier<Consensus> consensusSupplier,
            @NonNull final Shadowgraph shadowGraph,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final StandardOutputWire<Long> keystoneEventSequenceNumberOutput) {

        this.consensusSupplier = Objects.requireNonNull(consensusSupplier);
        this.shadowGraph = Objects.requireNonNull(shadowGraph);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.keystoneEventSequenceNumberOutput = Objects.requireNonNull(keystoneEventSequenceNumberOutput);

        this.eventAddedMetrics = new AddedEventMetrics(selfId, platformContext.getMetrics());
        this.staleMetrics = new StaleMetrics(platformContext, selfId);

        this.paused = false;
    }

    /**
     * Add an event to the hashgraph
     *
     * @param event an event to be added
     * @return a list of rounds that came to consensus as a result of adding the event
     */
    @NonNull
    public List<ConsensusRound> addEvent(@NonNull final EventImpl event) {
        Objects.requireNonNull(event);

        if (paused) {
            // If paused, throw everything into the void
            return List.of();
        }

        try {
            if (event.getGeneration() < consensusSupplier.get().getMinGenerationNonAncient()) {
                // ancient events *may* be discarded, and stale events *must* be discarded
                return List.of();
            }

            final long minimumGenerationNonAncientBeforeAdding =
                    consensusSupplier.get().getMinGenerationNonAncient();

            // record the event in the hashgraph, which results in the events in consEvent reaching consensus
            final List<ConsensusRound> consensusRounds = consensusSupplier.get().addEvent(event);

            eventAddedMetrics.eventAdded(event);

            if (consensusRounds != null) {
                consensusRounds.forEach(round -> {
                    // it is important that a flush request for the keystone event is submitted before starting
                    // to handle the transactions in the round. Otherwise, the system could arrive at a place
                    // where the transaction handler is waiting for a given event to become durable, but the
                    // PCES writer hasn't been notified yet that the event should be flushed.
                    keystoneEventSequenceNumberOutput.forward(
                            round.getKeystoneEvent().getBaseEvent().getStreamSequenceNumber());
                });
            }

            final long minimumGenerationNonAncient = consensusSupplier.get().getMinGenerationNonAncient();

            if (minimumGenerationNonAncient > minimumGenerationNonAncientBeforeAdding) {
                // consensus rounds can be null and the minNonAncient might change, this is probably because of a round
                // with no consensus events, so we check the diff in generations to look for stale events
                handleStale(minimumGenerationNonAncientBeforeAdding);
            }

            return Objects.requireNonNullElseGet(consensusRounds, List::of);
        } finally {
            intakeEventCounter.eventExitedIntakePipeline(event.getBaseEvent().getSenderId());
        }
    }

    /**
     * Pause or unpause this object.
     *
     * @param paused whether or not this object should be paused
     */
    public void setPaused(final boolean paused) {
        this.paused = paused;
    }

    /**
     * Notify observer of stale events, of all event in the consensus stale event queue.
     *
     * @param previousGenerationNonAncient the previous minimum generation of non-ancient events
     */
    private void handleStale(final long previousGenerationNonAncient) {
        // find all events that just became ancient and did not reach consensus, these events will be considered stale
        final Collection<EventImpl> staleEvents = shadowGraph.findByAncientIndicator(
                previousGenerationNonAncient,
                consensusSupplier.get().getMinGenerationNonAncient(),
                LinkedEventIntake::isNotConsensus);

        for (final EventImpl staleEvent : staleEvents) {
            staleEvent.setStale(true);
            staleMetrics.staleEvent(staleEvent);
        }
    }

    /**
     * Returns true if the event has not reached consensus
     *
     * @param event the event to check
     * @return true if the event has not reached consensus
     */
    private static boolean isNotConsensus(@NonNull final EventImpl event) {
        return !event.isConsensus();
    }
}
