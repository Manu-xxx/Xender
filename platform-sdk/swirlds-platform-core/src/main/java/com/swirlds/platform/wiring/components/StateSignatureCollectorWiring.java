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

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractor;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedStateManager;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.wiring.SignedStateReserver;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

/**
 * Wiring for the state signature collector.
 */
public class StateSignatureCollectorWiring {

    private final TaskScheduler<List<ReservedSignedState>> taskScheduler;
    private final BindableInputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>, List<ReservedSignedState>>
            preConsSigInput;
    private final BindableInputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>, List<ReservedSignedState>>
            postConsSigInput;
    private final BindableInputWire<ReservedSignedState, List<ReservedSignedState>> reservedStateInput;
    private final InputWire<GossipEvent> preConsensusEventInput;
    private final InputWire<ConsensusRound> postConsensusEventInput;
    private final OutputWire<ReservedSignedState> reservedStateOutput;
    private final OutputWire<ReservedSignedState> completeStateOutput;

    /**
     * Constructor.
     *
     * @param model         the wiring model for the platform
     * @param taskScheduler the task scheduler that will perform the prehandling
     */
    private StateSignatureCollectorWiring(
            @NonNull final WiringModel model, @NonNull final TaskScheduler<List<ReservedSignedState>> taskScheduler) {

        this.taskScheduler = Objects.requireNonNull(taskScheduler);
        final OutputWire<ReservedSignedState> stateSplitter =
                taskScheduler.getOutputWire().buildSplitter("reservedStateSplitter", "reserved states");
        this.reservedStateOutput = stateSplitter.buildAdvancedTransformer(new SignedStateReserver("allStatesReserver"));
        this.completeStateOutput = reservedStateOutput
                .buildFilter(
                        "filterOnlyCompleteStates",
                        "reservedStateOutput",
                        StateSignatureCollectorWiring::completeStates)
                .buildAdvancedTransformer(new SignedStateReserver("completeStatesReserver"));

        //
        // Create input for pre-consensus signatures
        //
        final WireTransformer<GossipEvent, List<ScopedSystemTransaction<StateSignatureTransaction>>>
                preConsensusTransformer = new WireTransformer<>(
                        model,
                        "preConsensusTransformer",
                        "pre-consensus events",
                        new SystemTransactionExtractor<>(StateSignatureTransaction.class)::handleEvent);
        preConsensusEventInput = preConsensusTransformer.getInputWire();
        preConsSigInput = taskScheduler.buildInputWire("pre-consensus state signature transactions");
        preConsensusTransformer.getOutputWire().solderTo(preConsSigInput);

        //
        // Create input for post-consensus signatures
        //
        final WireTransformer<ConsensusRound, List<ScopedSystemTransaction<StateSignatureTransaction>>>
                postConsensusTransformer = new WireTransformer<>(
                        model,
                        "gossipEventTransformer",
                        "consensus events",
                        new SystemTransactionExtractor<>(StateSignatureTransaction.class)::handleRound);
        postConsensusEventInput = postConsensusTransformer.getInputWire();
        postConsSigInput = taskScheduler.buildInputWire("post-consensus state signature transactions");
        postConsensusTransformer.getOutputWire().solderTo(postConsSigInput);

        //
        // Create input for signed states
        //
        reservedStateInput = taskScheduler.buildInputWire("reserved signed states");
    }

    /**
     * Create a new instance of this wiring.
     *
     * @param model         the wiring model
     * @param taskScheduler the task scheduler that will perform the prehandling
     * @return the new wiring instance
     */
    @NonNull
    public static StateSignatureCollectorWiring create(
            @NonNull final WiringModel model, @NonNull final TaskScheduler<List<ReservedSignedState>> taskScheduler) {
        return new StateSignatureCollectorWiring(model, taskScheduler);
    }

    private static boolean completeStates(@NonNull final ReservedSignedState rs) {
        if (rs.get().isComplete()) {
            return true;
        }
        rs.close();
        return false;
    }

    /**
     * Bind the preconsensus event handler to the input wire.
     *
     * @param signedStateManager collects and manages state signatures
     */
    public void bind(@NonNull final SignedStateManager signedStateManager) {
        Objects.requireNonNull(signedStateManager);
        preConsSigInput.bind(signedStateManager::handlePreconsensusScopedSystemTransactions);
        postConsSigInput.bind(signedStateManager::handlePostconsensusScopedSystemTransactions);
        reservedStateInput.bind(signedStateManager::addReservedState);
    }

    /**
     * Get the input wire for the preconsensus events.
     *
     * @return the input wire
     */
    @NonNull
    public InputWire<GossipEvent> preconsensusEventInput() {
        return preConsensusEventInput;
    }

    public InputWire<ReservedSignedState> getReservedStateInput() {
        return reservedStateInput;
    }

    public InputWire<ConsensusRound> getConsensusRoundInput() {
        return postConsensusEventInput;
    }

    public OutputWire<ReservedSignedState> getReservedStateOutput() {
        return reservedStateOutput;
    }

    public OutputWire<ReservedSignedState> getCompleteStateOutput() {
        return completeStateOutput;
    }

    /**
     * Flush the task scheduler.
     */
    public void flush() {
        taskScheduler.flush();
    }
}
