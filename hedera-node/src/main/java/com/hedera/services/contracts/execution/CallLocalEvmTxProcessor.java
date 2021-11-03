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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractAccessEntry;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Extension of the base {@link EvmTxProcessor} that provides interface for
 * executing {@link com.hederahashgraph.api.proto.java.ContractCallLocal} queries
 */
@Singleton
public class CallLocalEvmTxProcessor extends EvmTxProcessor {
	@Inject
	public CallLocalEvmTxProcessor(
			HederaWorldState worldState,
			HbarCentExchange exchange,
			UsagePricesProvider usagePrices,
			GlobalDynamicProperties dynamicProperties,
			GasCalculator gasCalculator,
			Set<Operation> hederaOperations) {
		super(worldState, exchange, usagePrices, dynamicProperties, gasCalculator, hederaOperations);
	}

	@Override
	protected HederaFunctionality getFunctionType() {
		return HederaFunctionality.ContractCallLocal;
	}

	public TransactionProcessingResult execute(
			final Account sender,
			final Address receiver,
			final long providedGasLimit,
			final long value,
			final Bytes callData,
			final Instant consensusTime,
			final List<AccountID> accountAccessList,
			final List<ContractAccessEntry> contractAccessList
			) {
		final long gasPrice = 1;

		return super.execute(sender,
				receiver,
				gasPrice,
				providedGasLimit,
				value,
				callData,
				false,
				consensusTime,
				true,
				Optional.empty(),
				accountAccessList,
				contractAccessList);
	}

	@Override
	protected MessageFrame buildInitialFrame(MessageFrame.Builder baseInitialFrame, HederaWorldState.Updater updater, Address to, Bytes payload) {
		Bytes code = updater.get(to).getCode();
		return baseInitialFrame
				.type(MessageFrame.Type.MESSAGE_CALL)
				.address(to)
				.contract(to)
				.inputData(payload)
				.code(new Code(code, Hash.hash(code)))
				.build();
	}
}
