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

package com.swirlds.platform.gossip;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps track of how many events have been received from each peer, but haven't yet made it through the intake
 * pipeline.
 */
public interface IntakeEventCounter {
    /**
     * Checks whether there are any events from a given sender that have entered the intake pipeline, but aren't yet
     * through it.
     *
     * @param peer the peer to check for unprocessed events
     * @return true if there are unprocessed events, false otherwise
     */
    boolean hasUnprocessedEvents(@NonNull final NodeId peer);

    /**
     * Get the counter tracking the number of events the given peer has currently in the intake pipeline
     *
     * @param peer the peer to get the counter for
     * @return the counter
     */
    @NonNull
    AtomicInteger getEventCounter(@NonNull final NodeId peer);

    /**
     * Reset event counts
     */
    void reset();
}
