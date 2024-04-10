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

package com.swirlds.platform.event.orphan;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Takes as input an unordered stream of {@link com.swirlds.platform.event.GossipEvent GossipEvent}s and emits a stream
 * of {@link com.swirlds.platform.event.GossipEvent GossipEvent}s in topological order.
 */
public interface OrphanBuffer {

    /**
     * Add a new event to the buffer if it is an orphan.
     * <p>
     * Events that are ancient are ignored, and events that don't have any missing parents are immediately passed along
     * down the pipeline.
     *
     * @param event the event to handle
     * @return the list of events that are no longer orphans as a result of this event being handled
     */
    @InputWireLabel("unordered events")
    @NonNull
    List<GossipEvent> handleEvent(@NonNull GossipEvent event);

    /**
     * Sets the non-ancient event window that defines when an event is considered ancient.
     *
     * @param nonAncientEventWindow the non-ancient event window
     * @return the list of events that are no longer orphans as a result of this change
     */
    @InputWireLabel("non-ancient event window")
    @NonNull
    List<GossipEvent> setNonAncientEventWindow(@NonNull final NonAncientEventWindow nonAncientEventWindow);

    /**
     * Clears the orphan buffer.
     */
    void clear();
}
