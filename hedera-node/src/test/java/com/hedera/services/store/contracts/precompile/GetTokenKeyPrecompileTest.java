/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile;

import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.contractAddress;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.fungible;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.invalidTokenIdResult;
import static com.hedera.services.store.contracts.precompile.HTSTestsUtil.successResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.sources.TxnAwareEvmSigsVerifier;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.UsagePricesProvider;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractIDKey;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GetTokenKeyWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenID;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetTokenKeyPrecompileTest {
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private GasCalculator gasCalculator;
    @Mock private MessageFrame frame;
    @Mock private TxnAwareEvmSigsVerifier sigsVerifier;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private DecodingFacade decoder;
    @Mock private EncodingFacade encoder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private ExpiringCreations creator;
    @Mock private ImpliedTransfersMarshal impliedTransfers;
    @Mock private FeeCalculator feeCalculator;
    @Mock private StateView stateView;
    @Mock private HederaStackedWorldStateUpdater worldUpdater;
    @Mock private WorldLedgers wrappedLedgers;
    @Mock private UsagePricesProvider resourceCosts;
    @Mock private HbarCentExchange exchange;
    @Mock private InfrastructureFactory infrastructureFactory;
    @Mock private TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokens;
    @Mock private AssetsLoader assetLoader;
    @Mock private JKey key;
    @Mock private JContractIDKey jContractIDKey;
    @Mock private JDelegatableContractIDKey jDelegatableContractIDKey;

    private HTSPrecompiledContract subject;
    private GetTokenKeyWrapper wrapper = new GetTokenKeyWrapper(fungible, 1L);

    @BeforeEach
    void setUp() throws IOException {
        Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices = new HashMap<>();
        canonicalPrices.put(
                HederaFunctionality.TokenGetInfo, Map.of(SubType.DEFAULT, BigDecimal.valueOf(0)));
        given(assetLoader.loadCanonicalPrices()).willReturn(canonicalPrices);
        PrecompilePricingUtils precompilePricingUtils =
                new PrecompilePricingUtils(
                        assetLoader, exchange, () -> feeCalculator, resourceCosts, stateView);
        subject =
                new HTSPrecompiledContract(
                        dynamicProperties,
                        gasCalculator,
                        recordsHistorian,
                        sigsVerifier,
                        decoder,
                        encoder,
                        syntheticTxnFactory,
                        creator,
                        impliedTransfers,
                        () -> feeCalculator,
                        stateView,
                        precompilePricingUtils,
                        infrastructureFactory);
    }

    @Test
    void computeCallsCorrectImplementationForGetFungibleTokenKey() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        given(tokens.get(fungible, TokenProperty.ADMIN_KEY)).willReturn(key);
        given(key.getECDSASecp256k1Key()).willReturn(new byte[0]);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(decoder.decodeGetTokenKey(input)).willReturn(wrapper);
        given(encoder.encodeGetTokenKey(any())).willReturn(successResult);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void computeCallsCorrectImplementationForGetFungibleTokenKeyWithDelegateContractKey() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        givenJKeyContractAndDelegateContext();
        wrapper = new GetTokenKeyWrapper(fungible, 16L);
        given(tokens.get(fungible, TokenProperty.SUPPLY_KEY)).willReturn(key);
        given(key.getECDSASecp256k1Key()).willReturn(new byte[0]);
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(decoder.decodeGetTokenKey(input)).willReturn(wrapper);
        given(encoder.encodeGetTokenKey(any())).willReturn(successResult);
        given(tokens.exists(any())).willReturn(true);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(successResult, result);
    }

    @Test
    void getTokenKeyCallForInvalidTokenIds() {
        // given
        final var input =
                Bytes.fromHexString(
                        "0x3c4dd32e00000000000000000000000000000000000000000000000000000000000010650000000000000000000000000000000000000000000000000000000000000001");
        givenMinimalFrameContext();
        givenMinimalContextForCall();
        given(wrappedLedgers.tokens()).willReturn(tokens);
        given(tokens.exists(any())).willReturn(false);
        given(decoder.decodeGetTokenKey(input)).willReturn(wrapper);
        // when
        subject.prepareFields(frame);
        subject.prepareComputation(input, a -> a);
        final var result = subject.computeInternal(frame);
        // then
        assertEquals(invalidTokenIdResult, result);
    }

    private void givenMinimalFrameContext() {
        given(frame.getSenderAddress()).willReturn(contractAddress);
        given(frame.getValue()).willReturn(Wei.ZERO);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
    }

    private void givenMinimalContextForCall() {
        Optional<WorldUpdater> parent = Optional.of(worldUpdater);
        given(worldUpdater.parentUpdater()).willReturn(parent);
        given(worldUpdater.permissivelyUnaliased(any()))
                .willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));
        given(worldUpdater.wrappedTrackingLedgers(any())).willReturn(wrappedLedgers);
    }

    private void givenJKeyContractAndDelegateContext() {
        given(key.hasContractID()).willReturn(true);
        given(key.getContractIDKey()).willReturn(jContractIDKey);
        given(jContractIDKey.getContractID()).willReturn(ContractID.getDefaultInstance());
        given(key.hasDelegatableContractId()).willReturn(true);
        given(key.getDelegatableContractIdKey()).willReturn(jDelegatableContractIDKey);
        given(jDelegatableContractIDKey.getContractID())
                .willReturn(ContractID.getDefaultInstance());
    }
}
