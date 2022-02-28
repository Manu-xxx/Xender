package com.hedera.services.contracts.operation;

import com.google.common.collect.ImmutableList;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.utils.EntityNum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.operation.AbstractOperation;

import java.util.Optional;

import static org.apache.tuweni.bytes.Bytes32.leftPad;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

public class HederaLogOperation extends AbstractOperation {
	private static final Logger log = LogManager.getLogger(HederaLogOperation.class);

	private static final Address UNRESOLVABLE_ADDRESS_STANDIN = EntityNum.MISSING_NUM.toEvmAddress();

	private final int numTopics;

	public HederaLogOperation(final int numTopics, final GasCalculator gasCalculator) {
		super(0xA0 + numTopics, "LOG" + numTopics, numTopics + 2, 0, 1, gasCalculator);
		this.numTopics = numTopics;
	}

	@Override
	public OperationResult execute(final MessageFrame frame, final EVM evm) {
		final long dataLocation = clampedToLong(frame.popStackItem());
		final long numBytes = clampedToLong(frame.popStackItem());

		final Gas cost = gasCalculator().logOperationGasCost(frame, dataLocation, numBytes, numTopics);
		final Optional<Gas> optionalCost = Optional.of(cost);
		if (frame.isStatic()) {
			return new OperationResult(
					optionalCost, Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
		} else if (frame.getRemainingGas().compareTo(cost) < 0) {
			return new OperationResult(optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
		}

		final var addressOrAlias = frame.getRecipientAddress();
		final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
		final var aliases = updater.aliases();
		var address = aliases.resolveForEvm(addressOrAlias);
		if (!aliases.isMirror(address)) {
			address = UNRESOLVABLE_ADDRESS_STANDIN;
			log.warn("Could not resolve logger address {}", addressOrAlias);
		}

		final Bytes data = frame.readMemory(dataLocation, numBytes);

		final ImmutableList.Builder<LogTopic> builder = ImmutableList.builderWithExpectedSize(numTopics);
		for (int i = 0; i < numTopics; i++) {
			builder.add(LogTopic.create(leftPad(frame.popStackItem())));
		}

		frame.addLog(new Log(address, data, builder.build()));
		return new OperationResult(optionalCost, Optional.empty());
	}
}
