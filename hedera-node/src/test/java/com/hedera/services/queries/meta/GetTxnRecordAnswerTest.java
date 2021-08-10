package com.hedera.services.queries.meta;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */


import com.hedera.services.context.StateChildren;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionGetRecordQuery;
import com.hederahashgraph.api.proto.java.TransactionGetRecordResponse;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.state.submerkle.ExpirableTxnRecordTestHelper.fromGprc;
import static com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RESULT_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

class GetTxnRecordAnswerTest {
	private String payer = "0.0.12345";
	private TransactionID targetTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount(payer))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(1_234L))
			.build();
	private TransactionID missingTxnId = TransactionID.newBuilder()
			.setAccountID(asAccount(payer))
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(4_321L))
			.build();
	private ExpirableTxnRecord targetRecord = constructTargetRecord();
	private TransactionRecord cachedTargetRecord = targetRecord.asGrpc();

	private StateView view;
	private RecordCache recordCache;
	private AnswerFunctions answerFunctions;
	private OptionValidator optionValidator;
	private String node = "0.0.3";
	private long fee = 1_234L;
	private Transaction paymentTxn;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;

	private GetTxnRecordAnswer subject;
	private NodeLocalProperties nodeProps;

	@BeforeEach
	private void setup() {
		recordCache = mock(RecordCache.class);
		accounts = mock(FCMap.class);
		nodeProps = mock(NodeLocalProperties.class);
		final StateChildren children = new StateChildren();
		children.setAccounts(accounts);
		view = new StateView(
				null,
				null,
				nodeProps,
				children,
				EMPTY_UNIQ_TOKEN_VIEW_FACTORY);
		optionValidator = mock(OptionValidator.class);
		answerFunctions = mock(AnswerFunctions.class);

		subject = new GetTxnRecordAnswer(recordCache, optionValidator, answerFunctions);
	}

	@Test
	void getsExpectedPayment() throws Throwable {
		// given:
		Query query = getRecordQuery(targetTxnId, COST_ANSWER, 5L);

		// expect:
		assertEquals(paymentTxn, subject.extractPaymentFrom(query).get().getSignedTxnWrapper());
	}

	@Test
	void getsValidity() {
		// given:
		Response response = Response.newBuilder().setTransactionGetRecord(
				TransactionGetRecordResponse.newBuilder()
						.setHeader(subject.answerOnlyHeader(RESULT_SIZE_LIMIT_EXCEEDED))).build();

		// expect:
		assertEquals(RESULT_SIZE_LIMIT_EXCEEDED, subject.extractValidityFrom(response));
	}

	@Test
	void getsCostAnswerResponse() throws Throwable {
		// setup:
		Query query = getRecordQuery(targetTxnId, COST_ANSWER, fee);

		// when:
		Response response = subject.responseGiven(query, view, OK, fee);

		// then:
		assertTrue(response.hasTransactionGetRecord());
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(COST_ANSWER, opResponse.getHeader().getResponseType());
		assertEquals(fee, opResponse.getHeader().getCost());
	}

	@Test
	void getsRecordWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);
		given(answerFunctions.txnRecord(recordCache, view, sensibleQuery))
				.willReturn(Optional.of(cachedTargetRecord));

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
		// and:
		verify(recordCache, never()).getDuplicateRecords(any());
	}

	@Test
	void getsRecordFromCtxWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);
		Map<String, Object> ctx = new HashMap<>();

		// given:
		ctx.put(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY, cachedTargetRecord);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
		// and:
		verify(answerFunctions, never()).txnRecord(any(), any(), any());
	}

	@Test
	void getsDuplicateRecordsFromCtxWhenAvailable() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L, true);
		Map<String, Object> ctx = new HashMap<>();

		// given:
		ctx.put(GetTxnRecordAnswer.PRIORITY_RECORD_CTX_KEY, cachedTargetRecord);
		ctx.put(GetTxnRecordAnswer.DUPLICATE_RECORDS_CTX_KEY, List.of(cachedTargetRecord));

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, ctx);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
		assertEquals(List.of(cachedTargetRecord), opResponse.getDuplicateTransactionRecordsList());
	}

	@Test
	void recognizesMissingRecordWhenCtxGiven() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L, Collections.emptyMap());

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(RECORD_NOT_FOUND, opResponse.getHeader().getNodeTransactionPrecheckCode());
		verify(answerFunctions, never()).txnRecord(any(), any(), any());
	}

	@Test
	void getsDuplicateRecordsWhenRequested() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L, true);
		given(answerFunctions.txnRecord(recordCache, view, sensibleQuery))
				.willReturn(Optional.of(cachedTargetRecord));
		given(recordCache.getDuplicateRecords(targetTxnId)).willReturn(List.of(cachedTargetRecord));

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(OK, opResponse.getHeader().getNodeTransactionPrecheckCode());
		assertEquals(cachedTargetRecord, opResponse.getTransactionRecord());
		assertEquals(List.of(cachedTargetRecord), opResponse.getDuplicateTransactionRecordsList());
	}

	@Test
	void recognizesUnavailableRecordFromMiss() throws Throwable {
		// setup:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);
		given(answerFunctions.txnRecord(recordCache, view, sensibleQuery))
				.willReturn(Optional.empty());

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, OK, 0L);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertTrue(opResponse.hasHeader(), "Missing response header!");
		assertEquals(RECORD_NOT_FOUND, opResponse.getHeader().getNodeTransactionPrecheckCode());
	}

	@Test
	void respectsMetaValidity() throws Throwable {
		// given:
		Query sensibleQuery = getRecordQuery(targetTxnId, ANSWER_ONLY, 5L);

		// when:
		Response response = subject.responseGiven(sensibleQuery, view, INVALID_TRANSACTION, 0L);

		// then:
		TransactionGetRecordResponse opResponse = response.getTransactionGetRecord();
		assertEquals(INVALID_TRANSACTION, opResponse.getHeader().getNodeTransactionPrecheckCode());
	}

	@Test
	void requiresAnswerOnlyPaymentButNotCostAnswer() throws Throwable {
		// expect:
		assertFalse(subject.requiresNodePayment(getRecordQuery(targetTxnId, COST_ANSWER, 0)));
		assertTrue(subject.requiresNodePayment(getRecordQuery(targetTxnId, ANSWER_ONLY, 0)));
	}

	@Test
	void requiresAnswerOnlyCostAsExpected() throws Throwable {
		// expect:
		assertTrue(subject.needsAnswerOnlyCost(getRecordQuery(targetTxnId, COST_ANSWER, 0)));
		assertFalse(subject.needsAnswerOnlyCost(getRecordQuery(targetTxnId, ANSWER_ONLY, 0)));
	}

	@Test
	void syntaxCheckPrioritizesAccountStatus() throws Throwable {
		// setup:
		Query query = getRecordQuery(targetTxnId, ANSWER_ONLY, 123L);

		given(optionValidator.queryableAccountStatus(targetTxnId.getAccountID(), accounts)).willReturn(ACCOUNT_DELETED);

		// when:
		ResponseCodeEnum validity = subject.checkValidity(query, view);

		// then:
		assertEquals(ACCOUNT_DELETED, validity);
	}

	@Test
	void syntaxCheckShortCircuitsOnDefaultAccountID() {
		// expect:
		assertEquals(INVALID_ACCOUNT_ID, subject.checkValidity(Query.getDefaultInstance(), view));
	}

	@Test
	void syntaxCheckOkForFindableRecord() throws Throwable {
		Query query = getRecordQuery(missingTxnId, ANSWER_ONLY, 123L);

		given(answerFunctions.txnRecord(recordCache, view, query)).willReturn(Optional.of(cachedTargetRecord));
		given(optionValidator.queryableAccountStatus(targetTxnId.getAccountID(), accounts)).willReturn(OK);

		// when:
		ResponseCodeEnum validity = subject.checkValidity(query, view);

		// then:
		assertEquals(OK, validity);
	}

	@Test
	void recognizesFunction() {
		// expect:
		assertEquals(HederaFunctionality.TransactionGetRecord, subject.canonicalFunction());
	}

	Query getRecordQuery(TransactionID txnId, ResponseType type, long payment, boolean duplicates) throws Throwable {
		this.paymentTxn = payerSponsoredTransfer(payer, COMPLEX_KEY_ACCOUNT_KT, node, payment);
		TransactionGetRecordQuery.Builder op = TransactionGetRecordQuery.newBuilder()
				.setHeader(QueryHeader.newBuilder()
						.setResponseType(type)
						.setPayment(paymentTxn))
				.setTransactionID(txnId)
				.setIncludeDuplicates(duplicates);
		return Query.newBuilder()
				.setTransactionGetRecord(op)
				.build();
	}

	 Query getRecordQuery(TransactionID txnId, ResponseType type, long payment) throws Throwable {
		return getRecordQuery(txnId, type, payment, false);
	}

	 ExpirableTxnRecord constructTargetRecord() {
		TransactionRecord record = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder().setStatus(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS))
				.setTransactionID(targetTxnId)
				.setMemo("Dim galleries, dusk winding stairs got past...")
				.setConsensusTimestamp(Timestamp.newBuilder().setSeconds(9_999_999_999L))
				.setTransactionFee(555L)
				.setTransferList(withAdjustments(
						asAccount("0.0.2"), -2L,
						asAccount("0.0.2"), -2L,
						asAccount("0.0.1001"), 2L,
						asAccount("0.0.1002"), 2L))
				.build();
		return fromGprc(record);
	}
}
