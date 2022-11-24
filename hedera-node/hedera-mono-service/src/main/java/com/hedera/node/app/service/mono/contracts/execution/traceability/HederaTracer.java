/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.contracts.execution.traceability;

import static com.hedera.services.evm.contracts.operations.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;

import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.store.contracts.HederaStackedWorldStateUpdater;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.frame.MessageFrame.Type;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.Operation.OperationResult;

public class HederaTracer implements HederaOperationTracer {

    private final List<SolidityAction> allActions;
    private final Deque<SolidityAction> currentActionsStack;
    private final boolean areActionSidecarsEnabled;

    private static final int OP_CODE_CREATE = 0xF0;
    private static final int OP_CODE_CALL = 0xF1;
    private static final int OP_CODE_CALLCODE = 0xF2;
    private static final int OP_CODE_DELEGATECALL = 0xF4;
    private static final int OP_CODE_CREATE2 = 0xF5;
    private static final int OP_CODE_STATICCALL = 0xFA;

    public HederaTracer(final boolean areActionSidecarsEnabled) {
        this.currentActionsStack = new ArrayDeque<>();
        this.allActions = new ArrayList<>();
        this.areActionSidecarsEnabled = areActionSidecarsEnabled;
    }

    @Override
    public void init(final MessageFrame initialFrame) {
        if (areActionSidecarsEnabled) {
            trackTopLevelActionFor(initialFrame);
        }
    }

    @Override
    public void tracePostExecution(MessageFrame currentFrame, OperationResult operationResult) {
        if (areActionSidecarsEnabled) {
            final var frameState = currentFrame.getState();
            if (frameState != State.CODE_EXECUTING) {
                if (frameState == State.CODE_SUSPENDED) {
                    final var nextFrame = currentFrame.getMessageFrameStack().peek();
                    trackInnerActionFor(nextFrame, currentFrame);
                } else {
                    finalizeActionFor(currentActionsStack.pop(), currentFrame, frameState);
                }
            }
        }
    }

    private void trackTopLevelActionFor(final MessageFrame initialFrame) {
        trackNewAction(
                initialFrame,
                action -> {
                    action.setCallOperationType(toCallOperationType(initialFrame.getType()));
                    action.setCallingAccount(
                            EntityId.fromAddress(
                                    asMirrorAddress(
                                            initialFrame.getOriginatorAddress(), initialFrame)));
                });
    }

    private void trackInnerActionFor(final MessageFrame nextFrame, final MessageFrame parentFrame) {
        trackNewAction(
                nextFrame,
                action -> {
                    action.setCallOperationType(
                            toCallOperationType(parentFrame.getCurrentOperation().getOpcode()));
                    action.setCallingContract(
                            EntityId.fromAddress(
                                    asMirrorAddress(
                                            parentFrame.getContractAddress(), parentFrame)));
                });
    }

    private void trackNewAction(
            final MessageFrame messageFrame, final Consumer<SolidityAction> actionConfig) {
        final var action =
                new SolidityAction(
                        toContractActionType(messageFrame.getType()),
                        messageFrame.getRemainingGas(),
                        messageFrame.getInputData().toArray(),
                        messageFrame.getValue().toLong(),
                        messageFrame.getMessageStackDepth());
        final var recipient =
                EntityId.fromAddress(
                        asMirrorAddress(messageFrame.getContractAddress(), messageFrame));
        if (Code.EMPTY.equals(messageFrame.getCode())) {
            // code can be empty when calling precompiles too, but we handle
            // that in tracePrecompileCall, after precompile execution is completed
            action.setRecipientAccount(recipient);
        } else {
            action.setRecipientContract(recipient);
        }
        actionConfig.accept(action);

        allActions.add(action);
        currentActionsStack.push(action);
    }

    private void finalizeActionFor(
            final SolidityAction action, final MessageFrame frame, final State frameState) {
        if (frameState == State.CODE_SUCCESS || frameState == State.COMPLETED_SUCCESS) {
            action.setGasUsed(action.getGas() - frame.getRemainingGas());
            // externalize output for calls only - create output is externalized in bytecode sidecar
            if (action.getCallType() != ContractActionType.CREATE) {
                action.setOutput(frame.getOutputData().toArrayUnsafe());
            } else {
                action.setOutput(new byte[0]);
            }
        } else if (frameState == State.REVERT) {
            // deliberate failures do not burn extra gas
            action.setGasUsed(action.getGas() - frame.getRemainingGas());
            frame.getRevertReason()
                    .ifPresentOrElse(
                            bytes -> action.setRevertReason(bytes.toArrayUnsafe()),
                            () -> action.setRevertReason(new byte[0]));
            if (frame.getType().equals(Type.CONTRACT_CREATION)) {
                action.setRecipientContract(null);
            }
        } else if (frameState == State.EXCEPTIONAL_HALT) {
            // exceptional exits always burn all gas
            action.setGasUsed(action.getGas());
            final var exceptionalHaltReasonOptional = frame.getExceptionalHaltReason();
            if (exceptionalHaltReasonOptional.isPresent()) {
                final var exceptionalHaltReason = exceptionalHaltReasonOptional.get();
                action.setError(exceptionalHaltReason.name().getBytes(StandardCharsets.UTF_8));
                if (exceptionalHaltReason.equals(INVALID_SOLIDITY_ADDRESS)) {
                    final var syntheticInvalidAction =
                            new SolidityAction(
                                    ContractActionType.CALL,
                                    frame.getRemainingGas(),
                                    null,
                                    0,
                                    frame.getMessageStackDepth() + 1);
                    syntheticInvalidAction.setCallingContract(
                            EntityId.fromAddress(
                                    asMirrorAddress(frame.getContractAddress(), frame)));
                    syntheticInvalidAction.setTargetedAddress(
                            Words.toAddress(frame.getStackItem(1)).toArray());
                    syntheticInvalidAction.setError(
                            INVALID_SOLIDITY_ADDRESS.name().getBytes(StandardCharsets.UTF_8));
                    syntheticInvalidAction.setCallOperationType(
                            toCallOperationType(frame.getCurrentOperation().getOpcode()));
                    allActions.add(syntheticInvalidAction);
                }
            } else {
                action.setError(new byte[0]);
            }
            if (frame.getType().equals(Type.CONTRACT_CREATION)) {
                action.setRecipientContract(null);
            }
        }
    }

    @Override
    public void tracePrecompileResult(final MessageFrame frame, final ContractActionType type) {
        if (areActionSidecarsEnabled) {
            final var lastAction = currentActionsStack.pop();
            lastAction.setCallType(type);
            lastAction.setRecipientAccount(null);
            lastAction.setRecipientContract(EntityId.fromAddress(frame.getContractAddress()));
            finalizeActionFor(lastAction, frame, frame.getState());
        }
    }

    @Override
    public void traceAccountCreationResult(
            final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
        frame.setExceptionalHaltReason(haltReason);
    }

    public List<SolidityAction> getActions() {
        return allActions;
    }

    private ContractActionType toContractActionType(final MessageFrame.Type type) {
        return switch (type) {
            case CONTRACT_CREATION -> ContractActionType.CREATE;
            case MESSAGE_CALL -> ContractActionType.CALL;
        };
    }

    private CallOperationType toCallOperationType(final int opCode) {
        return switch (opCode) {
            case OP_CODE_CREATE -> CallOperationType.OP_CREATE;
            case OP_CODE_CALL -> CallOperationType.OP_CALL;
            case OP_CODE_CALLCODE -> CallOperationType.OP_CALLCODE;
            case OP_CODE_DELEGATECALL -> CallOperationType.OP_DELEGATECALL;
            case OP_CODE_CREATE2 -> CallOperationType.OP_CREATE2;
            case OP_CODE_STATICCALL -> CallOperationType.OP_STATICCALL;
            default -> CallOperationType.OP_UNKNOWN;
        };
    }

    private CallOperationType toCallOperationType(final Type type) {
        return type == Type.CONTRACT_CREATION
                ? CallOperationType.OP_CREATE
                : CallOperationType.OP_CALL;
    }

    private Address asMirrorAddress(final Address addressOrAlias, final MessageFrame messageFrame) {
        final var aliases =
                ((HederaStackedWorldStateUpdater) messageFrame.getWorldUpdater()).aliases();
        return aliases.resolveForEvm(addressOrAlias);
    }
}
