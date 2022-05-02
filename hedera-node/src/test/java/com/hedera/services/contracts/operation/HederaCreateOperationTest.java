package com.hedera.services.contracts.operation;

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


import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Gas;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class HederaCreateOperationTest {
	private static final Gas baseGas = Gas.of(100);

	@Mock
	private MessageFrame frame;
	@Mock
	private GasCalculator gasCalculator;
	@Mock
	private HederaWorldUpdater hederaWorldUpdater;
	@Mock
	private Address recipientAddr;
	@Mock
	private SyntheticTxnFactory syntheticTxnFactory;
	@Mock
	private EntityCreator creator;
	@Mock
	private RecordsHistorian recordsHistorian;

	private HederaCreateOperation subject;

	@BeforeEach
	void setup() {
		subject = new HederaCreateOperation(
				gasCalculator, creator, syntheticTxnFactory, recordsHistorian);
	}

	@Test
	void isAlwaysEnabled() {
		Assertions.assertTrue(subject.isEnabled());
	}

	@Test
	void computesExpectedCost() {
		given(gasCalculator.createOperationGasCost(frame)).willReturn(baseGas);

		var actualGas = subject.cost(frame);

		assertEquals(baseGas, actualGas);
	}

	@Test
	void computesExpectedTargetAddress() {
		given(frame.getWorldUpdater()).willReturn(hederaWorldUpdater);
		given(frame.getRecipientAddress()).willReturn(recipientAddr);
		given(hederaWorldUpdater.newContractAddress(recipientAddr)).willReturn(Address.ZERO);
		var targetAddr = subject.targetContractAddress(frame);
		assertEquals(Address.ZERO, targetAddr);
	}
}