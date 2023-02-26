/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store.contracts.precompile.utils;

import static com.hedera.node.app.hapi.fees.pricing.FeeSchedules.USD_TO_TINYCENTS;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertExchangeRateFromDtoToProto;
import static com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter.convertTimestampFromProtoToDto;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.exchangeRate;
import static com.hedera.node.app.service.mono.store.contracts.precompile.HTSTestsUtil.feeData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.FeeResourcesLoaderImpl;
import com.hedera.node.app.service.mono.fees.calculation.utils.FeeConverter;
import com.hedera.node.app.service.mono.utils.accessors.AccessorFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Provider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrecompilePricingUtilsTest {

    private static final long COST = 36;
    private static final int CENTS_RATE = 12;
    private static final int HBAR_RATE = 1;
    private final Map<
                    com.hedera.node.app.service.evm.fee.codec.SubType,
                    com.hedera.node.app.service.evm.fee.codec.FeeData>
            feeMap = new HashMap<>();

    @Mock
    private AssetsLoader assetLoader;

    @Mock
    private ExchangeRate exchangeRateProto;

    @Mock
    private Provider<FeeCalculator> feeCalculator;

    @Mock
    private StateView stateView;

    @Mock
    private AccessorFactory accessorFactory;

    @Mock
    private FeeResourcesLoaderImpl feeResourcesLoader;

    private MockedStatic<FeeConverter> feeConverter;

    @BeforeEach
    void setup() {
        feeConverter = Mockito.mockStatic(FeeConverter.class);
        feeMap.put(com.hedera.node.app.service.evm.fee.codec.SubType.DEFAULT, feeData);
    }

    @AfterEach
    void closeMocks() {
        if (!feeConverter.isClosed()) {
            feeConverter.close();
        }
    }

    @Test
    void failsToLoadCanonicalPrices() throws IOException {
        given(assetLoader.loadCanonicalPrices()).willThrow(IOException.class);
        assertThrows(
                PrecompilePricingUtils.CanonicalOperationsUnloadableException.class,
                () -> new PrecompilePricingUtils(
                        assetLoader, feeCalculator, stateView, accessorFactory, feeResourcesLoader));
    }

    @Test
    void calculatesMinimumPrice() throws IOException {
        final Timestamp timestamp = Timestamp.newBuilder().setSeconds(123456789).build();
        given(assetLoader.loadCanonicalPrices())
                .willReturn(Map.of(
                        HederaFunctionality.TokenAssociateToAccount,
                        Map.of(SubType.DEFAULT, BigDecimal.valueOf(COST))));

        feeConverter
                .when(() -> convertTimestampFromProtoToDto(timestamp))
                .thenReturn(new com.hedera.node.app.service.evm.utils.codec.Timestamp(
                        timestamp.getSeconds(), timestamp.getNanos()));
        feeConverter.when(() -> convertExchangeRateFromDtoToProto(exchangeRate)).thenReturn(exchangeRateProto);
        given(exchangeRateProto.getCentEquiv()).willReturn(CENTS_RATE);
        given(exchangeRateProto.getHbarEquiv()).willReturn(HBAR_RATE);

        given(feeResourcesLoader.getCurrentRate()).willReturn(exchangeRate);
        given(feeResourcesLoader.getNextRate()).willReturn(exchangeRate);

        final PrecompilePricingUtils subject =
                new PrecompilePricingUtils(assetLoader, feeCalculator, stateView, accessorFactory, feeResourcesLoader);

        final long price = subject.getMinimumPriceInTinybars(PrecompilePricingUtils.GasCostType.ASSOCIATE, timestamp);

        assertEquals(
                USD_TO_TINYCENTS
                        .multiply(BigDecimal.valueOf(COST * HBAR_RATE / CENTS_RATE))
                        .longValue(),
                price);
    }
}
