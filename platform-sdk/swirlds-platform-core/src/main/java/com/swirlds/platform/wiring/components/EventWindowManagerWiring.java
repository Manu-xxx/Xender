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

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.EventWindowManager;
import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link EventWindowManager}.
 */
public record EventWindowManagerWiring(
        @NonNull InputWire<ConsensusRound> consensusRoundInput,
        @NonNull InputWire<NonAncientEventWindow> manualWindowInput,
        @NonNull OutputWire<NonAncientEventWindow> nonAncientEventWindowOutput) {

    /**
     * Create a new instance of this wiring.
     *
     * @param model the wiring model
     * @return the new wiring instance
     */
    @NonNull
    public static EventWindowManagerWiring create(@NonNull final WiringModel model) {
        final TaskScheduler<NonAncientEventWindow> scheduler = model.schedulerBuilder("eventWindowManager")
                .withType(TaskSchedulerType.DIRECT_STATELESS)
                .build()
                .cast();

        final BindableInputWire<ConsensusRound, NonAncientEventWindow> consensusRoundInput =
                scheduler.buildInputWire("rounds");

        final BindableInputWire<NonAncientEventWindow, NonAncientEventWindow> manualWindowInput =
                scheduler.buildInputWire("override event window");

        // This is stateless and requires no parameters to build, so we can just build and bind it here directly.
        final EventWindowManager eventWindowManager = new EventWindowManager();
        consensusRoundInput.bind(eventWindowManager::roundReachedConsensus);
        manualWindowInput.bind(eventWindowManager::manuallyOverrideNonAncientEventWindow);

        return new EventWindowManagerWiring(consensusRoundInput, manualWindowInput, scheduler.getOutputWire());
    }
}
