package com.hedera.services.txns.consensus;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.AccountStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.ALIASED_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISSING_ACCOUNT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISSING_TOPIC;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class MerkleTopicUpdateTransitionLogicTest {
	final private Key updatedAdminKey = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
	final private Key updatedSubmitKey = MISC_ACCOUNT_KT.asKey();
	final private Key existingKey = MISC_ACCOUNT_KT.asKey();

	private static final long VALID_AUTORENEW_PERIOD_SECONDS = 30 * 86400L;
	private static final long INVALID_AUTORENEW_PERIOD_SECONDS = -1L;
	private static final long EXISTING_AUTORENEW_PERIOD_SECONDS = 29 * 86400L;
	private static final long NOW_SECONDS = 1546304462;
	private static final RichInstant EXISTING_EXPIRATION_TIME = new RichInstant(NOW_SECONDS + 1000L, 0);
	private static final String TOO_LONG_MEMO = "too-long";
	private static final String VALID_MEMO = "updated memo";
	private static final String EXISTING_MEMO = "unmodified memo";
	private static final TopicID TOPIC_ID = asTopic("9.8.7");

	private Instant consensusTime;
	private Instant updatedExpirationTime;
	private TransactionBody transactionBody;
	private TransactionContext transactionContext;
	private HederaLedger ledger;
	private PlatformTxnAccessor accessor;
	private SigImpactHistorian sigImpactHistorian;
	private OptionValidator validator;
	private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
	private MerkleMap<EntityNum, MerkleTopic> topics = new MerkleMap<>();
	private TopicUpdateTransitionLogic subject;
	private AccountStore accountStore;
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();

	@BeforeEach
	private void setup() {
		consensusTime = Instant.ofEpochSecond(NOW_SECONDS);
		updatedExpirationTime = Instant.ofEpochSecond(EXISTING_EXPIRATION_TIME.getSeconds()).plusSeconds(1000);

		transactionContext = mock(TransactionContext.class);
		given(transactionContext.consensusTime()).willReturn(consensusTime);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		given(validator.isValidAutoRenewPeriod(Duration.newBuilder().setSeconds(VALID_AUTORENEW_PERIOD_SECONDS).build()))
				.willReturn(true);
		given(validator.isValidAutoRenewPeriod(
				Duration.newBuilder().setSeconds(INVALID_AUTORENEW_PERIOD_SECONDS).build()))
				.willReturn(false);
		given(validator.memoCheck("")).willReturn(OK);
		given(validator.memoCheck(VALID_MEMO)).willReturn(OK);
		given(validator.memoCheck(TOO_LONG_MEMO)).willReturn(MEMO_TOO_LONG);
		sigImpactHistorian = mock(SigImpactHistorian.class);
		accountStore = mock(AccountStore.class);

		ledger = mock(HederaLedger.class);
		subject = new TopicUpdateTransitionLogic(
				() -> accounts, () -> topics, validator, transactionContext, ledger, sigImpactHistorian);
	}

	@Test
	void hasCorrectApplicability() {
		// given:
		givenValidTransactionWithAllOptions();

		// expect:
		assertTrue(subject.applicability().test(transactionBody));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void syntaxCheckWithAdminKey() {
		// given:
		givenValidTransactionWithAllOptions();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(transactionBody));
	}

	@Test
	void syntaxCheckWithInvalidAdminKey() {
		// given:
		givenValidTransactionWithAllOptions();
		given(validator.hasGoodEncoding(updatedAdminKey)).willReturn(false);

		// expect:
		assertEquals(BAD_ENCODING, subject.semanticCheck().apply(transactionBody));
	}

	@Test
	void syntaxCheckWithValidAliasedAutoRenewAccount() throws Throwable {
		givenExistingTopicWithAdminKey();
		givenValidTransactionWithAliasedAutoRenewAccount();

		subject.doStateTransition();

		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		assertNotNull(topic);
		verify(transactionContext).setStatus(SUCCESS);
		assertEquals(VALID_MEMO, topic.getMemo());
		assertArrayEquals(JKey.mapKey(updatedAdminKey).serialize(), topic.getAdminKey().serialize());
		assertArrayEquals(JKey.mapKey(updatedSubmitKey).serialize(), topic.getSubmitKey().serialize());
		assertEquals(VALID_AUTORENEW_PERIOD_SECONDS, topic.getAutoRenewDurationSeconds());
		assertEquals(EntityId.fromGrpcAccountId(MISC_ACCOUNT), topic.getAutoRenewAccountId());
		assertEquals(updatedExpirationTime.getEpochSecond(), topic.getExpirationTimestamp().getSeconds());
		verify(sigImpactHistorian).markEntityChanged(TOPIC_ID.getTopicNum());
	}

	@Test
	void followsHappyPath() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenValidTransactionWithAllOptions();

		// when:
		subject.doStateTransition();

		// then:
		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		assertNotNull(topic);
		verify(transactionContext).setStatus(SUCCESS);
		assertEquals(VALID_MEMO, topic.getMemo());
		assertArrayEquals(JKey.mapKey(updatedAdminKey).serialize(), topic.getAdminKey().serialize());
		assertArrayEquals(JKey.mapKey(updatedSubmitKey).serialize(), topic.getSubmitKey().serialize());
		assertEquals(VALID_AUTORENEW_PERIOD_SECONDS, topic.getAutoRenewDurationSeconds());
		assertEquals(EntityId.fromGrpcAccountId(MISC_ACCOUNT), topic.getAutoRenewAccountId());
		assertEquals(updatedExpirationTime.getEpochSecond(), topic.getExpirationTimestamp().getSeconds());
		verify(sigImpactHistorian).markEntityChanged(TOPIC_ID.getTopicNum());
	}

	@Test
	void clearsKeysIfRequested() throws Throwable {
		// given:
		givenExistingTopicWithBothKeys();
		givenTransactionClearingKeys();

		// when:
		subject.doStateTransition();

		// then:
		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		assertNotNull(topic);
		verify(transactionContext).setStatus(SUCCESS);
		assertEquals(EXISTING_MEMO, topic.getMemo());
		assertFalse(topic.hasAdminKey());
		assertFalse(topic.hasSubmitKey());
		assertEquals(EXISTING_AUTORENEW_PERIOD_SECONDS, topic.getAutoRenewDurationSeconds());
		assertFalse(topic.hasAutoRenewAccountId());
		assertEquals(EXISTING_EXPIRATION_TIME, topic.getExpirationTimestamp());
	}

	@Test
	void failsOnInvalidMemo() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidMemo();

		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(MEMO_TOO_LONG);
	}

	@Test
	void failsOnInvalidAdminKey() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidAdminKey();

		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(BAD_ENCODING);
	}

	@Test
	void failsOnInvalidSubmitKey() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidSubmitKey();

		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(BAD_ENCODING);
	}

	@Test
	void failsOnInvalidAutoRenewPeriod() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidAutoRenewPeriod();

		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(AUTORENEW_DURATION_NOT_IN_RANGE);
	}

	@Test
	void failsOnInvalidExpirationTime() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidExpirationTime();

		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(INVALID_EXPIRATION_TIME);
	}

	@Test
	void failsOnExpirationTimeReduction() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithReducedExpirationTime();

		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(EXPIRATION_REDUCTION_NOT_ALLOWED);
	}

	@Test
	void failsUnauthorizedOnMemoChange() throws Throwable {
		// given:
		givenExistingTopicWithoutAdminKey();
		givenTransactionWithMemo();

		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		var originalValues = new MerkleTopic(topic);

		// when:
		subject.doStateTransition();

		// then:
		assertTopicNotUpdated(topic, originalValues);
		verify(transactionContext).setStatus(UNAUTHORIZED);
	}

	@Test
	void failsOnInvalidTopic() {
		// given:
		givenValidTransactionInvalidTopic();

		// when:
		subject.doStateTransition();

		// then:
		verify(transactionContext).setStatus(INVALID_TOPIC_ID);
	}

	@Test
	void failsOnInvalidAutoRenewAccount() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithInvalidAutoRenewAccount();

		// when:
		subject.doStateTransition();

		// then:
		verify(transactionContext).setStatus(INVALID_AUTORENEW_ACCOUNT);
	}

	@Test
	void failsOnDetachedExistingAutoRenewAccount() throws Throwable {
		// given:
		givenExistingTopicWithAutoRenewAccount();
		givenValidTransactionWithAllOptions();
		given(ledger.isDetached(MISC_ACCOUNT)).willReturn(true);

		// when:
		subject.doStateTransition();

		// then:
		verify(transactionContext).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void failsOnDetachedNewAutoRenewAccount() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithAutoRenewAccountNotClearingAdminKey();
		given(ledger.isDetached(MISC_ACCOUNT)).willReturn(true);

		// when:
		subject.doStateTransition();

		// then:
		verify(transactionContext).setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
	}

	@Test
	void failsOnAutoRenewAccountNotAllowed() throws Throwable {
		// given:
		givenExistingTopicWithAdminKey();
		givenTransactionWithAutoRenewAccountClearingAdminKey();

		// when:
		subject.doStateTransition();

		// then:
		verify(transactionContext).setStatus(AUTORENEW_ACCOUNT_NOT_ALLOWED);
	}

	@Test
	void clearsAutoRenewAccountIfCorrectSentinelUsed() throws Throwable {
		// given:
		givenExistingTopicWithAutoRenewAccount();
		givenTransactionClearingAutoRenewAccount();

		// when:
		subject.doStateTransition();

		// then:
		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		verify(transactionContext).setStatus(SUCCESS);
		assertFalse(topic.hasAutoRenewAccountId());
	}

	@Test
	void doesntClearAutoRenewAccountIfSentinelWithAliasUsed() throws Throwable {
		// given:
		givenExistingTopicWithAutoRenewAccount();
		givenTransactionChangingAutoRenewAccountWithAliasId();

		// when:
		subject.doStateTransition();

		// then:
		var topic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		verify(transactionContext).setStatus(INVALID_AUTORENEW_ACCOUNT);
		assertTrue(topic.hasAutoRenewAccountId());
	}

	private void assertTopicNotUpdated(MerkleTopic originalMerkleTopic, MerkleTopic originalMerkleTopicClone) {
		var updatedTopic = topics.get(EntityNum.fromTopicId(TOPIC_ID));
		assertSame(originalMerkleTopic, updatedTopic); // No change
		assertEquals(originalMerkleTopicClone, updatedTopic); // No change in values
	}

	private void givenExistingTopicWithAdminKey() throws Throwable {
		var existingTopic = new MerkleTopic(EXISTING_MEMO, JKey.mapKey(existingKey), null,
				EXISTING_AUTORENEW_PERIOD_SECONDS, null,
				EXISTING_EXPIRATION_TIME);
		topics.put(EntityNum.fromTopicId(TOPIC_ID), existingTopic);
		given(validator.queryableTopicStatus(TOPIC_ID, topics)).willReturn(OK);
	}

	private void givenExistingTopicWithBothKeys() throws Throwable {
		var existingTopic = new MerkleTopic(EXISTING_MEMO, JKey.mapKey(existingKey), JKey.mapKey(existingKey),
				EXISTING_AUTORENEW_PERIOD_SECONDS, null, EXISTING_EXPIRATION_TIME);
		topics.put(EntityNum.fromTopicId(TOPIC_ID), existingTopic);
		given(validator.queryableTopicStatus(TOPIC_ID, topics)).willReturn(OK);
	}

	private void givenExistingTopicWithoutAdminKey() throws Throwable {
		var existingTopic = new MerkleTopic(EXISTING_MEMO, null, null, EXISTING_AUTORENEW_PERIOD_SECONDS, null,
				EXISTING_EXPIRATION_TIME);
		topics.put(EntityNum.fromTopicId(TOPIC_ID), existingTopic);
		given(validator.queryableTopicStatus(TOPIC_ID, topics)).willReturn(OK);
	}

	private void givenExistingTopicWithAutoRenewAccount() throws Throwable {
		var existingTopic = new MerkleTopic(EXISTING_MEMO, JKey.mapKey(existingKey), null,
				EXISTING_AUTORENEW_PERIOD_SECONDS,
				EntityId.fromGrpcAccountId(MISC_ACCOUNT), EXISTING_EXPIRATION_TIME);
		topics.put(EntityNum.fromTopicId(TOPIC_ID), existingTopic);
		given(validator.queryableTopicStatus(TOPIC_ID, topics)).willReturn(OK);
	}

	private void givenTransaction(ConsensusUpdateTopicTransactionBody.Builder body) {
		transactionBody = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setConsensusUpdateTopic(body.build())
				.build();
		given(accessor.getTxn()).willReturn(transactionBody);
		given(transactionContext.accessor()).willReturn(accessor);
	}

	private ConsensusUpdateTopicTransactionBody.Builder getBasicValidTransactionBodyBuilder() {
		return ConsensusUpdateTopicTransactionBody.newBuilder()
				.setTopicID(TOPIC_ID);
	}

	private void givenValidTransactionInvalidTopic() {
		givenTransaction(
				ConsensusUpdateTopicTransactionBody.newBuilder()
						.setTopicID(MISSING_TOPIC)
						.setMemo(StringValue.of(VALID_MEMO))
		);
		given(validator.queryableTopicStatus(MISSING_TOPIC, topics)).willReturn(INVALID_TOPIC_ID);
	}

	private void givenTransactionWithInvalidAutoRenewAccount() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewAccount(MISSING_ACCOUNT)
		);
		given(ledger.lookUpAccountId(MISSING_ACCOUNT, INVALID_AUTORENEW_ACCOUNT))
				.willReturn(Pair.of(MISSING_ACCOUNT, INVALID_AUTORENEW_ACCOUNT));

	}

	private void givenTransactionWithAutoRenewAccountClearingAdminKey() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAdminKey(Key.newBuilder().setKeyList(KeyList.getDefaultInstance()))
						.setAutoRenewAccount(MISC_ACCOUNT)
		);
		given(validator.queryableAccountStatus(MISC_ACCOUNT, accounts)).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(ledger.lookUpAccountId(MISC_ACCOUNT, INVALID_AUTORENEW_ACCOUNT)).willReturn(Pair.of(MISC_ACCOUNT, OK));
	}

	private void givenTransactionWithAutoRenewAccountNotClearingAdminKey() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewAccount(MISC_ACCOUNT)
		);
		given(validator.queryableAccountStatus(MISC_ACCOUNT, accounts)).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
		given(ledger.lookUpAccountId(MISC_ACCOUNT, INVALID_AUTORENEW_ACCOUNT)).willReturn(Pair.of(MISC_ACCOUNT, OK));
	}

	private void givenTransactionClearingAutoRenewAccount() {
		final var account = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(0).build();
		givenTransaction(
				getBasicValidTransactionBodyBuilder().setAutoRenewAccount(account)
		);
		given(ledger.lookUpAccountId(account, INVALID_AUTORENEW_ACCOUNT)).willReturn(Pair.of(account, OK));
	}

	private void givenTransactionChangingAutoRenewAccountWithAliasId() {
		final var account = AccountID.newBuilder().setAlias(ByteString.copyFromUtf8("pretend")).build();
		givenTransaction(
				getBasicValidTransactionBodyBuilder().setAutoRenewAccount(account)
		);
		given(ledger.lookUpAccountId(account, INVALID_AUTORENEW_ACCOUNT)).willReturn(Pair.of(MISC_ACCOUNT, OK));
	}

	private void givenTransactionClearingKeys() {
		var clearKey = Key.newBuilder().setKeyList(KeyList.getDefaultInstance());
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAdminKey(clearKey)
						.setSubmitKey(clearKey)
		);
		given(validator.queryableAccountStatus(MISC_ACCOUNT, accounts)).willReturn(OK);
		given(validator.hasGoodEncoding(any())).willReturn(true);
	}

	private void givenValidTransactionWithAliasedAutoRenewAccount() {
		givenValidTransactionWithAllOptions(ALIASED_ACCOUNT);
		given(ledger.lookUpAccountId(ALIASED_ACCOUNT, INVALID_AUTORENEW_ACCOUNT)).willReturn(Pair.of(MISC_ACCOUNT, OK));
	}

	private void givenValidTransactionWithAllOptions() {
		givenValidTransactionWithAllOptions(MISC_ACCOUNT);
	}

	private void givenValidTransactionWithAllOptions(AccountID autoRenewAccount) {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setMemo(StringValue.of(VALID_MEMO))
						.setAdminKey(updatedAdminKey)
						.setSubmitKey(updatedSubmitKey)
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(VALID_AUTORENEW_PERIOD_SECONDS).build())
						.setAutoRenewAccount(autoRenewAccount)
						.setExpirationTime(Timestamp.newBuilder().setSeconds(updatedExpirationTime.getEpochSecond()))
		);
		given(validator.hasGoodEncoding(updatedAdminKey)).willReturn(true);
		given(validator.hasGoodEncoding(updatedSubmitKey)).willReturn(true);
		given(validator.isValidExpiry(any())).willReturn(true);
		given(validator.queryableAccountStatus(MISC_ACCOUNT, accounts)).willReturn(OK);
		given(ledger.lookUpAccountId(autoRenewAccount, INVALID_AUTORENEW_ACCOUNT)).willReturn(
				Pair.of(autoRenewAccount, OK));
	}

	private void givenTransactionWithInvalidMemo() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setMemo(StringValue.of(TOO_LONG_MEMO))
		);
	}

	private void givenTransactionWithMemo() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setMemo(StringValue.of(VALID_MEMO))
		);
	}

	private void givenTransactionWithInvalidAdminKey() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAdminKey(updatedAdminKey)
		);
		given(validator.hasGoodEncoding(updatedAdminKey)).willReturn(false);
	}

	private void givenTransactionWithInvalidSubmitKey() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setSubmitKey(updatedSubmitKey)
		);
		given(validator.hasGoodEncoding(updatedSubmitKey)).willReturn(false);
	}

	private void givenTransactionWithInvalidAutoRenewPeriod() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setAutoRenewPeriod(Duration.newBuilder().setSeconds(INVALID_AUTORENEW_PERIOD_SECONDS))
		);
	}

	private void givenTransactionWithInvalidExpirationTime() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setExpirationTime(Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond() - 1L))
		);
		given(validator.isValidExpiry(any())).willReturn(false);
	}

	private void givenTransactionWithReducedExpirationTime() {
		givenTransaction(
				getBasicValidTransactionBodyBuilder()
						.setExpirationTime(Timestamp.newBuilder().setSeconds(EXISTING_EXPIRATION_TIME.getSeconds() - 1L))
		);
		given(validator.isValidExpiry(any())).willReturn(true);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}
}
