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
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.components.transaction.system.SystemTransactionExtractor;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.state.iss.ConsensusHashManager;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.state.notifications.IssNotification;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public record IssDetectorWiring(
        @NonNull InputWire<NoInput> endOfPcesReplay,
        @NonNull InputWire<Long> roundCompletedInput,
        @NonNull InputWire<ConsensusRound> handleConsensusRound,
        @NonNull InputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>> handlePostconsensusSignatures,
        @NonNull InputWire<ReservedSignedState> newStateHashed,
        @NonNull InputWire<ReservedSignedState> overridingState,
        @NonNull OutputWire<IssNotification> issNotificationOutput) {
    /**
     * Create a new instance of this wiring.
     *
     * @param taskScheduler the task scheduler that will detect ISSs
     * @return the new wiring instance
     */
    @NonNull
    public static IssDetectorWiring create(
            @NonNull final WiringModel model, @NonNull final TaskScheduler<List<IssNotification>> taskScheduler) {
        final WireTransformer<ConsensusRound, List<ScopedSystemTransaction<StateSignatureTransaction>>>
                roundTransformer = new WireTransformer<>(
                        model,
                        "extractSignaturesForIssDetector",
                        "consensus round",
                        new SystemTransactionExtractor<>(StateSignatureTransaction.class)::handleRound);
        final InputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>> sigInput =
                taskScheduler.buildInputWire("handlePostconsensusSignatures");
        roundTransformer.getOutputWire().solderTo(sigInput);
        return new IssDetectorWiring(
                taskScheduler.buildInputWire("endOfPcesReplay"),
                taskScheduler.buildInputWire("roundCompleted"),
                roundTransformer.getInputWire(),
                sigInput,
                taskScheduler.buildInputWire("newStateHashed"),
                taskScheduler.buildInputWire("overridingState"),
                taskScheduler.getOutputWire().buildSplitter("issNotificationSplitter", "issNotificationList"));
    }

    public void bind(@NonNull final ConsensusHashManager hashManager) {
        ((BindableInputWire<NoInput, Void>) endOfPcesReplay).bind(hashManager::signalEndOfPreconsensusReplay);
        ((BindableInputWire<Long, List<IssNotification>>) roundCompletedInput).bind(hashManager::roundCompleted);
        ((BindableInputWire<List<ScopedSystemTransaction<StateSignatureTransaction>>, List<IssNotification>>)
                        handlePostconsensusSignatures)
                .bind(hashManager::handlePostconsensusSignatures);
        ((BindableInputWire<ReservedSignedState, List<IssNotification>>) newStateHashed).bind(hashManager::newStateHashed);
        ((BindableInputWire<ReservedSignedState, List<IssNotification>>) overridingState).bind(hashManager::overridingState);
    }
}
