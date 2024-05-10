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

package com.swirlds.platform.test.fixtures.turtle.gossip;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.wiring.NoInput;
import com.swirlds.platform.wiring.components.Gossip;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Simulates the {@link Gossip} subsystem for a group of nodes running on a {@link SimulatedNetwork}.
 */
public class SimulatedGossip implements Gossip {

    private final SimulatedNetwork network;
    private final NodeId selfId;

    private StandardOutputWire<GossipEvent> eventOutput;

    public SimulatedGossip(@NonNull final SimulatedNetwork network, @NonNull final NodeId selfId) {
        this.network = Objects.requireNonNull(network);
        this.selfId = Objects.requireNonNull(selfId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void bind(
            @NonNull final WiringModel model,
            @NonNull final BindableInputWire<GossipEvent, Void> eventInput,
            @NonNull final BindableInputWire<EventWindow, Void> eventWindowInput,
            @NonNull final StandardOutputWire<GossipEvent> eventOutput,
            @NonNull final BindableInputWire<NoInput, Void> startInput,
            @NonNull final BindableInputWire<NoInput, Void> stopInput,
            @NonNull final BindableInputWire<NoInput, Void> clearInput) {

        this.eventOutput = Objects.requireNonNull(eventOutput);
        eventInput.bindConsumer(event -> network.submitEvent(selfId, event));

        eventWindowInput.bindConsumer(ignored -> {});
        startInput.bindConsumer(ignored -> {});
        stopInput.bindConsumer(ignored -> {});
        clearInput.bindConsumer(ignored -> {});
    }

    /**
     * This method is called every time this node receives an event from the network.
     *
     * @param event the event that was received
     */
    void receiveEvent(@NonNull final GossipEvent event) {
        eventOutput.forward(event);
    }
}
