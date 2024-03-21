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

package com.swirlds.platform.gui;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides a way to access private platform objects from the GUI. Suboptimal, but necessary to preserve the current UI
 * architecture if we don't want to allow public access to these objects.
 *
 * @deprecated this class will eventually be removed
 */
@Deprecated(forRemoval = true)
public final class GuiPlatformAccessor {

    private final Map<NodeId, SignedStateNexus> latestCompleteStateComponents = new ConcurrentHashMap<>();
    private final Map<NodeId, SignedStateNexus> latestImmutableStateComponents = new ConcurrentHashMap<>();

    private static final GuiPlatformAccessor INSTANCE = new GuiPlatformAccessor();

    /**
     * Get the static instance of the GuiPlatformAccessor.
     *
     * @return the static instance of the GuiPlatformAccessor
     */
    public static GuiPlatformAccessor getInstance() {
        return INSTANCE;
    }

    private GuiPlatformAccessor() {}

    /**
     * Information required to sort events by consensus order (or, if events have not yet reached consensus, by received
     * time).
     *
     * @param consensusOrder the consensus order if known, else -1 if unknown
     * @param timeReceived   the time the event was received, used to break ties when consensus order is unknown for
     *                       both events
     */
    private record EventOrderInfo(long consensusOrder, @NonNull Instant timeReceived)
            implements Comparable<EventOrderInfo> {

        /**
         * Create a new EventOrderInfo from an event.
         */
        public static EventOrderInfo of(@NonNull final EventImpl event) {
            return new EventOrderInfo(
                    event.getConsensusOrder(), event.getBaseEvent().getTimeReceived());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int compareTo(@NonNull final EventOrderInfo that) {
            if (this.consensusOrder == -1 && that.consensusOrder == -1) {
                // neither have reached consensus
                return this.timeReceived.compareTo(that.timeReceived);
            } else if (this.consensusOrder == -1) {
                // that has reached consensus but not this
                return 1;
            } else if (that.consensusOrder == -1) {
                // this has reached consensus but not that
                return -1;
            } else {
                // both have reached consensus
                return Long.compare(this.consensusOrder, that.consensusOrder);
            }
        }
    }

    /**
     * Set the latest complete state component for a node.
     *
     * @param nodeId              the ID of the node
     * @param latestCompleteState the latest complete state component
     */
    public void setLatestCompleteStateComponent(
            @NonNull final NodeId nodeId, @NonNull final SignedStateNexus latestCompleteState) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(latestCompleteState, "latestCompleteState must not be null");
        latestCompleteStateComponents.put(nodeId, latestCompleteState);
    }

    /**
     * Get the latest complete state component for a node, or null if none is set.
     *
     * @param nodeId the ID of the node
     * @return the latest complete state component
     */
    @Nullable
    public SignedStateNexus getLatestCompleteStateComponent(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return latestCompleteStateComponents.getOrDefault(nodeId, null);
    }

    /**
     * Set the latest immutable state component for a node.
     *
     * @param nodeId              the ID of the node
     * @param latestCompleteState the latest immutable state component
     */
    public void setLatestImmutableStateComponent(
            @NonNull final NodeId nodeId, @NonNull final SignedStateNexus latestCompleteState) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(latestCompleteState, "latestCompleteState must not be null");
        latestImmutableStateComponents.put(nodeId, latestCompleteState);
    }

    /**
     * Get the latest immutable state component for a node, or null if none is set.
     *
     * @param nodeId the ID of the node
     * @return the latest immutable state component
     */
    @Nullable
    public SignedStateNexus getLatestImmutableStateComponent(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        return latestImmutableStateComponents.getOrDefault(nodeId, null);
    }
}
