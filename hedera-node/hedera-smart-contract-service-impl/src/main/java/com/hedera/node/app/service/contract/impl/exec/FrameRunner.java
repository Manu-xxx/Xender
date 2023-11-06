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

package com.hedera.node.app.service.contract.impl.exec;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult.failureFrom;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult.successFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asNumberedContractId;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isLongZero;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;

/**
 * An infrastructure service that runs the EVM transaction beginning with the given {@link MessageFrame}
 * to completion and returns the result.
 */
@Singleton
public class FrameRunner {
    private final CustomGasCalculator gasCalculator;

    @Inject
    public FrameRunner(@NonNull final CustomGasCalculator gasCalculator) {
        this.gasCalculator = gasCalculator;
    }

    /**
     * Runs the EVM transaction implied by the given {@link MessageFrame} to completion using the provided
     * {@link org.hyperledger.besu.evm.processor.AbstractMessageProcessor} implementations, and returns the result.
     *
     * @param gasLimit the gas limit for the transaction
     * @param frame the frame to run
     * @param senderId the Hedera id of the sending account
     * @param tracer the tracer to use
     * @param messageCall the message call processor to use
     * @param contractCreation the contract creation processor to use
     * @return the result of the transaction
     */
    public HederaEvmTransactionResult runToCompletion(
            final long gasLimit,
            @NonNull final AccountID senderId,
            @NonNull final MessageFrame frame,
            @NonNull final ActionSidecarContentTracer tracer,
            @NonNull final CustomMessageCallProcessor messageCall,
            @NonNull final ContractCreationProcessor contractCreation) {
        requireNonNull(frame);
        requireNonNull(tracer);
        requireNonNull(senderId);
        requireNonNull(messageCall);
        requireNonNull(contractCreation);

        final var recipientAddress = frame.getRecipientAddress();
        // We compute the called contract's Hedera id up front because it could
        // selfdestruct, preventing us from looking up its id after the fact
        final var recipientId = resolvedHederaId(frame, recipientAddress);

        // Now run the transaction implied by the frame
        tracer.traceOriginAction(frame);
        final var stack = frame.getMessageFrameStack();
        while (!stack.isEmpty()) {
            runToCompletion(stack.peekFirst(), tracer, messageCall, contractCreation);
        }
        tracer.sanitizeTracedActions(frame);

        // And return the result, success or failure
        final var gasUsed = effectiveGasUsed(gasLimit, frame);
        var updater = ((ProxyWorldUpdater)frame.getWorldUpdater());
        if (frame.getState() == COMPLETED_SUCCESS) {
            var result = successFrom(gasUsed, senderId, recipientId, asEvmContractId(recipientAddress), frame);
            updater.addSidecars(frame, tracer, result.stateChanges(), recipientId, updater.getAccount(recipientAddress));
            return result;
        } else {
            var result = failureFrom(gasUsed, senderId, frame);
            updater.addSidecars(frame, tracer, result.stateChanges(), null, null);
            return result;
        }
    }

    private ContractID resolvedHederaId(@NonNull final MessageFrame frame, @NonNull final Address address) {
        return isLongZero(address)
                ? asNumberedContractId(address)
                : ((ProxyWorldUpdater) frame.getWorldUpdater()).getHederaContractId(address);
    }

    private void runToCompletion(
            @NonNull final MessageFrame frame,
            @NonNull final ActionSidecarContentTracer tracer,
            @NonNull final CustomMessageCallProcessor messageCall,
            @NonNull final ContractCreationProcessor contractCreation) {
        final var executor =
                switch (frame.getType()) {
                    case MESSAGE_CALL -> messageCall;
                    case CONTRACT_CREATION -> contractCreation;
                };
        executor.process(frame, tracer);
    }

    private long effectiveGasUsed(final long gasLimit, @NonNull final MessageFrame frame) {
        var nominalUsed = gasLimit - frame.getRemainingGas();
        final var selfDestructRefund = gasCalculator.getSelfDestructRefundAmount()
                * Math.min(frame.getSelfDestructs().size(), nominalUsed / gasCalculator.getMaxRefundQuotient());
        nominalUsed -= (selfDestructRefund + frame.getGasRefund());
        final var maxRefundPercent = contractsConfigOf(frame).maxRefundPercentOfGasLimit();
        return Math.max(nominalUsed, gasLimit - gasLimit * maxRefundPercent / 100);
    }
}
