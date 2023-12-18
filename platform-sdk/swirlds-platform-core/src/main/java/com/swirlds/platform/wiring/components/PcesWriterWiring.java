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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.wiring.DoneStreamingPcesTrigger;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link PcesWriter}.
 *
 * @param doneStreamingPcesInputWire        the input wire for the trigger to indicate that PCES streaming is complete
 * @param eventInputWire                    the input wire for events to be written
 * @param discontinuityInputWire            the input wire for PCES discontinuities
 * @param minimumGenerationNonAncientInput  the input wire for the minimum generation of non-ancient events
 * @param minimumGenerationToStoreInputWire the input wire for the minimum generation of events to store
 * @param flushRunnable                     the runnable to flush the writer
 */
public record PcesWriterWiring(
        @NonNull InputWire<DoneStreamingPcesTrigger> doneStreamingPcesInputWire,
        @NonNull InputWire<GossipEvent> eventInputWire,
        @NonNull InputWire<Long> discontinuityInputWire,
        @NonNull InputWire<Long> minimumGenerationNonAncientInput,
        @NonNull InputWire<Long> minimumGenerationToStoreInputWire,
        @NonNull Runnable flushRunnable) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static PcesWriterWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new PcesWriterWiring(
                taskScheduler.buildInputWire("done streaming pces"),
                taskScheduler.buildInputWire("events to write"),
                taskScheduler.buildInputWire("discontinuity"),
                taskScheduler.buildInputWire("minimum generation non ancient"),
                taskScheduler.buildInputWire("minimum generation to store"),
                taskScheduler::flush);
    }
}
