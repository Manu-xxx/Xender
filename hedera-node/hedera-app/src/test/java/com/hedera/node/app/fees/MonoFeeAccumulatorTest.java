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

package com.hedera.node.app.fees;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.consensus.ConsensusGetTopicInfoQuery;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hederahashgraph.api.proto.java.FeeData;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoFeeAccumulatorTest {
    private final Query mockQuery = Query.newBuilder()
            .consensusGetTopicInfo(ConsensusGetTopicInfoQuery.DEFAULT)
            .build();
    private final FeeData mockUsage = FeeData.getDefaultInstance();
    private final FeeData mockPrices = FeeData.getDefaultInstance();
    private final Instant mockInstant = Instant.ofEpochSecond(1_234_567L);

    @Mock
    private UsageBasedFeeCalculator usageBasedFeeCalculator;

    @Mock
    private UsagePricesProvider usagePricesProvider;

    @Mock
    private StateView stateView;

    @Mock
    private MonoGetTopicInfoUsage getTopicInfoUsage;

    @Mock
    private ReadableStoreFactory readableStoreFactory;

    @Mock
    private ReadableTopicStore readableTopicStore;

    private MonoFeeAccumulator subject;

    @BeforeEach
    void setUp() {
        subject = new MonoFeeAccumulator(
                usageBasedFeeCalculator, getTopicInfoUsage, usagePricesProvider, () -> stateView);
    }

    @Test
    void usesMonoAdapterDirectlyForGetTopicInfo() {
        final var expectedFees = new FeeObject(100L, 0L, 100L);
        final var timestamp = PbjConverter.fromPbj(HapiUtils.asTimestamp(mockInstant));
        given(usagePricesProvider.defaultPricesGiven(ConsensusGetTopicInfo, timestamp))
                .willReturn(mockPrices);
        given(readableStoreFactory.getStore(ReadableTopicStore.class)).willReturn(readableTopicStore);
        given(getTopicInfoUsage.computeUsage(PbjConverter.fromPbj(mockQuery), readableTopicStore))
                .willReturn(mockUsage);
        given(usageBasedFeeCalculator.computeFromQueryResourceUsage(mockUsage, mockUsage, timestamp))
                .willReturn(expectedFees);

        final var actualFees = subject.computePayment(mockQuery, mockInstant, readableStoreFactory);

        assertSame(expectedFees, actualFees);
    }

    @Test
    void delegatedComputePaymentForQuery() {
        final var expectedFee = new FeeObject(100L, 0L, 100L);

        given(usagePricesProvider.defaultPricesGiven(eq(ConsensusGetTopicInfo), any()))
                .willReturn(mockPrices);
        given(readableStoreFactory.getStore(ReadableTopicStore.class)).willReturn(readableTopicStore);
        given(getTopicInfoUsage.computeUsage(any(), eq(readableTopicStore))).willReturn(mockUsage);
        given(usageBasedFeeCalculator.computeFromQueryResourceUsage(eq(mockUsage), eq(mockPrices), any()))
                .willReturn(expectedFee);

        final var fee = subject.computePayment(mockQuery, mockInstant, readableStoreFactory);

        assertSame(expectedFee, fee);
    }
}
