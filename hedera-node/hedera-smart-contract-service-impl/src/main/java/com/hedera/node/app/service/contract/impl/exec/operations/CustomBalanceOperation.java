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

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;

import com.hedera.node.app.service.contract.impl.exec.AddressChecks;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.BalanceOperation;

public class CustomBalanceOperation extends BalanceOperation {
    private final AddressChecks addressChecks;

    public CustomBalanceOperation(
            @NonNull final GasCalculator gasCalculator, @NonNull final AddressChecks addressChecks) {
        super(gasCalculator);
        this.addressChecks = addressChecks;
    }

    @Override
    public OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        try {
            final var address = Words.toAddress(frame.getStackItem(0));
            // Make system contract balance invisible to EVM (added in v0.38)
            if (addressChecks.isSystemAccount(address)) {
                frame.popStackItem();
                frame.pushStackItem(UInt256.ZERO);
                return new OperationResult(cost(true), null);
            }
            // Otherwise continue to enforce existence checks for backward compatibility
            if (proxyUpdaterFor(frame).contractMustBePresent() && !addressChecks.isPresent(address, frame)) {
                return new OperationResult(cost(true), INVALID_SOLIDITY_ADDRESS);
            }
            return super.execute(frame, evm);
        } catch (UnderflowException ignore) {
            return new OperationResult(cost(true), ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        }
    }
}
