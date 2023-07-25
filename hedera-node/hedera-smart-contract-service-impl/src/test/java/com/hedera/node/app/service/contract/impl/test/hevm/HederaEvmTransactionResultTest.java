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

package com.hedera.node.app.service.contract.impl.test.hevm;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.BESU_LOGS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_EVM_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CHILD_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.GAS_LIMIT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NONCES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OUTPUT_DATA;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOME_STORAGE_ACCESSES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.TWO_STORAGE_ACCESSES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.WEI_NETWORK_GAS_PRICE;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.bloomForAll;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjLogsFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult;
import com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmTransactionResultTest {
    @Mock
    private MessageFrame frame;

    @Mock
    private ProxyWorldUpdater proxyWorldUpdater;

    @Mock
    private RootProxyWorldUpdater committedUpdater;

    @Mock
    private StorageAccessTracker accessTracker;

    @Test
    void abortsWithTranslatedStatus() {
        var subject = HederaEvmTransactionResult.abortFor(INVALID_SIGNATURE);

        assertEquals(INVALID_SIGNATURE, subject.abortReason());
        assertEquals(INVALID_SIGNATURE, subject.finalStatus());
    }

    @Test
    void givenAccessTrackerIncludesFullContractStorageChangesAndNonNullNoncesOnSuccess() {
        given(frame.getContextVariable(FrameUtils.TRACKER_CONTEXT_VARIABLE)).willReturn(accessTracker);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        final var pendingWrites = List.of(TWO_STORAGE_ACCESSES);
        given(proxyWorldUpdater.pendingStorageUpdates()).willReturn(pendingWrites);
        given(accessTracker.getReadsMergedWith(pendingWrites)).willReturn(SOME_STORAGE_ACCESSES);
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getLogs()).willReturn(BESU_LOGS);
        given(frame.getOutputData()).willReturn(pbjToTuweniBytes(OUTPUT_DATA));
        final var createdIds = List.of(CALLED_CONTRACT_ID, CHILD_CONTRACT_ID);
        given(committedUpdater.getCreatedContractIds()).willReturn(createdIds);
        given(committedUpdater.getUpdatedContractNonces()).willReturn(NONCES);

        final var result = HederaEvmTransactionResult.successFrom(
                GAS_LIMIT / 2, CALLED_CONTRACT_ID, CALLED_CONTRACT_EVM_ADDRESS, frame);
        final var protoResult = result.asProtoResultForBase(committedUpdater);
        assertEquals(GAS_LIMIT / 2, protoResult.gasUsed());
        assertEquals(bloomForAll(BESU_LOGS), protoResult.bloom());
        assertEquals(OUTPUT_DATA, protoResult.contractCallResult());
        assertNull(protoResult.errorMessage());
        assertEquals(CALLED_CONTRACT_ID, protoResult.contractID());
        assertEquals(pbjLogsFrom(BESU_LOGS), protoResult.logInfo());
        assertEquals(createdIds, protoResult.createdContractIDs());
        assertEquals(CALLED_CONTRACT_EVM_ADDRESS.evmAddressOrThrow(), protoResult.evmAddress());
        assertEquals(NONCES, protoResult.contractNonces());

        final var expectedChanges = ConversionUtils.asPbjStateChanges(SOME_STORAGE_ACCESSES);
        assertEquals(expectedChanges, result.stateChanges());
        assertEquals(SUCCESS, result.finalStatus());
    }

    @Test
    void givenAccessTrackerIncludesReadStorageAccessesOnlyOnFailure() {
        given(frame.getContextVariable(FrameUtils.TRACKER_CONTEXT_VARIABLE)).willReturn(accessTracker);
        given(accessTracker.getJustReads()).willReturn(SOME_STORAGE_ACCESSES);
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);

        final var result = HederaEvmTransactionResult.failureFrom(GAS_LIMIT / 2, frame);

        final var expectedChanges = ConversionUtils.asPbjStateChanges(SOME_STORAGE_ACCESSES);
        assertEquals(expectedChanges, result.stateChanges());
    }

    @Test
    void withoutAccessTrackerReturnsNullStateChanges() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getOutputData()).willReturn(pbjToTuweniBytes(OUTPUT_DATA));

        final var result = HederaEvmTransactionResult.successFrom(
                GAS_LIMIT / 2, CALLED_CONTRACT_ID, CALLED_CONTRACT_EVM_ADDRESS, frame);

        assertNull(result.stateChanges());
    }
}
