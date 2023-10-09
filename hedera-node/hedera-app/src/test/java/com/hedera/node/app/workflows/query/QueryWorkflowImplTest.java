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

package com.hedera.node.app.workflows.query;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_GET_INFO;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_STATE_PROOF;
import static com.hedera.hapi.node.base.ResponseType.COST_ANSWER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.FileGetInfoQuery;
import com.hedera.hapi.node.file.FileGetInfoResponse;
import com.hedera.hapi.node.network.NetworkGetExecutionTimeQuery;
import com.hedera.hapi.node.network.NetworkGetExecutionTimeResponse;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.stats.HapiOpCounters;
import com.hedera.node.app.service.networkadmin.impl.handlers.NetworkGetExecutionTimeHandler;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.SynchronizedThrottleAccumulator;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.ingest.IngestChecker;
import com.hedera.node.app.workflows.ingest.SubmissionManager;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.VersionedConfiguration;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.AutoCloseableWrapper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class QueryWorkflowImplTest extends AppTestBase {

    private static final int BUFFER_SIZE = 1024 * 6;
    private static final long DEFAULT_CONFIG_VERSION = 1L;

    @Mock(strictness = LENIENT)
    private Function<ResponseType, AutoCloseableWrapper<HederaState>> stateAccessor;

    @Mock
    private SubmissionManager submissionManager;

    @Mock(strictness = LENIENT)
    private QueryChecker queryChecker;

    @Mock(strictness = LENIENT)
    private IngestChecker ingestChecker;

    @Mock(strictness = LENIENT)
    FileGetInfoHandler handler;

    @Mock(strictness = LENIENT)
    private QueryDispatcher dispatcher;

    @Mock
    private HapiOpCounters opCounters;

    @Mock(strictness = LENIENT)
    private Codec<Query> queryParser;

    @Mock(strictness = LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = LENIENT)
    private RecordCache recordCache;

    @Mock
    private Authorizer authorizer;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock(strictness = LENIENT)
    private FeeManager feeManager;

    @Mock(strictness = LENIENT)
    private SynchronizedThrottleAccumulator synchronizedThrottleAccumulator;

    private VersionedConfiguration configuration;
    private Query query;
    private Transaction payment;
    private TransactionBody txBody;
    private Bytes requestBuffer;
    private TransactionInfo transactionInfo;

    private QueryWorkflowImpl workflow;

    @BeforeEach
    void setup(@Mock FeeCalculator feeCalculator) throws IOException, PreCheckException {
        setupStandardStates();

        when(stateAccessor.apply(any())).thenReturn(new AutoCloseableWrapper<>(state, () -> {}));
        requestBuffer = Bytes.wrap(new byte[] {1, 2, 3});
        payment = Transaction.newBuilder().build();
        final var queryHeader = QueryHeader.newBuilder().payment(payment).build();
        final var query = Query.newBuilder()
                .fileGetInfo(FileGetInfoQuery.newBuilder().header(queryHeader))
                .build();
        when(queryParser.parseStrict(notNull())).thenReturn(query);

        configuration = new VersionedConfigImpl(HederaTestConfigBuilder.createConfig(), DEFAULT_CONFIG_VERSION);
        when(configProvider.getConfiguration()).thenReturn(configuration);

        when(feeManager.createFeeCalculator(eq(FILE_GET_INFO), any())).thenReturn(feeCalculator);

        final var transactionID =
                TransactionID.newBuilder().accountID(ALICE.accountID()).build();
        txBody = TransactionBody.newBuilder().transactionID(transactionID).build();

        final var signatureMap = SignatureMap.newBuilder().build();
        transactionInfo =
                new TransactionInfo(payment, txBody, signatureMap, payment.signedTransactionBytes(), CRYPTO_TRANSFER);
        when(ingestChecker.runAllChecks(state, payment, configuration)).thenReturn(transactionInfo);

        when(handler.extractHeader(query)).thenReturn(queryHeader);
        when(handler.createEmptyResponse(any())).thenAnswer((Answer<Response>) invocation -> {
            final var header = (ResponseHeader) invocation.getArguments()[0];
            return Response.newBuilder()
                    .fileGetInfo(FileGetInfoResponse.newBuilder().header(header).build())
                    .build();
        });

        final var responseHeader = ResponseHeader.newBuilder()
                .responseType(ANSWER_ONLY)
                .nodeTransactionPrecheckCode(OK)
                .build();
        final var fileGetInfo =
                FileGetInfoResponse.newBuilder().header(responseHeader).build();
        final var response = Response.newBuilder().fileGetInfo(fileGetInfo).build();

        when(dispatcher.getHandler(query)).thenReturn(handler);
        when(handler.findResponse(any(), eq(responseHeader))).thenReturn(response);

        workflow = new QueryWorkflowImpl(
                stateAccessor,
                submissionManager,
                queryChecker,
                ingestChecker,
                dispatcher,
                queryParser,
                configProvider,
                recordCache,
                authorizer,
                exchangeRateManager,
                feeManager,
                synchronizedThrottleAccumulator);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        null,
                        submissionManager,
                        queryChecker,
                        ingestChecker,
                        dispatcher,
                        queryParser,
                        configProvider,
                        recordCache,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        null,
                        queryChecker,
                        ingestChecker,
                        dispatcher,
                        queryParser,
                        configProvider,
                        recordCache,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        null,
                        ingestChecker,
                        dispatcher,
                        queryParser,
                        configProvider,
                        recordCache,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        queryChecker,
                        null,
                        dispatcher,
                        queryParser,
                        configProvider,
                        recordCache,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        queryChecker,
                        ingestChecker,
                        null,
                        queryParser,
                        configProvider,
                        recordCache,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        queryChecker,
                        ingestChecker,
                        dispatcher,
                        null,
                        configProvider,
                        recordCache,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        queryChecker,
                        ingestChecker,
                        dispatcher,
                        null,
                        configProvider,
                        recordCache,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        queryChecker,
                        ingestChecker,
                        dispatcher,
                        queryParser,
                        null,
                        recordCache,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        queryChecker,
                        ingestChecker,
                        dispatcher,
                        queryParser,
                        configProvider,
                        null,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        queryChecker,
                        ingestChecker,
                        dispatcher,
                        queryParser,
                        configProvider,
                        recordCache,
                        null,
                        exchangeRateManager,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        queryChecker,
                        ingestChecker,
                        dispatcher,
                        queryParser,
                        configProvider,
                        recordCache,
                        authorizer,
                        null,
                        feeManager,
                        synchronizedThrottleAccumulator))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryWorkflowImpl(
                        stateAccessor,
                        submissionManager,
                        queryChecker,
                        ingestChecker,
                        dispatcher,
                        queryParser,
                        configProvider,
                        recordCache,
                        authorizer,
                        exchangeRateManager,
                        feeManager,
                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testHandleQueryWithIllegalParameters() {
        // given
        final var requestBuffer = Bytes.wrap(new byte[] {1, 2, 3});
        final var responseBuffer = newEmptyBuffer();

        // then
        assertThatThrownBy(() -> workflow.handleQuery(null, responseBuffer)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> workflow.handleQuery(requestBuffer, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSuccessIfPaymentNotRequired() throws IOException {
        // given
        final var responseBuffer = newEmptyBuffer();
        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();
    }

    @Test
    void testSuccessIfPaymentRequired() throws IOException {
        // given
        given(handler.computeFees(any(QueryContext.class))).willReturn(new Fees(100L, 0L, 100L));
        given(handler.requiresNodePayment(any())).willReturn(true);
        when(handler.findResponse(any(), any()))
                .thenReturn(Response.newBuilder()
                        .fileGetInfo(FileGetInfoResponse.newBuilder()
                                .header(ResponseHeader.newBuilder().build())
                                .build())
                        .build());
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();
    }

    @Test
    void testSuccessIfCostOnly() throws IOException {
        // given
        final var queryHeader =
                QueryHeader.newBuilder().responseType(COST_ANSWER).build();
        final var query = Query.newBuilder()
                .fileGetInfo(FileGetInfoQuery.newBuilder().header(queryHeader))
                .build();
        when(queryParser.parseStrict(notNull())).thenReturn(query);
        when(dispatcher.getHandler(query)).thenReturn(handler);
        when(handler.extractHeader(query)).thenReturn(queryHeader);
        when(handler.needsAnswerOnlyCost(COST_ANSWER)).thenReturn(true);
        final var fees = new Fees(1L, 20L, 300L);
        when(handler.computeFees(any())).thenReturn(fees);
        final var responseHeader = ResponseHeader.newBuilder()
                .responseType(COST_ANSWER)
                .nodeTransactionPrecheckCode(OK)
                .cost(321L)
                .build();
        final var expectedResponse = Response.newBuilder()
                .fileGetInfo(FileGetInfoResponse.newBuilder().header(responseHeader))
                .build();
        when(handler.createEmptyResponse(responseHeader)).thenReturn(expectedResponse);
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var actualResponse = parseResponse(responseBuffer);
        final var header = actualResponse.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(header.responseType()).isEqualTo(COST_ANSWER);
        assertThat(header.cost()).isEqualTo(fees.totalFee());
    }

    @Test
    void testParsingFails() throws IOException {
        // given
        when(queryParser.parseStrict(notNull())).thenThrow(new IOException("Expected failure"));
        final var responseBuffer = newEmptyBuffer();

        // then
        assertThatThrownBy(() -> workflow.handleQuery(requestBuffer, responseBuffer))
                .isInstanceOf(StatusRuntimeException.class)
                .hasFieldOrPropertyWithValue("status", Status.INVALID_ARGUMENT);
        verify(opCounters, never()).countReceived(any());
        verify(opCounters, never()).countAnswered(any());
    }

    @Test
    void testUnrecognizableQueryTypeFails() throws IOException {
        // given
        final var query = Query.newBuilder().build();
        when(queryParser.parseStrict(notNull())).thenReturn(query);
        final var responseBuffer = newEmptyBuffer();

        // then
        assertThatThrownBy(() -> workflow.handleQuery(requestBuffer, responseBuffer))
                .isInstanceOf(StatusRuntimeException.class)
                .hasFieldOrPropertyWithValue("status", Status.INVALID_ARGUMENT);
        verify(opCounters, never()).countReceived(any());
        verify(opCounters, never()).countAnswered(any());
    }

    @Test
    void testInvalidNodeFails() throws PreCheckException, IOException {
        // given
        doThrow(new PreCheckException(INVALID_NODE_ACCOUNT)).when(ingestChecker).checkNodeState();
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(INVALID_NODE_ACCOUNT);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();
    }

    @Test
    void testUnsupportedResponseTypeFails() throws IOException {
        // given
        final var localRequestBuffer = newEmptyBuffer();
        final var queryHeader =
                QueryHeader.newBuilder().responseType(ANSWER_STATE_PROOF).build();
        final var query = Query.newBuilder()
                .fileGetInfo(FileGetInfoQuery.newBuilder().header(queryHeader).build())
                .build();
        when(queryParser.parseStrict(notNull())).thenReturn(query);

        final var requestBytes = PbjConverter.asBytes(localRequestBuffer);
        when(handler.extractHeader(query)).thenReturn(queryHeader);
        when(dispatcher.getHandler(query)).thenReturn(handler);
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(Bytes.wrap(requestBytes), responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
        assertThat(header.responseType()).isEqualTo(ANSWER_STATE_PROOF);
        assertThat(header.cost()).isZero();
    }

    @Test
    void testThrottleFails() throws IOException {
        // given
        when(synchronizedThrottleAccumulator.shouldThrottle(eq(HederaFunctionality.FILE_GET_INFO), any()))
                .thenReturn(true);
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();
    }

    @Test
    void testPaidQueryWithInvalidTransactionFails() throws PreCheckException, IOException {
        // given
        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
        doThrow(new PreCheckException(INVALID_TRANSACTION_BODY))
                .when(ingestChecker)
                .runAllChecks(state, payment, configuration);
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(INVALID_TRANSACTION_BODY);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();
    }

    @Test
    void testPaidQueryWithInvalidCryptoTransferFails() throws PreCheckException, IOException {
        // given
        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
        doThrow(new PreCheckException(INSUFFICIENT_TX_FEE)).when(queryChecker).validateCryptoTransfer(transactionInfo);
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(INSUFFICIENT_TX_FEE);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();
    }

    @Test
    void testPaidQueryForSuperUserDoesNotSubmitCryptoTransfer() throws PreCheckException, IOException {
        // given
        given(handler.computeFees(any(QueryContext.class))).willReturn(new Fees(100L, 0L, 100L));
        given(handler.requiresNodePayment(any())).willReturn(true);
        when(handler.findResponse(any(), any()))
                .thenReturn(Response.newBuilder()
                        .fileGetInfo(FileGetInfoResponse.newBuilder()
                                .header(ResponseHeader.newBuilder().build())
                                .build())
                        .build());
        final var responseBuffer = newEmptyBuffer();
        given(authorizer.isSuperUser(ALICE.accountID())).willReturn(true);

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(OK);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();

        verify(submissionManager, never()).submit(any(), any());
    }

    @Test
    void testPaidQueryWithInsufficientPermissionFails() throws PreCheckException, IOException {
        // given
        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
        doThrow(new PreCheckException(NOT_SUPPORTED))
                .when(queryChecker)
                .checkPermissions(ALICE.accountID(), HederaFunctionality.FILE_GET_INFO);
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();
    }

    @Test
    void testPaidQueryWithInsufficientBalanceFails() throws PreCheckException, IOException {
        // given
        given(handler.computeFees(any(QueryContext.class))).willReturn(new Fees(1L, 20L, 300L));
        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
        when(queryChecker.estimateTxFees(
                        any(), any(), eq(transactionInfo), eq(ALICE.account().key()), eq(configuration)))
                .thenReturn(4000L);
        doThrow(new InsufficientBalanceException(INSUFFICIENT_TX_FEE, 12345L))
                .when(queryChecker)
                .validateAccountBalances(any(), eq(transactionInfo), eq(ALICE.account()), eq(321L), eq(4000L));
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(INSUFFICIENT_TX_FEE);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isEqualTo(12345L);
    }

    @Test
    void testUnpaidQueryWithRestrictedFunctionalityFails(@Mock NetworkGetExecutionTimeHandler networkHandler)
            throws IOException {
        // given
        final var localRequestBuffer = newEmptyBuffer();
        final var localQueryHeader =
                QueryHeader.newBuilder().responseType(COST_ANSWER).build();
        final var localQuery = Query.newBuilder()
                .networkGetExecutionTime(
                        NetworkGetExecutionTimeQuery.newBuilder().header(localQueryHeader))
                .build();

        final var requestBytes = PbjConverter.asBytes(localRequestBuffer);
        when(queryParser.parseStrict(notNull())).thenReturn(localQuery);
        when(networkHandler.extractHeader(localQuery)).thenReturn(localQueryHeader);
        when(dispatcher.getHandler(localQuery)).thenReturn(networkHandler);

        final var expectedResponse = Response.newBuilder()
                .networkGetExecutionTime(NetworkGetExecutionTimeResponse.newBuilder()
                        .header(ResponseHeader.newBuilder()
                                .responseType(COST_ANSWER)
                                .nodeTransactionPrecheckCode(NOT_SUPPORTED)
                                .build())
                        .build())
                .build();
        when(networkHandler.createEmptyResponse(notNull())).thenReturn(expectedResponse);
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(Bytes.wrap(requestBytes), responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.networkGetExecutionTimeOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(NOT_SUPPORTED);
        assertThat(header.responseType()).isEqualTo(COST_ANSWER);
        assertThat(header.cost()).isZero();
    }

    @Test
    void testQuerySpecificValidationFails() throws PreCheckException, IOException {
        final var captor = ArgumentCaptor.forClass(QueryContext.class);
        // given
        doThrow(new PreCheckException(ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN))
                .when(handler)
                .validate(captor.capture());
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final Response response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();
        final var queryContext = captor.getValue();
        assertThat(queryContext.payer()).isNull();
    }

    @Test
    void testPaidQueryWithFailingSubmissionFails() throws PreCheckException, IOException {
        // given
        when(handler.requiresNodePayment(ANSWER_ONLY)).thenReturn(true);
        doThrow(new PreCheckException(PLATFORM_TRANSACTION_NOT_CREATED))
                .when(submissionManager)
                .submit(txBody, payment.bodyBytes());
        given(handler.computeFees(any(QueryContext.class))).willReturn(new Fees(100L, 0L, 100L));
        final var responseBuffer = newEmptyBuffer();

        // when
        workflow.handleQuery(requestBuffer, responseBuffer);

        // then
        final var response = parseResponse(responseBuffer);
        final var header = response.fileGetInfoOrThrow().headerOrThrow();
        assertThat(header.nodeTransactionPrecheckCode()).isEqualTo(PLATFORM_TRANSACTION_NOT_CREATED);
        assertThat(header.responseType()).isEqualTo(ANSWER_ONLY);
        assertThat(header.cost()).isZero();
    }

    private static Response parseResponse(BufferedData responseBuffer) throws IOException {
        final byte[] bytes = new byte[Math.toIntExact(responseBuffer.position())];
        responseBuffer.resetPosition();
        responseBuffer.readBytes(bytes);
        return Response.PROTOBUF.parseStrict(BufferedData.wrap(bytes));
    }

    private static BufferedData newEmptyBuffer() {
        return BufferedData.allocate(BUFFER_SIZE);
    }
}
