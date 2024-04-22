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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link PcesWriter}.
 *
 * @param doneStreamingPcesInputWire               the input wire for the trigger to indicate that PCES streaming is
 *                                                 complete
 * @param eventInputWire                           the input wire for events to be written
 * @param discontinuityInputWire                   the input wire for PCES discontinuities
 * @param eventWindowInput                         the input wire for the event window
 * @param minimumAncientIdentifierToStoreInputWire the input wire for the minimum ancient identifier of events to store
 * @param flushRequestInputWire                    the input wire for flush requests
 * @param latestDurableSequenceNumberOutput        the output wire for the latest durable sequence number
 */
public record PcesWriterWiring(
        @NonNull InputWire<NoInput> doneStreamingPcesInputWire,
        @NonNull InputWire<GossipEvent> eventInputWire,
        @NonNull InputWire<Long> discontinuityInputWire,
        @NonNull InputWire<EventWindow> eventWindowInput,
        @NonNull InputWire<Long> minimumAncientIdentifierToStoreInputWire,
        @NonNull InputWire<Long> flushRequestInputWire,
        @NonNull OutputWire<Long> latestDurableSequenceNumberOutput) {

    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    @NonNull
    public static PcesWriterWiring create(@NonNull final TaskScheduler<Long> taskScheduler) {
        return new PcesWriterWiring(
                taskScheduler.buildInputWire("done streaming pces"),
                taskScheduler.buildInputWire("events to write"),
                taskScheduler.buildInputWire("discontinuity"),
                taskScheduler.buildInputWire("event window"),
                taskScheduler.buildInputWire("minimum identifier to store"),
                taskScheduler.buildInputWire("flush request"),
                taskScheduler.getOutputWire());
    }

    /**
     * Bind a PCES writer to this wiring.
     *
     * @param pcesWriter the PCES writer to bind
     */
    public void bind(@NonNull final PcesWriter pcesWriter) {
        ((BindableInputWire<NoInput, Long>) doneStreamingPcesInputWire)
                .bindConsumer(x -> pcesWriter.beginStreamingNewEvents());
        ((BindableInputWire<GossipEvent, Long>) eventInputWire).bind(pcesWriter::writeEvent);
        ((BindableInputWire<Long, Long>) discontinuityInputWire).bind(pcesWriter::registerDiscontinuity);
        ((BindableInputWire<EventWindow, Long>) eventWindowInput)
                .bindConsumer(pcesWriter::updateNonAncientEventBoundary);
        ((BindableInputWire<Long, Long>) minimumAncientIdentifierToStoreInputWire)
                .bindConsumer(pcesWriter::setMinimumAncientIdentifierToStore);
        ((BindableInputWire<Long, Long>) flushRequestInputWire).bind(pcesWriter::submitFlushRequest);
    }
}
