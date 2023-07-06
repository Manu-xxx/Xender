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

package com.hedera.node.app.service.contract.impl.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.MISSING_ADDRESS;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.Operation;

/**
 * Interface to avoid duplicating the exact same {@link org.hyperledger.besu.evm.operation.AbstractCallOperation#execute(MessageFrame, EVM)}
 * override in {@link CustomCallOperation}, {@link CustomStaticCallOperation}, and {@link CustomDelegateCallOperation}.
 */
public interface BasicCustomCallOperation {
    Operation.OperationResult UNDERFLOW_RESPONSE =
            new Operation.OperationResult(0, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);

    /**
     * Returns the {@link AddressChecks} instance used to determine whether a call is to a missing address.
     *
     * @return the {@link AddressChecks} instance used to determine whether a call is to a missing address
     */
    AddressChecks addressChecks();

    /**
     * Returns the address to which the {@link org.hyperledger.besu.evm.operation.AbstractCallOperation} being
     * customized is targeted.
     *
     * @param frame the frame in which the call is being made
     * @return the address to which the call is being made
     */
    Address superTo(@NonNull MessageFrame frame);

    /**
     * Returns the gas cost of the {@link org.hyperledger.besu.evm.operation.AbstractCallOperation} being customized.
     * @param frame the frame in which the call is being made
     * @return the gas cost of the call
     */
    long superCost(@NonNull MessageFrame frame);

    /**
     * Executes the {@link org.hyperledger.besu.evm.operation.AbstractCallOperation} being customized.
     *
     * @param frame the frame in which the call is being made
     * @param evm the EVM in which the call is being made
     * @return the result of the call
     */
    Operation.OperationResult superExecute(@NonNull MessageFrame frame, @NonNull EVM evm);

    /**
     * The basic Hedera-specific override of {@link org.hyperledger.besu.evm.operation.AbstractCallOperation#execute(MessageFrame, EVM)}.
     * Immediately halts on calls to missing addresses, <i>unless</i> the call is to an address in the system account
     * range, in which case the fate of the call is determined by the {@link CustomMessageCallProcessor}.
     *
     * @param frame the frame in which the call is being made
     * @param evm the EVM in which the call is being made
     * @return the result of the call
     */
    default Operation.OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        requireNonNull(evm);
        requireNonNull(frame);
        try {
            final var address = superTo(frame);
            if (addressChecks().isNeitherSystemNorPresent(address, frame)) {
                return new Operation.OperationResult(superCost(frame), MISSING_ADDRESS);
            }
            return superExecute(frame, evm);
        } catch (FixedStack.UnderflowException ignore) {
            return UNDERFLOW_RESPONSE;
        }
    }
}
