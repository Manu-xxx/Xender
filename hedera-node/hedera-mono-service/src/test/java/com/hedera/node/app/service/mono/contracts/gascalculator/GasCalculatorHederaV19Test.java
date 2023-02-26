/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.contracts.gascalculator;

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

import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.exchangeRate;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.feeData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.contracts.execution.HederaBlockValues;
import com.hedera.node.app.service.evm.fee.codec.SubType;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GasCalculatorHederaV19Test {
    GasCalculatorHederaV19 subject;

    @Mock
    private GlobalDynamicProperties globalDynamicProperties;

    @Mock
    private MessageFrame messageFrame;

    @Mock
    private FeeResourcesLoaderImpl feeResourcesLoader;

    private final Map<SubType, com.hedera.node.app.service.evm.fee.codec.FeeData> feeMap = new HashMap<>();

    @BeforeEach
    void setUp() {
        subject = new GasCalculatorHederaV19(globalDynamicProperties, feeResourcesLoader);
        feeMap.put(SubType.DEFAULT, feeData);
    }

    @Test
    void gasDepositCost() {
        assertEquals(0L, subject.codeDepositGasCost(1));
    }

    @Test
    void transactionIntrinsicGasCost() {
        assertEquals(0L, subject.transactionIntrinsicGasCost(Bytes.of(1, 2, 3), true));
    }

    @Test
    void logOperationGasCost() {
        final var consensusTime = 123L;
        final var functionality = HederaFunctionality.ContractCreate;
        final var returningDeque = new ArrayDeque<MessageFrame>() {};
        returningDeque.add(messageFrame);

        final var rbh = 20000L;
        final var feeComponents = FeeComponents.newBuilder().setRbh(rbh);
        final var feeData = FeeData.newBuilder().setServicedata(feeComponents).build();
        final var blockConsTime = Instant.ofEpochSecond(consensusTime);
        final var blockNo = 123L;

        given(messageFrame.getGasPrice()).willReturn(Wei.of(2000L));
        given(messageFrame.getBlockValues()).willReturn(new HederaBlockValues(10L, blockNo, blockConsTime));
        given(messageFrame.getContextVariable("HederaFunctionality")).willReturn(functionality);
        given(messageFrame.getMessageFrameStack()).willReturn(returningDeque);

        given(feeResourcesLoader.pricesGiven(any(), any())).willReturn(feeMap);
        given(feeResourcesLoader.getCurrentRate()).willReturn(exchangeRate);

        assertEquals(1516L, subject.logOperationGasCost(messageFrame, 1L, 2L, 3));
        verify(messageFrame).getGasPrice();
        verify(messageFrame).getBlockValues();
        verify(messageFrame).getContextVariable("HederaFunctionality");
        verify(messageFrame).getMessageFrameStack();
    }
}
