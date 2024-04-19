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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.common.wiring.wires.output.StandardOutputWire;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.wiring.NoInput;
import com.swirlds.platform.wiring.PlatformSchedulersConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for gossip.
 */
public class GossipWiring {

    /**
     * The wiring model for this node.
     */
    private final WiringModel model;

    /**
     * The task scheduler for the gossip component.
     */
    private final TaskScheduler<Void> scheduler;

    /**
     * Events to be gossiped are sent here.
     */
    private final BindableInputWire<GossipEvent, Void> eventInput;

    /**
     * Event window updates are sent here.
     */
    private final BindableInputWire<EventWindow, Void> eventWindowInput;

    /**
     * Events received through gossip are sent out over this wire.
     */
    private final StandardOutputWire<GossipEvent> eventOutput;

    /**
     * This wire is used to start gossip.
     */
    private final BindableInputWire<NoInput, Void> startInput;

    /**
     * This wire is used to stop gossip.
     */
    private final BindableInputWire<NoInput, Void> stopInput;

    /**
     * This wire is used to clear internal gossip state.
     */
    private final BindableInputWire<NoInput, Void> clearInput;

    public GossipWiring(@NonNull final PlatformContext platformContext, @NonNull final WiringModel model) {
        this.model = model;

        scheduler = model.schedulerBuilder("gossip")
                .configure(platformContext
                        .getConfiguration()
                        .getConfigData(PlatformSchedulersConfig.class)
                        .gossip())
                .build()
                .cast();

        eventInput = scheduler.buildInputWire("events to gossip");
        eventWindowInput = scheduler.buildInputWire("event window");
        eventOutput = scheduler.buildSecondaryOutputWire();

        startInput = scheduler.buildInputWire("start");
        stopInput = scheduler.buildInputWire("stop");
        clearInput = scheduler.buildInputWire("clear");
    }

    /**
     * Bind the wiring to a gossip implementation.
     *
     * @param gossip the gossip implementation
     */
    public void bind(@NonNull final Gossip gossip) {
        gossip.bind(model, eventInput, eventWindowInput, eventOutput, startInput, stopInput, clearInput);
    }

    /**
     * Get the input wire for events to be gossiped to the network.
     *
     * @return the input wire for events
     */
    @NonNull
    public InputWire<GossipEvent> getEventInput() {
        return eventInput;
    }

    /**
     * Get the input wire for the current event window.
     *
     * @return the input wire for the event window
     */
    @NonNull
    public InputWire<EventWindow> getEventWindowInput() {
        return eventWindowInput;
    }

    /**
     * Get the output wire for events received from peers during gossip.
     *
     * @return the output wire for events
     */
    @NonNull
    public OutputWire<GossipEvent> getEventOutput() {
        return eventOutput;
    }

    /**
     * Get the input wire to start gossip.
     *
     * @return the input wire to start gossip
     */
    @NonNull
    public InputWire<NoInput> getStartInput() {
        return startInput;
    }

    /**
     * Get the input wire to stop gossip.
     *
     * @return the input wire to stop gossip
     */
    @NonNull
    public InputWire<NoInput> getStopInput() {
        return stopInput;
    }

    /**
     * Get the input wire to clear the gossip state.
     */
    @NonNull
    public InputWire<NoInput> getClearInput() {
        return clearInput;
    }

    /**
     * Flush the gossip scheduler.
     */
    public void flush() {
        scheduler.flush();
    }
}
