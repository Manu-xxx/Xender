/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts;

import static com.hedera.node.app.service.contract.impl.exec.failure.CustomExceptionalHaltReason.NOT_SUPPORTED;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.haltResult;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.assertSamePrecompileResult;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HasSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallFactory;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.config.data.ContractsConfig;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HasSystemContractTest {
    @Mock
    private Call call;

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private MessageFrame frame;

    @Mock
    private ProxyWorldUpdater updater;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    @Mock
    private ContractsConfig contractsConfig;

    @Mock
    private HasCallFactory attemptFactory;

    @Mock
    private GasCalculator gasCalculator;

    private MockedStatic<FrameUtils> frameUtils;

    private HasSystemContract subject;
    private final Bytes validInput = Bytes.fromHexString("91548228");

    @BeforeEach
    void setUp() {
        frameUtils = Mockito.mockStatic(FrameUtils.class);
        subject = new HasSystemContract(gasCalculator, attemptFactory);
    }

    @AfterEach
    void clear() {
        frameUtils.close();
    }

    /**
     * The unit tests for HtsSystemContract are also valid for HasSystemContract.
     * Only add tests for unique functionality.
     */
    @Test
    void haltsAndConsumesRemainingGasIfConfigIsOff() {
        frameUtils.when(() -> contractsConfigOf(frame)).thenReturn(contractsConfig);
        when(contractsConfig.systemContractAccountServiceEnabled()).thenReturn(false);
        final var expected = haltResult(NOT_SUPPORTED, frame.getRemainingGas());
        final var result = subject.computeFully(validInput, frame);
        assertSamePrecompileResult(expected, result);
    }
}
