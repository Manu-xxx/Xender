package com.hedera.services.contracts.execution;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */


import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS;
import static com.hedera.services.state.enums.ContractActionType.CALL;
import static com.hedera.services.state.enums.ContractActionType.CREATE;

import com.hedera.services.state.enums.ContractActionType;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.SolidityAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/**
 * Custom {@link OperationTracer} that populates exceptional halt reasons in the {@link MessageFrame}
 */
@Singleton
public class HederaTracer implements HederaOperationTracer {

	private final List<SolidityAction> allActions;
	private final Deque<SolidityAction> currentActionsStack;

	@Inject
	public HederaTracer() {
		this.currentActionsStack = new ArrayDeque<>();
		this.allActions = new ArrayList<>();
	}

	@Override
	public void reset() {
		this.currentActionsStack.clear();
		this.allActions.clear();
	}

	//TODO: direct token calls? Call, but to system contract like HTS or ERC20 facades over Token accounts
	@Override
	public void traceExecution(MessageFrame frame, ExecuteOperation executeOperation) {
		if (currentActionsStack.isEmpty()) {
			trackActionFor(frame, 0);
		}

		executeOperation.execute();

		final var frameState = frame.getState();
		if (frameState != State.CODE_EXECUTING) {
			if (frameState == State.CODE_SUSPENDED) {
				final var nextFrame = frame.getMessageFrameStack().peek();
				trackActionFor(nextFrame, nextFrame.getMessageFrameStack().size() - 1);
			} else {
				finalizeActionFor(currentActionsStack.pop(), frame, frameState);
			}
		}
	}

	@Override
	public void tracePrecompileResult(final MessageFrame frame, final ContractActionType type) {
		final var lastAction = currentActionsStack.pop();
		// specialize the call type - precompile or system (Hedera precompile contracts)
		lastAction.setCallType(type);
		// we have to null out recipient account and set recipient contract
		lastAction.setRecipientAccount(null);
		// TODO: do we set contract for precompiles? 0.0.1?
		lastAction.setRecipientContract(EntityId.fromAddress(frame.getContractAddress()));
		finalizeActionFor(lastAction, frame, frame.getState());
	}

	private void trackActionFor(final MessageFrame frame, final int callDepth) {
		// code can be empty when calling precompiles too, but we handle
		// that in tracePrecompileCall, after precompile execution is completed
		final var isCallToAccount = Code.EMPTY.equals(frame.getCode());
		final var isTopLevelEVMTransaction = callDepth == 0;
		final var action = new SolidityAction(
				toContractActionType(frame.getType()),
				isTopLevelEVMTransaction ? EntityId.fromAddress(frame.getOriginatorAddress()) : null,
				!isTopLevelEVMTransaction ? EntityId.fromAddress(frame.getSenderAddress()) : null,
				frame.getRemainingGas(),
				frame.getInputData().toArray(),
				isCallToAccount ? EntityId.fromAddress(frame.getContractAddress()) : null,
				!isCallToAccount ? EntityId.fromAddress(frame.getContractAddress()) : null,
				frame.getValue().toLong(),
				callDepth);
		allActions.add(action);
		currentActionsStack.push(action);
	}

	private void finalizeActionFor(final SolidityAction action, final MessageFrame frame, final State frameState) {
		if (frameState == State.CODE_SUCCESS || frameState == State.COMPLETED_SUCCESS) {
			action.setGasUsed(action.getGas() - frame.getRemainingGas());
			// extract only CALL output - CREATE output is extracted in bytecode sidecar
			if (action.getCallType() != CREATE) {
				action.setOutput(frame.getOutputData().toArrayUnsafe());
			}
		} else if (frameState == State.REVERT) {
			// deliberate failures do not burn extra gas
			action.setGasUsed(action.getGas() - frame.getRemainingGas());
			// set the revert reason in the action if present
			frame.getRevertReason().ifPresent(bytes -> action.setRevertReason(bytes.toArrayUnsafe()));
		} else if (frameState == State.EXCEPTIONAL_HALT) {
			// exceptional exits always burn all gas
			action.setGasUsed(action.getGas());
			// exceptional halt state always has an exceptional halt reason set
			final var exceptionalHaltReasonOptional = frame.getExceptionalHaltReason();
			if (exceptionalHaltReasonOptional.isPresent()) {
				final var exceptionalHaltReason = exceptionalHaltReasonOptional.get();
				// set the result as error
				action.setError(exceptionalHaltReason.getDescription().getBytes(StandardCharsets.UTF_8));
				// if receiver was an invalid address, clear set receiver
				// and set invalid solidity address field
				if (exceptionalHaltReason.equals(INVALID_SOLIDITY_ADDRESS)) {
					if (action.getRecipientAccount() != null) {
						action.setInvalidSolidityAddress(action.getRecipientAccount().toEvmAddress().toArrayUnsafe());
						action.setRecipientAccount(null);
					} else {
						action.setInvalidSolidityAddress(action.getRecipientContract().toEvmAddress().toArrayUnsafe());
						action.setRecipientContract(null);
					}
				}
			}
		}
	}

	@Override
	public void traceAccountCreationResult(final MessageFrame frame, final Optional<ExceptionalHaltReason> haltReason) {
		frame.setExceptionalHaltReason(haltReason);
	}

	private ContractActionType toContractActionType(final MessageFrame.Type type) {
		return switch (type) {
			case CONTRACT_CREATION -> CREATE;
			case MESSAGE_CALL -> CALL;
		};
	}

	@Override
	public List<SolidityAction> getFinalizedActions() {
		return allActions;
	}
}
