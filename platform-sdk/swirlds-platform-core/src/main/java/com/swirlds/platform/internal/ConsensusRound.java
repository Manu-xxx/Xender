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

package com.swirlds.platform.internal;

import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.swirlds.common.system.Round;
import com.swirlds.common.system.events.ConsensusEvent;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.util.iterator.TypedIterator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;

/** A consensus round with events and all other relevant data. */
public class ConsensusRound implements Round {
    /** an unmodifiable list of consensus events in this round, in consensus order */
    private final List<EventImpl> consensusEvents;
    /** the consensus generations when this round reached consensus */
    private final GraphGenerations generations;
    /** this round's number */
    private final long roundNum;
    /** the last event in the round */
    private final EventImpl lastEvent;
    /** The number of application transactions in this round */
    private int numAppTransactions = 0;
    /** A snapshot of consensus at this consensus round */
    private final ConsensusSnapshot snapshot;
    /** The event that, when added to the hashgraph, caused this round to reach consensus. */
    private final EventImpl keystoneEvent;

    /**
     * @deprecated this is currently used only by unit tests, should be removed before merging to
     *     develop
     */
    @Deprecated(forRemoval = true)
    public ConsensusRound(final List<EventImpl> consensusEvents, final GraphGenerations generations) {
        throw new RuntimeException("dont use this");
    }

    /**
     * Same as {@link #ConsensusRound(List, EventImpl, GraphGenerations, long, ConsensusSnapshot)} but the
     * round number is derived from the snapshot
     */
    public ConsensusRound(
            final List<EventImpl> consensusEvents,
            @NonNull final EventImpl keystoneEvent,
            final GraphGenerations generations,
            final ConsensusSnapshot snapshot) {
        this(consensusEvents, keystoneEvent, generations, snapshot.round(), snapshot);
    }

    /**
     * Create a new instance with the provided consensus info.
     *
     * @param consensusEvents the events in the round, in consensus order
     * @param keystoneEvent   the event that, when added to the hashgraph, caused this round to reach consensus
     * @param generations the consensus generations for this round
     * @param roundNum the round number
     * @param snapshot snapshot of consensus at this round
     */
    private ConsensusRound(
            @NonNull final List<EventImpl> consensusEvents,
            @NonNull final EventImpl keystoneEvent,
            @NonNull final GraphGenerations generations,
            final long roundNum,
            @NonNull final ConsensusSnapshot snapshot) {
        Objects.requireNonNull(consensusEvents, "consensusEvents must not be null");
        Objects.requireNonNull(keystoneEvent, "keystoneEvent must not be null");
        Objects.requireNonNull(generations, "generations must not be null");

        this.consensusEvents = Collections.unmodifiableList(consensusEvents);
        this.keystoneEvent = keystoneEvent;
        this.generations = generations;
        this.roundNum = roundNum;
        this.snapshot = snapshot;

        for (final EventImpl e : consensusEvents) {
            numAppTransactions += e.getNumAppTransactions();
        }

        lastEvent = consensusEvents.isEmpty() ? null : consensusEvents.get(consensusEvents.size() - 1);
    }

    /**
     * @return true if this round is complete (contains the last event of the round)
     * @deprecated an incomplete round is a concept introduced by the old recovery workflow. the new
     *     workflow does not use this class so a round is never incomplete. also, a round might have
     *     no consensus events and still be a complete round.
     */
    @Deprecated(forRemoval = true)
    public boolean isComplete() {
        return lastEvent != null;
    }

    /**
     * Returns the number of application transactions in this round
     *
     * @return the number of application transactions
     */
    public int getNumAppTransactions() {
        return numAppTransactions;
    }

    /**
     * Provides an unmodifiable list of the consensus event in this round.
     *
     * @return the list of events in this round
     */
    public List<EventImpl> getConsensusEvents() {
        return consensusEvents;
    }

    /**
     * @return the consensus generations when this round reached consensus
     */
    public GraphGenerations getGenerations() {
        return generations;
    }

    /**
     * @return a snapshot of consensus at this consensus round
     */
    public ConsensusSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * @return the number of events in this round
     */
    public int getNumEvents() {
        return consensusEvents.size();
    }

    /** {@inheritDoc} */
    @Override
    public Iterator<ConsensusEvent> iterator() {
        return new TypedIterator<>(consensusEvents.iterator());
    }

    /** {@inheritDoc} */
    @Override
    public long getRoundNum() {
        return roundNum;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isEmpty() {
        return consensusEvents.isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public int getEventCount() {
        return consensusEvents.size();
    }

    /**
     * @return the last event of this round, or null if this round is not complete
     * @deprecated a round might not have any events, the code should not expect this to be non-null
     */
    @Deprecated(forRemoval = true)
    public EventImpl getLastEvent() {
        return lastEvent;
    }

    /**
     * @return the event that, when added to the hashgraph, caused this round to reach consensus
     */
    public @NonNull EventImpl getKeystoneEvent() {
        return keystoneEvent;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("round", roundNum)
                .append("consensus events", EventUtils.toShortStrings(consensusEvents))
                .toString();
    }
}
