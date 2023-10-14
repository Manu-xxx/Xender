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

package com.hedera.node.app.service.contract.impl.test.exec;

import static com.hedera.node.app.service.contract.impl.exec.TransactionModule.provideActionSidecarContentTracer;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_HEDERA_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITH_CALL_DATA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.EvmActionTracer;
import com.hedera.node.app.service.contract.impl.exec.TransactionModule;
import com.hedera.node.app.service.contract.impl.exec.gas.CanonicalDispatchPrices;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.contract.impl.infra.EthereumCallDataHydration;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionModuleTest {
    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private AttributeValidator attributeValidator;

    @Mock
    private TinybarValues tinybarValues;

    @Mock
    private CanonicalDispatchPrices canonicalDispatchPrices;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private HederaOperations hederaOperations;

    @Mock
    private HederaNativeOperations nativeOperations;

    @Mock
    private SystemContractOperations systemContractOperations;

    @Mock
    private EvmFrameStateFactory factory;

    @Mock
    private EthereumCallDataHydration hydration;

    @Mock
    private ReadableFileStore fileStore;

    @Mock
    private HandleContext context;

    @Test
    void createsEvmActionTracer() {
        assertInstanceOf(EvmActionTracer.class, provideActionSidecarContentTracer());
    }

    @Test
    void feesOnlyUpdaterIsProxyUpdater() {
        final var enhancement =
                new HederaWorldUpdater.Enhancement(hederaOperations, nativeOperations, systemContractOperations);
        assertInstanceOf(
                ProxyWorldUpdater.class,
                TransactionModule.provideFeesOnlyUpdater(enhancement, factory).get());
        verify(hederaOperations).begin();
    }

    @Test
    void providesEthTxDataWhenApplicable() {
        final var ethTxn = EthereumTransactionBody.newBuilder()
                .ethereumData(TestHelpers.ETH_WITH_TO_ADDRESS)
                .build();
        final var body =
                TransactionBody.newBuilder().ethereumTransaction(ethTxn).build();
        given(context.body()).willReturn(body);
        final var expectedHydration = HydratedEthTxData.successFrom(ETH_DATA_WITH_CALL_DATA);
        given(hydration.tryToHydrate(ethTxn, fileStore, DEFAULT_HEDERA_CONFIG.firstUserEntity()))
                .willReturn(expectedHydration);
        assertSame(
                expectedHydration,
                TransactionModule.maybeProvideHydratedEthTxData(context, hydration, DEFAULT_HEDERA_CONFIG, fileStore));
    }

    @Test
    void providesEnhancement() {
        given(hederaOperations.begin()).willReturn(hederaOperations);
        assertNotNull(
                TransactionModule.provideEnhancement(hederaOperations, nativeOperations, systemContractOperations));
    }

    @Test
    void providesNullEthTxDataIfNotEthereumTransaction() {
        final var callTxn = ContractCallTransactionBody.newBuilder()
                .contractID(TestHelpers.CALLED_CONTRACT_ID)
                .build();
        final var body = TransactionBody.newBuilder().contractCall(callTxn).build();
        given(context.body()).willReturn(body);
        assertNull(
                TransactionModule.maybeProvideHydratedEthTxData(context, hydration, DEFAULT_HEDERA_CONFIG, fileStore));
    }

    @Test
    void providesSystemGasContractCalculator() {
        given(context.dispatchComputeFees(TransactionBody.DEFAULT)).willReturn(new Fees(1, 2, 3));
        given(tinybarValues.childTransactionServiceGasPrice()).willReturn(2L);
        given(canonicalDispatchPrices.canonicalPriceInTinycents(DispatchType.APPROVE))
                .willReturn(66L);
        given(tinybarValues.asTinybars(66L)).willReturn(7L);
        final var calculator =
                TransactionModule.provideSystemContractGasCalculator(context, canonicalDispatchPrices, tinybarValues);
        final var result = calculator.gasRequirement(TransactionBody.DEFAULT, DispatchType.APPROVE);
        assertEquals(4L, result);
    }

    @Test
    void providesValidators() {
        given(context.attributeValidator()).willReturn(attributeValidator);
        given(context.expiryValidator()).willReturn(expiryValidator);
        assertSame(attributeValidator, TransactionModule.provideAttributeValidator(context));
        assertSame(expiryValidator, TransactionModule.provideExpiryValidator(context));
    }

    @Test
    void providesNetworkInfo() {
        given(context.networkInfo()).willReturn(networkInfo);
        assertSame(networkInfo, TransactionModule.provideNetworkInfo(context));
    }

    @Test
    void providesExpectedConsTime() {
        given(context.consensusNow()).willReturn(Instant.MAX);
        assertSame(Instant.MAX, TransactionModule.provideConsensusTime(context));
    }
}
