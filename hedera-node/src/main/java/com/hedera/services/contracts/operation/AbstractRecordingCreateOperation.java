package com.hedera.services.contracts.operation;

import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;

import java.util.Optional;

import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public abstract class AbstractRecordingCreateOperation extends AbstractOperation {
	protected static final Operation.OperationResult UNDERFLOW_RESPONSE =
			new Operation.OperationResult(
					Optional.empty(), Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));

	private final SyntheticTxnFactory syntheticTxnFactory;

	protected AbstractRecordingCreateOperation(
			final int opcode,
			final String name,
			final int stackItemsConsumed,
			final int stackItemsProduced,
			final int opSize,
			final GasCalculator gasCalculator,
			final SyntheticTxnFactory syntheticTxnFactory
	) {
		super(opcode, name, stackItemsConsumed, stackItemsProduced, opSize, gasCalculator);
		this.syntheticTxnFactory = syntheticTxnFactory;
	}

	@Override
	public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
		// manual check because some reads won't come until the "complete" step.
		if (frame.stackSize() < getStackItemsConsumed()) {
			return UNDERFLOW_RESPONSE;
		}

		final Gas cost = cost(frame);
		final Optional<Gas> optionalCost = Optional.ofNullable(cost);
		if (cost != null) {
			if (frame.isStatic()) {
				return new Operation.OperationResult(
						optionalCost, Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
			} else if (frame.getRemainingGas().compareTo(cost) < 0) {
				return new Operation.OperationResult(
						optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
			}
			final Wei value = Wei.wrap(frame.getStackItem(0));

			final Address address = frame.getRecipientAddress();
			final MutableAccount account = frame.getWorldUpdater().getAccount(address).getMutable();

			frame.clearReturnData();

			if (value.compareTo(account.getBalance()) > 0 || frame.getMessageStackDepth() >= 1024) {
				fail(frame);
			} else {
				spawnChildMessage(frame);
			}
		}

		return new Operation.OperationResult(optionalCost, Optional.empty());
	}

	protected abstract Gas cost(final MessageFrame frame);

	protected abstract Address targetContractAddress(MessageFrame frame);

	private void fail(final MessageFrame frame) {
		final long inputOffset = clampedToLong(frame.getStackItem(1));
		final long inputSize = clampedToLong(frame.getStackItem(2));
		frame.readMutableMemory(inputOffset, inputSize);
		frame.popStackItems(getStackItemsConsumed());
		frame.pushStackItem(UInt256.ZERO);
	}

	private void spawnChildMessage(final MessageFrame frame) {
		// memory cost needs to be calculated prior to memory expansion
		final Gas cost = cost(frame);
		frame.decrementRemainingGas(cost);

		final Address address = frame.getRecipientAddress();
		final MutableAccount account = frame.getWorldUpdater().getAccount(address).getMutable();

		account.incrementNonce();

		final Wei value = Wei.wrap(frame.getStackItem(0));
		final long inputOffset = clampedToLong(frame.getStackItem(1));
		final long inputSize = clampedToLong(frame.getStackItem(2));
		final Bytes inputData = frame.readMemory(inputOffset, inputSize);

		final Address contractAddress = targetContractAddress(frame);

		final Gas childGasStipend = gasCalculator().gasAvailableForChildCreate(frame.getRemainingGas());
		frame.decrementRemainingGas(childGasStipend);

		final MessageFrame childFrame =
				MessageFrame.builder()
						.type(MessageFrame.Type.CONTRACT_CREATION)
						.messageFrameStack(frame.getMessageFrameStack())
						.worldUpdater(frame.getWorldUpdater().updater())
						.initialGas(childGasStipend)
						.address(contractAddress)
						.originator(frame.getOriginatorAddress())
						.contract(contractAddress)
						.gasPrice(frame.getGasPrice())
						.inputData(Bytes.EMPTY)
						.sender(frame.getRecipientAddress())
						.value(value)
						.apparentValue(value)
						.code(new Code(inputData, Hash.EMPTY))
						.blockValues(frame.getBlockValues())
						.depth(frame.getMessageStackDepth() + 1)
						.completer(child -> complete(frame, child))
						.miningBeneficiary(frame.getMiningBeneficiary())
						.blockHashLookup(frame.getBlockHashLookup())
						.maxStackSize(frame.getMaxStackSize())
						.build();

		frame.incrementRemainingGas(cost);

		frame.getMessageFrameStack().addFirst(childFrame);
		frame.setState(MessageFrame.State.CODE_SUSPENDED);
	}

	private void complete(final MessageFrame frame, final MessageFrame childFrame) {
		frame.setState(MessageFrame.State.CODE_EXECUTING);

		frame.incrementRemainingGas(childFrame.getRemainingGas());
		frame.addLogs(childFrame.getLogs());
		frame.addSelfDestructs(childFrame.getSelfDestructs());
		frame.incrementGasRefund(childFrame.getGasRefund());
		frame.popStackItems(getStackItemsConsumed());

		if (childFrame.getState() == MessageFrame.State.COMPLETED_SUCCESS) {
			frame.mergeWarmedUpFields(childFrame);
			frame.pushStackItem(Words.fromAddress(childFrame.getContractAddress()));
		} else {
			frame.setReturnData(childFrame.getOutputData());
			frame.pushStackItem(UInt256.ZERO);
		}

		final int currentPC = frame.getPC();
		frame.setPC(currentPC + 1);
	}
}
