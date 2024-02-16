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

import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.components.appcomm.AppCommunicationComponent;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link AppCommunicationComponent}.
 *
 * @param appCommunicationComponentInputWire  the input wire for app communication component
 */
public record AppCommunicationComponentWiring(
        @NonNull InputWire<ReservedSignedState> appCommunicationComponentInputWire) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler for this wiring
     * @return the new wiring instance
     */
    public static AppCommunicationComponentWiring create(@NonNull final TaskScheduler<Void> taskScheduler) {
        return new AppCommunicationComponentWiring(taskScheduler.buildInputWire("events to notify"));
    }

    /**
     * Bind an app communication component to this wiring.
     *
     * @param appCommunicationComponent the communication component to bind
     */
    public void bind(@NonNull final AppCommunicationComponent appCommunicationComponent) {
        ((BindableInputWire<ReservedSignedState, Void>) appCommunicationComponentInputWire)
                .bind(appCommunicationComponent::latestCompleteStateHandler);
    }
}
