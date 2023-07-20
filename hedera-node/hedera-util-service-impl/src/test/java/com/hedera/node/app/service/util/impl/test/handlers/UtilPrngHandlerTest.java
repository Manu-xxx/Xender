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

package com.hedera.node.app.service.util.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PRNG_RANGE;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
import com.hedera.node.app.service.networkadmin.ReadableRunningHashLeafStore;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import com.hedera.node.app.service.util.impl.records.PrngRecordBuilder;
import com.hedera.node.app.spi.fixtures.TestBase;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UtilPrngHandlerTest {
    @Mock
    private PreHandleContext preHandleContext;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock
    private ReadableRunningHashLeafStore readableRunningHashLeafStore;

    @Mock
    private PrngRecordBuilder recordBuilder;

    @Mock
    private BlockRecordInfo blockRecordInfo;

    private UtilPrngHandler subject;
    private UtilPrngTransactionBody txn;
    private static final Random random = new Random(92399921);
    private static final Bytes hash = Bytes.wrap(TestBase.randomBytes(random, 48));
    private static final Bytes nMinusThreeHash = hash;

    @BeforeEach
    void setUp() {
        final var config = HederaTestConfigBuilder.create()
                .withValue("utilPrng.isEnabled", true)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);

        subject = new UtilPrngHandler();
        given(handleContext.recordBuilder(PrngRecordBuilder.class)).willReturn(recordBuilder);
        givenTxnWithoutRange();
    }

    @Test
    void preHandleValidatesRange() {
        final var body = TransactionBody.newBuilder()
                .utilPrng(UtilPrngTransactionBody.newBuilder())
                .build();
        given(preHandleContext.body()).willReturn(body);
        assertThatCode(() -> subject.preHandle(preHandleContext)).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidRange() {
        givenTxnWithRange(-10000);
        given(preHandleContext.body())
                .willReturn(TransactionBody.newBuilder().utilPrng(txn).build());

        assertThatThrownBy(() -> subject.preHandle(preHandleContext))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_PRNG_RANGE));
    }

    @Test
    void acceptsPositiveAndZeroRange() {
        givenTxnWithRange(10000);
        given(preHandleContext.body())
                .willReturn(TransactionBody.newBuilder().utilPrng(txn).build());
        assertThatCode(() -> subject.preHandle(preHandleContext)).doesNotThrowAnyException();

        givenTxnWithRange(0);
        given(preHandleContext.body())
                .willReturn(TransactionBody.newBuilder().utilPrng(txn).build());
        assertThatCode(() -> subject.preHandle(preHandleContext)).doesNotThrowAnyException();
    }

    @Test
    void acceptsNoRange() {
        givenTxnWithoutRange();
        given(preHandleContext.body())
                .willReturn(TransactionBody.newBuilder().utilPrng(txn).build());

        assertThatCode(() -> subject.preHandle(preHandleContext)).doesNotThrowAnyException();
    }

    @Test
    void returnsIfNotEnabled() {
        givenTxnWithoutRange();
        final var config = HederaTestConfigBuilder.create()
                .withValue("utilPrng.isEnabled", false)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);

        subject.handle(handleContext);

        verify(recordBuilder, never()).entropyNumber(anyInt());
        verify(recordBuilder, never()).entropyBytes(any());
    }

    @Test
    void followsHappyPathWithNoRange() {
        givenTxnWithoutRange();
        given(handleContext.blockRecordInfo()).willReturn(blockRecordInfo);
        given(blockRecordInfo.getNMinus3RunningHash()).willReturn(nMinusThreeHash);

        subject.handle(handleContext);

        verify(recordBuilder, never()).entropyNumber(anyInt());
        verify(recordBuilder).entropyBytes(hash);
    }

    @Test
    void followsHappyPathWithRange() {
        givenTxnWithRange(20);
        given(handleContext.blockRecordInfo()).willReturn(blockRecordInfo);
        given(blockRecordInfo.getNMinus3RunningHash()).willReturn(nMinusThreeHash);

        subject.handle(handleContext);

        final var argCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(recordBuilder).entropyNumber(argCaptor.capture());
        assertThat(argCaptor.getValue()).isBetween(0, 20);
        verify(recordBuilder, never()).entropyBytes(any());
    }

    @Test
    void followsHappyPathWithMaxIntegerRange() {
        givenTxnWithRange(Integer.MAX_VALUE);
        given(handleContext.blockRecordInfo()).willReturn(blockRecordInfo);
        given(blockRecordInfo.getNMinus3RunningHash()).willReturn(nMinusThreeHash);

        subject.handle(handleContext);

        final var argCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(recordBuilder).entropyNumber(argCaptor.capture());
        assertThat(argCaptor.getValue()).isBetween(0, Integer.MAX_VALUE);
        verify(recordBuilder, never()).entropyBytes(any());
    }

    @Test
    void anyNegativeValueThrowsInPrecheck() {
        givenTxnWithRange(Integer.MIN_VALUE);
        given(preHandleContext.body())
                .willReturn(TransactionBody.newBuilder().utilPrng(txn).build());

        assertThatThrownBy(() -> subject.preHandle(preHandleContext))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_PRNG_RANGE));
    }

    @Test
    void givenRangeZeroGivesBitString() {
        givenTxnWithRange(0);
        given(handleContext.blockRecordInfo()).willReturn(blockRecordInfo);
        given(blockRecordInfo.getNMinus3RunningHash()).willReturn(nMinusThreeHash);

        subject.handle(handleContext);

        verify(recordBuilder, never()).entropyNumber(anyInt());
        verify(recordBuilder).entropyBytes(hash);
    }

    @Test
    void nullBlockRecordInfoThrows() {
        givenTxnWithRange(0);
        given(handleContext.readableStore(ReadableRunningHashLeafStore.class)).willReturn(readableRunningHashLeafStore);
        given(handleContext.blockRecordInfo()).willReturn(null);

        assertThatThrownBy(() -> subject.handle(handleContext)).isInstanceOf(NullPointerException.class);

        verify(recordBuilder, never()).entropyNumber(anyInt());
        verify(recordBuilder, never()).entropyBytes(any());
    }

    @Test
    void nullHashFromRunningHashDoesntReturnAnyValue() throws ExecutionException, InterruptedException {
        givenTxnWithRange(0);
        given(handleContext.readableStore(ReadableRunningHashLeafStore.class)).willReturn(readableRunningHashLeafStore);
        given(handleContext.blockRecordInfo()).willReturn(blockRecordInfo);
        given(blockRecordInfo.getNMinus3RunningHash()).willReturn(null);

        subject.handle(handleContext);

        verify(recordBuilder, never()).entropyNumber(anyInt());
        verify(recordBuilder, never()).entropyBytes(any());
    }

    @Test
    void emptyHashFromRunningHashDoesntReturnAnyValue() {
        givenTxnWithRange(0);
        given(handleContext.readableStore(ReadableRunningHashLeafStore.class)).willReturn(readableRunningHashLeafStore);
        given(handleContext.blockRecordInfo()).willReturn(blockRecordInfo);
        given(blockRecordInfo.getNMinus3RunningHash()).willReturn(Bytes.EMPTY);

        subject.handle(handleContext);

        verify(recordBuilder, never()).entropyNumber(anyInt());
        verify(recordBuilder, never()).entropyBytes(any());
    }

    private void givenTxnWithRange(int range) {
        txn = UtilPrngTransactionBody.newBuilder().range(range).build();
        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().utilPrng(txn).build());
    }

    private void givenTxnWithoutRange() {
        txn = UtilPrngTransactionBody.newBuilder().build();
        given(handleContext.body())
                .willReturn(TransactionBody.newBuilder().utilPrng(txn).build());
    }
}
