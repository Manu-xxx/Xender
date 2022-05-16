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
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.logic.BlockManager;
import com.hedera.services.store.contracts.CodeCache;
import com.hedera.services.store.contracts.HederaWorldState;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.contract.helpers.StorageExpiry;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.plugin.data.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CallLocalEvmTxProcessorTest {
	private static final int MAX_STACK_SIZE = 1024;

	@Mock
	private LivePricesSource livePricesSource;
	@Mock
	private HederaWorldState worldState;
	@Mock
	private CodeCache codeCache;
	@Mock
	private GlobalDynamicProperties globalDynamicProperties;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private Set<Operation> operations;
	@Mock
	private Transaction transaction;
	@Mock
	private HederaWorldState.Updater updater;
	@Mock
	private Map<String, PrecompiledContract> precompiledContractMap;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private StorageExpiry storageExpiry;
	@Mock
	private StorageExpiry.Oracle oracle;
	@Mock
	private BlockManager blockManager;
	@Mock
	private HederaBlockValues hederaBlockValues;

	private final Account sender = new Account(new Id(0, 0, 1002));
	private final Account receiver = new Account(new Id(0, 0, 1006));
	private final Address receiverAddress = receiver.getId().asEvmAddress();
	private final Instant consensusTime = Instant.now();

	private CallLocalEvmTxProcessor callLocalEvmTxProcessor;

	@BeforeEach
	private void setup() {
		CommonProcessorSetup.setup(gasCalculator);

		callLocalEvmTxProcessor = new CallLocalEvmTxProcessor(
				codeCache, livePricesSource, globalDynamicProperties,
				gasCalculator, operations, precompiledContractMap, aliasManager, storageExpiry, blockManager);

		callLocalEvmTxProcessor.setWorldState(worldState);
	}

	@Test
	void assertSuccessExecutе() {
		givenValidMock();
		given(storageExpiry.hapiStaticCallOracle()).willReturn(oracle);
		final var receiverAddress = receiver.getId().asEvmAddress();
		given(aliasManager.resolveForEvm(receiverAddress)).willReturn(receiverAddress);
		var result = callLocalEvmTxProcessor.execute(
				sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime);
		assertTrue(result.isSuccessful());
		assertEquals(receiver.getId().asGrpcContract(), result.toGrpc().getContractID());
	}


	@Test
	void throwsWhenCodeCacheFailsLoading() {
		given(worldState.updater()).willReturn(updater);
		given(worldState.updater().updater()).willReturn(updater);
		given(storageExpiry.hapiStaticCallOracle()).willReturn(oracle);
		given(globalDynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());

		var evmAccount = mock(EvmAccount.class);

		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(Gas.ZERO);

		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(evmAccount);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()).getMutable()).willReturn(
				mock(MutableAccount.class));
		given(worldState.updater()).willReturn(updater);

		var senderMutableAccount = mock(MutableAccount.class);

		given(updater.getSenderAccount(any())).willReturn(evmAccount);
		given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
		given(updater.getOrCreate(any())).willReturn(evmAccount);
		given(updater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);

		assertFailsWith(() ->
						callLocalEvmTxProcessor.execute(
								sender, receiverAddress, 33_333L, 1234L, Bytes.EMPTY, consensusTime),
				INVALID_CONTRACT_ID);
	}

	@Test
	void assertIsContractCallFunctionality() {
		//expect:
		assertEquals(HederaFunctionality.ContractCallLocal, callLocalEvmTxProcessor.getFunctionType());
	}

	@Test
	void assertTransactionSenderAndValue() {
		// setup:
		doReturn(Optional.of(receiver.getId().asEvmAddress())).when(transaction).getTo();
		given(codeCache.getIfPresent(any())).willReturn(new Code());
		given(transaction.getSender()).willReturn(sender.getId().asEvmAddress());
		given(transaction.getValue()).willReturn(Wei.of(1L));
		final MessageFrame.Builder commonInitialFrame =
				MessageFrame.builder()
						.messageFrameStack(mock(Deque.class))
						.maxStackSize(MAX_STACK_SIZE)
						.worldUpdater(mock(WorldUpdater.class))
						.initialGas(mock(Gas.class))
						.originator(sender.getId().asEvmAddress())
						.gasPrice(mock(Wei.class))
						.sender(sender.getId().asEvmAddress())
						.value(Wei.of(transaction.getValue().getAsBigInteger()))
						.apparentValue(Wei.of(transaction.getValue().getAsBigInteger()))
						.blockValues(mock(BlockValues.class))
						.depth(0)
						.completer(__ -> {
						})
						.miningBeneficiary(mock(Address.class))
						.blockHashLookup(h -> null);
		//when:
		MessageFrame buildMessageFrame = callLocalEvmTxProcessor.buildInitialFrame(commonInitialFrame,
				(Address) transaction.getTo().get(), Bytes.EMPTY, 0L);

		//expect:
		assertEquals(transaction.getSender(), buildMessageFrame.getSenderAddress());
		assertEquals(transaction.getValue(), buildMessageFrame.getApparentValue());
	}

	private void givenValidMock() {
		given(worldState.updater()).willReturn(updater);
		given(worldState.updater().updater()).willReturn(updater);
		given(globalDynamicProperties.fundingAccount()).willReturn(new Id(0, 0, 1010).asGrpcAccount());

		var evmAccount = mock(EvmAccount.class);

		given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, false)).willReturn(Gas.ZERO);

		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress())).willReturn(evmAccount);
		given(updater.getOrCreateSenderAccount(sender.getId().asEvmAddress()).getMutable()).willReturn(
				mock(MutableAccount.class));
		given(worldState.updater()).willReturn(updater);
		given(codeCache.getIfPresent(any())).willReturn(new Code());


		var senderMutableAccount = mock(MutableAccount.class);
		given(senderMutableAccount.decrementBalance(any())).willReturn(Wei.of(1234L));
		given(senderMutableAccount.incrementBalance(any())).willReturn(Wei.of(1500L));

		given(gasCalculator.getSelfDestructRefundAmount()).willReturn(Gas.ZERO);
		given(gasCalculator.getMaxRefundQuotient()).willReturn(2L);

		given(updater.getSenderAccount(any())).willReturn(evmAccount);
		given(updater.getSenderAccount(any()).getMutable()).willReturn(senderMutableAccount);
		given(updater.getOrCreate(any())).willReturn(evmAccount);
		given(updater.getOrCreate(any()).getMutable()).willReturn(senderMutableAccount);
		given(updater.getSbhRefund()).willReturn(Gas.ZERO);

		given(blockManager.computeProvisionalBlockValues(any(), anyLong())).willReturn(hederaBlockValues);
	}
}
