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

import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * Detects when a self event becomes stale. Note that this detection may not observe a self event go stale if the node
 * needs to reconnect or restart.
 */
public class DefaultStaleEventDetector implements StaleEventDetector {

    /**
     * The ID of this node.
     */
    private final NodeId selfId;

    /**
     * Self events that have not yet reached consensus.
     */
    private final StandardSequenceMap<EventDescriptor, GossipEvent> selfEvents;

    /**
     * The most recent event window we know about.
     */
    private EventWindow currentEventWindow;

    /**
     * Metrics for the stale event detector.
     */
    private final StaleEventDetectorMetrics metrics;

    /**
     * Constructor.
     *
     * @param platformContext    the platform context
     * @param selfId             the ID of this node
     */
    public DefaultStaleEventDetector(@NonNull final PlatformContext platformContext, @NonNull final NodeId selfId) {

        this.selfId = Objects.requireNonNull(selfId);

        final AncientMode ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();

        final ToLongFunction<EventDescriptor> getAncientIdentifier;
        if (ancientMode == BIRTH_ROUND_THRESHOLD) {
            getAncientIdentifier = EventDescriptor::getBirthRound;
        } else {
            getAncientIdentifier = EventDescriptor::getGeneration;
        }
        selfEvents = new StandardSequenceMap<>(0, 1024, true, getAncientIdentifier);

        metrics = new StaleEventDetectorMetrics(platformContext);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<GossipEvent> addSelfEvent(@NonNull final GossipEvent event) {
        if (currentEventWindow == null) {
            throw new IllegalStateException("Event window must be set before adding self events");
        }

        if (currentEventWindow.isAncient(event)) {
            // Although unlikely, it is plausible for an event to go stale before it is added to the detector.
            handleStaleEvent(event);
            return List.of(event);
        }

        selfEvents.put(event.getDescriptor(), event);
        return List.of();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<GossipEvent> addConsensusRound(@NonNull final ConsensusRound consensusRound) {
        for (final EventImpl event : consensusRound.getConsensusEvents()) {
            if (event.getCreatorId().equals(selfId)) {
                selfEvents.remove(event.getBaseEvent().getDescriptor());
            }
        }

        final List<GossipEvent> staleEvents = new ArrayList<>();
        currentEventWindow = consensusRound.getEventWindow();
        selfEvents.shiftWindow(currentEventWindow.getAncientThreshold(), (descriptor, event) -> staleEvents.add(event));

        for (final GossipEvent event : staleEvents) {
            handleStaleEvent(event);
        }

        return staleEvents;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInitialEventWindow(@NonNull final EventWindow initialEventWindow) {
        this.currentEventWindow = Objects.requireNonNull(initialEventWindow);
        selfEvents.shiftWindow(currentEventWindow.getAncientThreshold());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        selfEvents.clear();
        currentEventWindow = null;
    }

    /**
     * Handle a stale event.
     *
     * @param event the stale event
     */
    private void handleStaleEvent(@NonNull final GossipEvent event) {
        metrics.reportStaleEvent(event);
    }
}
