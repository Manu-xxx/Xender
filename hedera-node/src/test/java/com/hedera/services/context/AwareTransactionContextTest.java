package com.hedera.services.context;

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
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.charging.ItemizableFeeCharging;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.fcmap.FCMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static com.hedera.services.context.AwareTransactionContext.EMPTY_KEY;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountString;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asSchedule;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@ExtendWith(LogCaptureExtension.class)
class AwareTransactionContextTest {
	private final TransactionID scheduledTxnId = TransactionID.newBuilder()
			.setAccountID(IdUtils.asAccount("0.0.2"))
			.build();
	private long memberId = 3;
	private long anotherMemberId = 4;
	private Instant now = Instant.now();
	private Timestamp timeNow = Timestamp.newBuilder()
			.setSeconds(now.getEpochSecond())
			.setNanos(now.getNano())
			.build();
	private ExchangeRate rateNow = ExchangeRate.newBuilder().setHbarEquiv(1).setCentEquiv(100).build();
	private ExchangeRateSet ratesNow =
			ExchangeRateSet.newBuilder().setCurrentRate(rateNow).setNextRate(rateNow).build();
	private AccountID payer = asAccount("0.0.2");
	private AccountID node = asAccount("0.0.3");
	private AccountID anotherNodeAccount = asAccount("0.0.4");
	private AccountID funding = asAccount("0.0.98");
	private AccountID created = asAccount("1.0.2");
	private AccountID another = asAccount("1.0.300");
	private TransferList transfers = withAdjustments(payer, -2L, created, 1L, another, 1L);
	private TokenID tokenCreated = asToken("3.0.2");
	private ScheduleID scheduleCreated = asSchedule("0.0.10");
	private TokenTransferList tokenTransfers = TokenTransferList.newBuilder()
			.setToken(tokenCreated)
			.addAllTransfers(withAdjustments(payer, -2L, created, 1L, another, 1L).getAccountAmountsList())
			.build();
	private FileID fileCreated = asFile("2.0.1");
	private ContractID contractCreated = asContract("0.1.2");
	private TopicID topicCreated = asTopic("5.4.3");
	private long txnValidStart = now.getEpochSecond() - 1_234L;
	private HederaLedger ledger;
	private ItemizableFeeCharging itemizableFeeCharging;
	private AccountID nodeAccount = asAccount("0.0.3");
	private Address address;
	private Address anotherAddress;
	private AddressBook book;
	private HbarCentExchange exchange;
	private NodeInfo nodeInfo;
	private ServicesContext ctx;
	private PlatformTxnAccessor accessor;
	private Transaction signedTxn;
	private TransactionBody txn;
	private TransactionRecord record;
	private ExpiringEntity expiringEntity;
	private String memo = "Hi!";
	private ByteString hash = ByteString.copyFrom("fake hash".getBytes());
	private TransactionID txnId = TransactionID.newBuilder()
			.setTransactionValidStart(Timestamp.newBuilder().setSeconds(txnValidStart))
			.setAccountID(payer)
			.build();
	private ContractFunctionResult result = ContractFunctionResult.newBuilder().setContractID(contractCreated).build();
	private JKey payerKey;

	@Inject
	private LogCaptor logCaptor;

	@LoggingSubject
	private AwareTransactionContext subject;

	@BeforeEach
	private void setup() {
		address = mock(Address.class);
		given(address.getMemo()).willReturn(asAccountString(nodeAccount));
		anotherAddress = mock(Address.class);
		given(anotherAddress.getMemo()).willReturn(asAccountString(anotherNodeAccount));
		book = mock(AddressBook.class);
		given(book.getAddress(memberId)).willReturn(address);
		given(book.getAddress(anotherMemberId)).willReturn(anotherAddress);

		ledger = mock(HederaLedger.class);
		given(ledger.netTransfersInTxn()).willReturn(transfers);
		given(ledger.netTokenTransfersInTxn()).willReturn(List.of(tokenTransfers));

		exchange = mock(HbarCentExchange.class);
		given(exchange.activeRates()).willReturn(ratesNow);

		itemizableFeeCharging = mock(ItemizableFeeCharging.class);
		given(itemizableFeeCharging.itemizedFees()).willReturn(TransferList.getDefaultInstance());

		payerKey = mock(JKey.class);
		MerkleAccount payerAccount = mock(MerkleAccount.class);
		given(payerAccount.getKey()).willReturn(payerKey);
		FCMap<MerkleEntityId, MerkleAccount> accounts = mock(FCMap.class);
		given(accounts.get(MerkleEntityId.fromAccountId(payer))).willReturn(payerAccount);

		ctx = mock(ServicesContext.class);
		given(ctx.exchange()).willReturn(exchange);
		given(ctx.ledger()).willReturn(ledger);
		given(ctx.accounts()).willReturn(accounts);
		given(ctx.charging()).willReturn(itemizableFeeCharging);
		given(ctx.accounts()).willReturn(accounts);
		given(ctx.addressBook()).willReturn(book);

		nodeInfo = mock(NodeInfo.class);
		given(ctx.nodeInfo()).willReturn(nodeInfo);
		given(nodeInfo.accountOf(memberId)).willReturn(nodeAccount);

		txn = mock(TransactionBody.class);
		given(txn.getMemo()).willReturn(memo);
		signedTxn = mock(Transaction.class);
		given(signedTxn.toByteArray()).willReturn(memo.getBytes());
		accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getTxnId()).willReturn(txnId);
		given(accessor.getTxn()).willReturn(txn);
		given(accessor.getBackwardCompatibleSignedTxn()).willReturn(signedTxn);
		given(accessor.getPayer()).willReturn(payer);
		given(accessor.getHash()).willReturn(hash);

		expiringEntity = mock(ExpiringEntity.class);

		subject = new AwareTransactionContext(ctx);
		subject.resetFor(accessor, now, memberId);
	}

	@Test
	void throwsOnUpdateIfNoRecordSoFar() {
		// expect:
		assertThrows(
				IllegalStateException.class,
				() -> subject.updatedRecordGiven(withAdjustments(payer, -100, funding, 50, another, 50)));
	}

	@Test
	void updatesAsExpectedIfRecordSoFar() {
		// setup:
		subject.recordSoFar = mock(TransactionRecord.Builder.class);
		subject.hasComputedRecordSoFar = true;
		// and:
		var expected = mock(TransactionRecord.class);

		// given:
		given(itemizableFeeCharging.totalFeesChargedToPayer()).willReturn(123L);
		var xfers = withAdjustments(payer, -100, funding, 50, another, 50);
		// and:
		given(subject.recordSoFar.build()).willReturn(expected);
		given(subject.recordSoFar.setTransferList(xfers)).willReturn(subject.recordSoFar);

		// when:
		var actual = subject.updatedRecordGiven(xfers);

		// then:
		verify(subject.recordSoFar).setTransferList(xfers);
		verify(subject.recordSoFar).setTransactionFee(123L);
		// and:
		assertSame(expected, actual);
	}

	@Test
	void canOverrideTokenTransfers() {
		// given:
		final var someTokenXfers = List.of(TokenTransferList.newBuilder()
				.setToken(IdUtils.asToken("1.2.3"))
				.addAllTransfers(
						withAdjustments(payer, -100, node, 10, funding, 90).getAccountAmountsList())
				.build());

		// when:
		subject.setTokenTransferLists(someTokenXfers);
		// and:
		var record = subject.recordSoFar();

		// then:
		assertEquals(someTokenXfers, record.getTokenTransferListsList());
	}

	@Test
	void throwsIseIfNoPayerActive() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.activePayer());
	}

	@Test
	void returnsPayerIfSigActive() {
		// given:
		subject.payerSigIsKnownActive();

		// expect:
		assertEquals(payer, subject.activePayer());
	}

	@Test
	void returnsEmptyKeyIfNoPayerActive() {
		// expect:
		assertEquals(EMPTY_KEY, subject.activePayerKey());
	}

	@Test
	void getsPayerKeyIfSigActive() {
		// given:
		subject.payerSigIsKnownActive();

		// then:
		assertEquals(payerKey, subject.activePayerKey());
	}

	@Test
	void getsExpectedNodeAccount() {
		// expect:
		assertEquals(nodeAccount, subject.submittingNodeAccount());
	}

	@Test
	void failsHardForMissingMemberAccount() {
		given(nodeInfo.accountOf(memberId)).willThrow(IllegalArgumentException.class);

		// then:
		var ise = assertThrows(IllegalStateException.class, () -> subject.submittingNodeAccount());
		// and:
		assertThat(logCaptor.warnLogs(), contains(Matchers.startsWith("No available Hedera account for member 3!")));
		assertEquals("Member 3 must have a Hedera account!", ise.getMessage());
	}

	@Test
	void resetsRecordSoFar() {
		// given:
		subject.recordSoFar = mock(TransactionRecord.Builder.class);

		// when:
		subject.resetFor(accessor, now, anotherMemberId);

		// then:
		verify(subject.recordSoFar).clear();
	}

	@Test
	void resetsEverythingElse() {
		given(nodeInfo.accountOf(anotherMemberId)).willReturn(anotherNodeAccount);
		// and:
		subject.addNonThresholdFeeChargedToPayer(1_234L);
		subject.setCallResult(result);
		subject.setStatus(ResponseCodeEnum.SUCCESS);
		subject.setCreated(contractCreated);
		subject.payerSigIsKnownActive();
		subject.hasComputedRecordSoFar = true;
		// and:
		assertEquals(memberId, subject.submittingSwirldsMember());
		assertEquals(nodeAccount, subject.submittingNodeAccount());

		// when:
		subject.resetFor(accessor, now, anotherMemberId);
		assertFalse(subject.hasComputedRecordSoFar);
		// and:
		record = subject.recordSoFar();

		// then:
		assertEquals(ResponseCodeEnum.UNKNOWN, record.getReceipt().getStatus());
		assertFalse(record.getReceipt().hasContractID());
		assertEquals(0, record.getTransactionFee());
		assertFalse(record.hasContractCallResult());
		assertFalse(subject.isPayerSigKnownActive());
		assertTrue(subject.hasComputedRecordSoFar);
		assertEquals(anotherNodeAccount, subject.submittingNodeAccount());
		assertEquals(anotherMemberId, subject.submittingSwirldsMember());
		// and:
		verify(itemizableFeeCharging).resetFor(accessor, anotherNodeAccount);
	}

	@Test
	void effectivePayerIsSubmittingNodeIfNotVerified() {
		// expect:
		assertEquals(nodeAccount, subject.effectivePayer());
	}

	@Test
	void effectivePayerIsActiveIfVerified() {
		// given:
		subject.payerSigIsKnownActive();

		// expect:
		assertEquals(payer, subject.effectivePayer());
	}

	@Test
	void getsItemizedRepr() {
		// setup:
		TransferList canonicalAdjustments =
				withAdjustments(payer, -2100, node, 100, funding, 1000, another, 1000);
		TransferList itemizedFees =
				withAdjustments(funding, 900, payer, -900, node, 100, payer, -100);
		// and:
		TransferList desiredRepr = itemizedFees.toBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(payer).setAmount(-1100))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(funding).setAmount(100))
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(another).setAmount(1000))
				.build();

		given(ledger.netTransfersInTxn()).willReturn(canonicalAdjustments);
		given(itemizableFeeCharging.itemizedFees()).willReturn(itemizedFees);

		// when:
		TransferList repr = subject.itemizedRepresentation();

		// then:
		assertEquals(desiredRepr, repr);
	}

	@Test
	void usesChargingToSetTransactionFee() {
		long std = 1_234L;
		long other = 4_321L;
		given(itemizableFeeCharging.totalFeesChargedToPayer()).willReturn(std);

		// when:
		subject.addNonThresholdFeeChargedToPayer(other);
		record = subject.recordSoFar();

		// then:
		assertEquals(std + other, record.getTransactionFee());
	}

	@Test
	void usesTokenTransfersToSetApropos() {
		// when:
		record = subject.recordSoFar();

		// then:
		assertEquals(tokenTransfers, record.getTokenTransferLists(0));
	}

	@Test
	void configuresCallResult() {
		// when:
		subject.setCallResult(result);
		record = subject.recordSoFar();

		// expect:
		assertEquals(result, record.getContractCallResult());
	}

	@Test
	void configuresCreateResult() {
		// when:
		subject.setCreateResult(result);
		record = subject.recordSoFar();

		// expect:
		assertEquals(result, record.getContractCreateResult());
	}

	@Test
	void hasTransferList() {
		// expect:
		assertEquals(transfers, subject.recordSoFar().getTransferList());
	}

	@Test
	void hasExpectedCopyFields() {
		// when:
		TransactionRecord record = subject.recordSoFar();

		// expect:
		assertEquals(memo, record.getMemo());
		assertEquals(hash, record.getTransactionHash());
		assertEquals(txnId, record.getTransactionID());
		assertEquals(timeNow, record.getConsensusTimestamp());
	}

	@Test
	void hasExpectedPrimitives() {
		// expect:
		assertEquals(accessor, subject.accessor());
		assertEquals(now, subject.consensusTime());
		assertEquals(ResponseCodeEnum.UNKNOWN, subject.status());
	}

	@Test
	void hasExpectedStatus() {
		// when:
		subject.setStatus(ResponseCodeEnum.INVALID_PAYER_SIGNATURE);

		// then:
		assertEquals(ResponseCodeEnum.INVALID_PAYER_SIGNATURE, subject.status());
	}

	@Test
	void hasExpectedRecordStatus() {
		// when:
		subject.setStatus(ResponseCodeEnum.INVALID_PAYER_SIGNATURE);
		record = subject.recordSoFar();

		// then:
		assertEquals(ResponseCodeEnum.INVALID_PAYER_SIGNATURE, record.getReceipt().getStatus());
	}

	@Test
	void getsExpectedReceiptForAccountCreation() {
		// when:
		subject.setCreated(created);
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().getExchangeRate());
		assertEquals(created, record.getReceipt().getAccountID());
	}

	@Test
	void getsExpectedReceiptForTokenCreation() {
		// when:
		subject.setCreated(tokenCreated);
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().getExchangeRate());
		assertEquals(tokenCreated, record.getReceipt().getTokenID());
	}

	@Test
	void getsExpectedReceiptForTokenMintBurnWipe() {
		// when:
		final var newTotalSupply = 1000L;
		subject.setNewTotalSupply(newTotalSupply);
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().getExchangeRate());
		assertEquals(newTotalSupply, record.getReceipt().getNewTotalSupply());
	}


	@Test
	void getsExpectedReceiptForFileCreation() {
		// when:
		subject.setCreated(fileCreated);
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().getExchangeRate());
		assertEquals(fileCreated, record.getReceipt().getFileID());
	}

	@Test
	void getsExpectedReceiptForContractCreation() {
		// when:
		subject.setCreated(contractCreated);
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().getExchangeRate());
		assertEquals(contractCreated, record.getReceipt().getContractID());
	}

	@Test
	void getsExpectedReceiptForTopicCreation() {
		// when:
		subject.setCreated(topicCreated);
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().getExchangeRate());
		assertEquals(topicCreated, record.getReceipt().getTopicID());
	}

	@Test
	void getsExpectedReceiptForSubmitMessage() {
		var sequenceNumber = 1000L;
		var runningHash = new byte[11];

		// when:
		subject.setTopicRunningHash(runningHash, sequenceNumber);
		record = subject.recordSoFar();

		// then:
		assertEquals(ratesNow, record.getReceipt().getExchangeRate());
		assertArrayEquals(runningHash, record.getReceipt().getTopicRunningHash().toByteArray());
		assertEquals(sequenceNumber, record.getReceipt().getTopicSequenceNumber());
		assertEquals(MerkleTopic.RUNNING_HASH_VERSION, record.getReceipt().getTopicRunningHashVersion());
	}

	@Test
	void getsExpectedReceiptForSuccessfulScheduleOps() {
		// when:
		subject.setCreated(scheduleCreated);
		subject.setScheduledTxnId(scheduledTxnId);
		// and:
		record = subject.recordSoFar();

		// then:
		assertEquals(scheduleCreated, record.getReceipt().getScheduleID());
		assertEquals(scheduledTxnId, record.getReceipt().getScheduledTransactionID());
	}

	@Test
	void startsWithoutKnownValidPayerSig() {
		// expect:
		assertFalse(subject.isPayerSigKnownActive());
	}

	@Test
	void setsSigToKnownValid() {
		// given:
		subject.payerSigIsKnownActive();

		// expect:
		assertTrue(subject.isPayerSigKnownActive());
	}

	@Test
	void triggersTxn() {
		// when:
		subject.trigger(accessor);
		// then:
		assertEquals(subject.triggeredTxn(), accessor);
	}

	@Test
	void getsExpectedRecordForTriggeredTxn() {
		// given:
		given(accessor.getScheduleRef()).willReturn(scheduleCreated);
		given(accessor.isTriggeredTxn()).willReturn(true);

		// when:
		record = subject.recordSoFar();

		// then:
		assertEquals(scheduleCreated, record.getScheduleRef());
	}

	@Test
	void addsExpiringEntities() {
		// given:
		var expected = Collections.singletonList(expiringEntity);
		// when:
		subject.addExpiringEntities(expected);

		// then:
		assertEquals(subject.expiringEntities(), expected);
	}

	@Test
	void throwsIfAccessorIsAlreadyTriggered() {
		// given:
		given(accessor.getScheduleRef()).willReturn(scheduleCreated);
		given(accessor.isTriggeredTxn()).willReturn(true);

		// when:
		assertThrows(IllegalStateException.class, () -> subject.trigger(accessor));
	}
}
