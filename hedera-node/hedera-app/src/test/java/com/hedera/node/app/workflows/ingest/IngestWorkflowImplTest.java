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
package com.hedera.node.app.workflows.ingest;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.hedera.node.app.SessionContext;
import com.hedera.node.app.service.mono.stats.HapiOpCounters;
import com.hedera.node.app.service.token.CryptoQueryHandler;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.common.InsufficientBalanceException;
import com.hedera.node.app.workflows.common.PreCheckException;
import com.hedera.node.app.workflows.common.SubmissionManager;
import com.hedera.node.app.workflows.onset.OnsetResult;
import com.hedera.node.app.workflows.onset.WorkflowOnset;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestWorkflowImplTest {

    private static final TransactionBody TRANSACTION_BODY = TransactionBody.getDefaultInstance();
    private static final SignatureMap SIGNATURE_MAP = SignatureMap.getDefaultInstance();
    private static final OnsetResult ONSET_RESULT =
            new OnsetResult(TRANSACTION_BODY, SIGNATURE_MAP, ConsensusCreateTopic);

    @Mock(strictness = LENIENT)
    private Supplier<AutoCloseableWrapper<HederaState>> stateAccessor;

    @Mock(strictness = LENIENT)
    private WorkflowOnset onset;

    @Mock private IngestChecker checker;

    @Mock(strictness = LENIENT)
    private CryptoService cryptoService;

    @Mock private ThrottleAccumulator throttleAccumulator;
    @Mock private SubmissionManager submissionManager;
    @Mock private HapiOpCounters opCounters;

    @Mock private ByteBuffer requestBuffer;

    @Mock private Parser<Query> queryParser;
    @Mock private Parser<Transaction> txParser;
    @Mock private Parser<SignedTransaction> signedParser;
    @Mock private Parser<TransactionBody> txBodyParser;

    private SessionContext ctx;
    private IngestWorkflowImpl workflow;

    @SuppressWarnings("JUnitMalformedDeclaration")
    @BeforeEach
    void setup(
            @Mock HederaState state,
            @Mock(strictness = LENIENT) CryptoQueryHandler cryptoQueryHandler,
            @Mock Account account)
            throws PreCheckException {
        when(stateAccessor.get()).thenReturn(new AutoCloseableWrapper<>(state, () -> {}));

        when(onset.parseAndCheck(any(), any(ByteBuffer.class))).thenReturn(ONSET_RESULT);

        when(cryptoQueryHandler.getAccountById(any())).thenReturn(Optional.of(account));
        when(cryptoService.createQueryHandler(any())).thenReturn(cryptoQueryHandler);

        ctx = new SessionContext(queryParser, txParser, signedParser, txBodyParser);
        workflow =
                new IngestWorkflowImpl(
                        stateAccessor,
                        onset,
                        checker,
                        cryptoService,
                        throttleAccumulator,
                        submissionManager,
                        opCounters);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidArguments() {
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        null,
                                        onset,
                                        checker,
                                        cryptoService,
                                        throttleAccumulator,
                                        submissionManager,
                                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        stateAccessor,
                                        null,
                                        checker,
                                        cryptoService,
                                        throttleAccumulator,
                                        submissionManager,
                                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        stateAccessor,
                                        onset,
                                        null,
                                        cryptoService,
                                        throttleAccumulator,
                                        submissionManager,
                                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        stateAccessor,
                                        onset,
                                        checker,
                                        null,
                                        throttleAccumulator,
                                        submissionManager,
                                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        stateAccessor,
                                        onset,
                                        checker,
                                        cryptoService,
                                        null,
                                        submissionManager,
                                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        stateAccessor,
                                        onset,
                                        checker,
                                        cryptoService,
                                        throttleAccumulator,
                                        null,
                                        opCounters))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(
                        () ->
                                new IngestWorkflowImpl(
                                        stateAccessor,
                                        onset,
                                        checker,
                                        cryptoService,
                                        throttleAccumulator,
                                        submissionManager,
                                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSuccess() throws PreCheckException, InvalidProtocolBufferException {
        // given
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager).submit(ctx, TRANSACTION_BODY, requestBuffer);
        verify(opCounters).countSubmitted(ConsensusCreateTopic);
    }

    @Test
    void testOnsetFails(@Mock WorkflowOnset localOnset)
            throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(localOnset.parseAndCheck(any(), any(ByteBuffer.class)))
                .thenThrow(new PreCheckException(INVALID_TRANSACTION));
        workflow =
                new IngestWorkflowImpl(
                        stateAccessor,
                        localOnset,
                        checker,
                        cryptoService,
                        throttleAccumulator,
                        submissionManager,
                        opCounters);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(INVALID_TRANSACTION);
        assertThat(response.getCost()).isZero();
        verify(opCounters, never()).countReceived(any());
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testSemanticFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        doThrow(new PreCheckException(NOT_SUPPORTED))
                .when(checker)
                .checkTransactionSemantics(TRANSACTION_BODY, ConsensusCreateTopic);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @SuppressWarnings("JUnitMalformedDeclaration")
    @Test
    void testPayerAccountNotFoundFails(
            @Mock CryptoQueryHandler cryptoQueryHandler, @Mock CryptoService localCryptoService)
            throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(cryptoQueryHandler.getAccountById(any())).thenReturn(Optional.empty());
        when(localCryptoService.createQueryHandler(any())).thenReturn(cryptoQueryHandler);
        workflow =
                new IngestWorkflowImpl(
                        stateAccessor,
                        onset,
                        checker,
                        localCryptoService,
                        throttleAccumulator,
                        submissionManager,
                        opCounters);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(PAYER_ACCOUNT_NOT_FOUND);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(ctx, TRANSACTION_BODY, requestBuffer);
        verify(opCounters, never()).countSubmitted(ConsensusCreateTopic);
    }

    @Test
    void testPayerSignatureFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        doThrow(new PreCheckException(INVALID_PAYER_SIGNATURE))
                .when(checker)
                .checkPayerSignature(eq(TRANSACTION_BODY), eq(SIGNATURE_MAP), any());
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(INVALID_PAYER_SIGNATURE);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testSolvencyFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        doThrow(new InsufficientBalanceException(INSUFFICIENT_ACCOUNT_BALANCE, 42L))
                .when(checker)
                .checkSolvency(eq(TRANSACTION_BODY), eq(ConsensusCreateTopic), any());
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode())
                .isEqualTo(INSUFFICIENT_ACCOUNT_BALANCE);
        assertThat(response.getCost()).isEqualTo(42L);
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    @Test
    void testThrottleFails() throws PreCheckException, InvalidProtocolBufferException {
        // given
        when(throttleAccumulator.shouldThrottle(ConsensusCreateTopic)).thenReturn(true);
        final ByteBuffer responseBuffer = ByteBuffer.allocate(1024 * 6);

        // when
        workflow.submitTransaction(ctx, requestBuffer, responseBuffer);

        // then
        final TransactionResponse response = parseResponse(responseBuffer);
        assertThat(response.getNodeTransactionPrecheckCode()).isEqualTo(BUSY);
        assertThat(response.getCost()).isZero();
        verify(opCounters).countReceived(ConsensusCreateTopic);
        verify(submissionManager, never()).submit(any(), any(), any());
        verify(opCounters, never()).countSubmitted(any());
    }

    private static TransactionResponse parseResponse(ByteBuffer responseBuffer)
            throws InvalidProtocolBufferException {
        final byte[] bytes = new byte[responseBuffer.position()];
        responseBuffer.get(0, bytes);
        return TransactionResponse.parseFrom(bytes);
    }
}
