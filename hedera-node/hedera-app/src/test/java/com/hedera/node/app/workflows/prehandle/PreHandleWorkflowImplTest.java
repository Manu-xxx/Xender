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
package com.hedera.node.app.workflows.prehandle;

import com.hedera.node.app.ServicesAccessor;
import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.consensus.ConsensusPreTransactionHandler;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.mono.config.AccountNumbers;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.scheduled.ScheduleService;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.meta.ErrorTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.common.PreCheckException;
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hederahashgraph.api.proto.java.*;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.system.transaction.internal.SwirldTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreHandleWorkflowImplTest {

    @Mock private ExecutorService executorService;
    @Mock private CryptoService cryptoService;
    @Mock private WorkflowOnset onset;

    @Mock private HederaState state;
    @Mock private Event event;

    private ServicesAccessor servicesAccessor;
    private PreHandleContext context;

    private PreHandleWorkflowImpl workflow;

    @BeforeEach
    void setup(
            @Mock ContractService contractService,
            @Mock ConsensusService consensusService,
            @Mock FileService fileService,
            @Mock FreezeService freezeService,
            @Mock NetworkService networkService,
            @Mock ScheduleService scheduleService,
            @Mock TokenService tokenService,
            @Mock UtilService utilService,
            @Mock AccountNumbers accountNumbers,
            @Mock HederaFileNumbers hederaFileNumbers) {
        servicesAccessor =
                new ServicesAccessor(
                        consensusService,
                        contractService,
                        cryptoService,
                        fileService,
                        freezeService,
                        networkService,
                        scheduleService,
                        tokenService,
                        utilService);

        context = new PreHandleContext(accountNumbers, hederaFileNumbers);

        workflow = new PreHandleWorkflowImpl(executorService, servicesAccessor, context, onset);
    }

    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(null, servicesAccessor, context, onset))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleWorkflowImpl(executorService, null, context, onset))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new PreHandleWorkflowImpl(
                                        executorService, servicesAccessor, null, onset))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new PreHandleWorkflowImpl(
                                        executorService, servicesAccessor, context, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testStartWithIllegalParameters() {
        // then
        assertThatThrownBy(() -> workflow.start(null, event))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> workflow.start(state, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testStartEventWithNoTransactions() {
        // given
        when(event.transactionIterator()).thenReturn(Collections.emptyIterator());

        // when
        assertThatCode(() -> workflow.start(state, event)).doesNotThrowAnyException();
    }

    @Test
    void testStartEventWithTwoTransactions(
            @Mock SwirldTransaction transaction1, @Mock SwirldTransaction transaction2) {
        // given
        final Iterator<Transaction> iterator =
                List.of(transaction1, (Transaction) transaction2).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(state, event);

        // then
        verify(transaction1).setMetadata(any());
        verify(transaction2).setMetadata(any());
    }

    @Test
    void testUnchangedStateDoesNotRegenerateHandlers(@Mock SwirldTransaction transaction) {
        // given
        final Iterator<Transaction> iterator = List.of((Transaction) transaction).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(state, event);
        workflow.start(state, event);

        // then
        verify(cryptoService, times(1)).createPreTransactionHandler(any(), any());
    }

    @Test
    void testChangedStateDoesRegenerateHandlers(
            @Mock HederaState state2, @Mock SwirldTransaction transaction) {
        // given
        final Iterator<Transaction> iterator = List.of((Transaction) transaction).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(state, event);
        workflow.start(state2, event);

        // then
        verify(cryptoService, times(2)).createPreTransactionHandler(any(), any());
    }

    @Test
    void testPreHandleSuccess(
            @Mock CryptoPreTransactionHandler preTransactionHandler,
            @Mock TransactionMetadata metadata,
            @Mock SwirldTransaction transaction)
            throws PreCheckException {
        // given
        when(executorService.submit(any(Callable.class)))
                .thenAnswer(
                        (Answer<Future<TransactionMetadata>>)
                                invocation ->
                                        CompletableFuture.completedFuture(
                                                (TransactionMetadata)
                                                        invocation
                                                                .getArgument(0, Callable.class)
                                                                .call()));

        final CryptoCreateTransactionBody content =
                CryptoCreateTransactionBody.newBuilder().build();
        final TransactionBody txBody =
                TransactionBody.newBuilder().setCryptoCreateAccount(content).build();
        final SignatureMap signatureMap = SignatureMap.newBuilder().build();
        final HederaFunctionality functionality = HederaFunctionality.CryptoCreate;
        final OnsetResult onsetResult = new OnsetResult(txBody, signatureMap, functionality);
        when(onset.parseAndCheck(any(), any())).thenReturn(onsetResult);
        when(cryptoService.createPreTransactionHandler(any(), eq(context)))
                .thenReturn(preTransactionHandler);
        when(preTransactionHandler.preHandle(eq(txBody), any())).thenReturn(metadata);


        final Iterator<Transaction> iterator = List.of((Transaction) transaction).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(state, event);

        // then
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Future<TransactionMetadata>> captor =
                ArgumentCaptor.forClass(Future.class);
        verify(transaction).setMetadata(captor.capture());
        assertThat(captor.getValue()).succeedsWithin(Duration.ofMillis(100)).isEqualTo(metadata);
    }

    @Test
    void testPreHandleOnsetFails(
            @Mock ConsensusPreTransactionHandler preTransactionHandler,
            @Mock SwirldTransaction transaction)
            throws PreCheckException {
        // given
        when(executorService.submit(any(Callable.class)))
                .thenAnswer(
                        (Answer<Future<TransactionMetadata>>)
                                invocation ->
                                        CompletableFuture.completedFuture(
                                                (TransactionMetadata)
                                                        invocation
                                                                .getArgument(0, Callable.class)
                                                                .call()));

        when(onset.parseAndCheck(any(), any()))
                .thenThrow(new PreCheckException(INVALID_TRANSACTION));

        final Iterator<Transaction> iterator = List.of((Transaction) transaction).iterator();
        when(event.transactionIterator()).thenReturn(iterator);

        // when
        workflow.start(state, event);

        // then
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Future<TransactionMetadata>> captor =
                ArgumentCaptor.forClass(Future.class);
        verify(transaction).setMetadata(captor.capture());
        assertThat(captor.getValue())
                .succeedsWithin(Duration.ofMillis(100))
                .isInstanceOf(ErrorTransactionMetadata.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
    }
}
