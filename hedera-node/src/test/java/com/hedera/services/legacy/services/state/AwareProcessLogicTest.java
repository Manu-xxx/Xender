package com.hedera.services.legacy.services.state;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.charging.TxnFeeChargingPolicy;
import com.hedera.services.files.HederaFs;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.legacy.services.stats.HederaNodeStats;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.records.TxnIdRecentHistory;
import com.hedera.services.security.ops.SystemOpAuthorization;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.context.domain.trackers.IssEventStatus.NO_KNOWN_ISS;
import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class AwareProcessLogicTest {
	Logger mockLog;
	Transaction platformTxn;
	AddressBook book;
	ServicesContext ctx;
	TransactionContext txnCtx;
	TransactionBody txnBody;
	SmartContractRequestHandler contracts;
	HederaFs hfs;

	AwareProcessLogic subject;

	@BeforeEach
	public void setup() {
		final Transaction txn = mock(Transaction.class);
		final PlatformTxnAccessor txnAccessor = mock(PlatformTxnAccessor.class);
		final HederaLedger ledger = mock(HederaLedger.class);
		final AccountRecordsHistorian historian = mock(AccountRecordsHistorian.class);
		final HederaSigningOrder keyOrder = mock(HederaSigningOrder.class);
		final SigningOrderResult orderResult = mock(SigningOrderResult.class);
		final HederaNodeStats stats = mock(HederaNodeStats.class);
		final FeeCalculator fees = mock(FeeCalculator.class);
		final TxnIdRecentHistory recentHistory = mock(TxnIdRecentHistory.class);
		final Map<TransactionID, TxnIdRecentHistory> histories = mock(Map.class);
		final BackingAccounts<AccountID, MerkleAccount> backingAccounts = mock(BackingAccounts.class);
		final AccountID accountID = mock(AccountID.class);
		final OptionValidator validator = mock(OptionValidator.class);
		final TxnFeeChargingPolicy policy = mock(TxnFeeChargingPolicy.class);
		final SystemOpPolicies policies = mock(SystemOpPolicies.class);
		final TransitionLogicLookup lookup = mock(TransitionLogicLookup.class);
		hfs = mock(HederaFs.class);

		given(histories.get(any())).willReturn(recentHistory);

		txnCtx = mock(TransactionContext.class);
		ctx = mock(ServicesContext.class);
		txnBody = mock(TransactionBody.class);
		contracts = mock(SmartContractRequestHandler.class);
		mockLog = mock(Logger.class);
		TransactionBody txnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
								.setAccountID(IdUtils.asAccount("0.0.2"))).build();
		platformTxn = new Transaction(com.hederahashgraph.api.proto.java.Transaction.newBuilder()
				.setBodyBytes(txnBody.toByteString())
				.build().toByteArray());

		AwareProcessLogic.log = mockLog;

		var zeroStakeAddress = mock(Address.class);
		given(zeroStakeAddress.getStake()).willReturn(0L);
		var stakedAddress = mock(Address.class);
		given(stakedAddress.getStake()).willReturn(1L);
		book = mock(AddressBook.class);
		given(book.getAddress(1)).willReturn(stakedAddress);
		given(book.getAddress(666L)).willReturn(zeroStakeAddress);
		given(ctx.addressBook()).willReturn(book);
		given(ctx.ledger()).willReturn(ledger);
		given(ctx.txnCtx()).willReturn(txnCtx);
		given(ctx.recordsHistorian()).willReturn(historian);
		given(ctx.backedKeyOrder()).willReturn(keyOrder);
		given(ctx.stats()).willReturn(stats);
		given(ctx.fees()).willReturn(fees);
		given(ctx.txnHistories()).willReturn(histories);
		given(ctx.backingAccounts()).willReturn(backingAccounts);
		given(ctx.validator()).willReturn(validator);
		given(ctx.txnChargingPolicy()).willReturn(policy);
		given(ctx.systemOpPolicies()).willReturn(policies);
		given(ctx.transitionLogic()).willReturn(lookup);
		given(ctx.hfs()).willReturn(hfs);
		given(ctx.contracts()).willReturn(contracts);

		given(txnCtx.accessor()).willReturn(txnAccessor);
		given(txnCtx.submittingNodeAccount()).willReturn(accountID);
		given(txnCtx.isPayerSigKnownActive()).willReturn(true);
		given(txnAccessor.getPlatformTxn()).willReturn(txn);

		given(txn.getSignatures()).willReturn(Collections.emptyList());
		given(keyOrder.keysForPayer(any(), any())).willReturn(orderResult);
		given(keyOrder.keysForOtherParties(any(), any())).willReturn(orderResult);

		final com.hederahashgraph.api.proto.java.Transaction signedTxn = mock(com.hederahashgraph.api.proto.java.Transaction.class);
		final TransactionID txnId = mock(TransactionID.class);

		given(txnAccessor.getSignedTxn()).willReturn(signedTxn);
		given(txnAccessor.getTxn()).willReturn(txnBody);
		given(txnBody.getTransactionID()).willReturn(txnId);
		given(txnBody.getTransactionValidDuration()).willReturn(Duration.getDefaultInstance());

		given(recentHistory.currentDuplicityFor(anyLong())).willReturn(BELIEVED_UNIQUE);
		given(backingAccounts.contains(any())).willReturn(true);

		given(validator.isValidTxnDuration(anyLong())).willReturn(true);
		given(validator.chronologyStatus(any(), any())).willReturn(ResponseCodeEnum.OK);
		given(validator.isValidAutoRenewPeriod(any())).willReturn(true);

		given(txnBody.getNodeAccountID()).willReturn(accountID);
		given(policy.apply(any(), any())).willReturn(ResponseCodeEnum.OK);
		given(policies.check(any())).willReturn(SystemOpAuthorization.AUTHORIZED);
		given(lookup.lookupFor(any(), any())).willReturn(Optional.empty());
		given(hfs.exists(any())).willReturn(true);

		subject = new AwareProcessLogic(ctx);
	}

	@AfterEach
	public void cleanup() {
		AwareProcessLogic.log = LogManager.getLogger(AwareProcessLogic.class);
	}

	@Test
	public void shortCircuitsWithWarningOnZeroStakeSubmission() {
		// setup:
		var now = Instant.now();
		var then = now.minusMillis(1L);

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);

		// when:
		subject.incorporateConsensusTxn(platformTxn, now, 666);

		// then:
		verify(mockLog).warn(argThat((String s) -> s.startsWith("Ignoring a transaction submitted by zero-stake")));
	}

	@Test
	public void shortCircuitsWithErrorOnNonIncreasingConsensusTime() {
		// setup:
		var now = Instant.now();

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(now);

		// when:
		subject.incorporateConsensusTxn(platformTxn, now,1);

		// then:
		verify(mockLog).error(argThat((String s) -> s.startsWith("Catastrophic invariant failure!")));
	}

	@Test
	public void shortCircuitsWithWarningOnZeroStakeSignedTxnSubmission() {
		// setup:
		var now = Instant.now();
		var then = now.minusMillis(1L);
		SignedTransaction signedTxn = SignedTransaction.newBuilder().setBodyBytes(txnBody.toByteString()).build();
		Transaction platformSignedTxn = new Transaction(com.hederahashgraph.api.proto.java.Transaction.newBuilder().
				setSignedTransactionBytes(signedTxn.toByteString()).build().toByteArray());

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);

		// when:
		subject.incorporateConsensusTxn(platformSignedTxn, now, 666);

		// then:
		verify(mockLog).warn(argThat((String s) -> s.startsWith("Ignoring a transaction submitted by zero-stake")));
	}

	@Test
	@DisplayName("incorporateConsensusTxn assigns a failure due to memo size for ContractCreateInstance")
	public void shortCircuitsOnMemoSizeForContractCreate() {
		// setup:
		final Instant now = Instant.now();
		final Instant then = now.minusMillis(10L);
		final IssEventInfo eventInfo = mock(IssEventInfo.class);
		final TransactionRecord record = mock(TransactionRecord.class);
		given(eventInfo.status()).willReturn(NO_KNOWN_ISS);

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);
		given(ctx.addressBook().getAddress(666).getStake()).willReturn(1L);
		given(ctx.issEventInfo()).willReturn(eventInfo);
		given(txnCtx.consensusTime()).willReturn(now);
		given(txnBody.hasContractCreateInstance()).willReturn(true);
		given(txnBody.getContractCreateInstance()).willReturn(ContractCreateTransactionBody.newBuilder()
				.setMemo("This is a very long memo because it contains more than 100 characters, " +
						"which is greater than it is expected")
				.setFileID(FileID.newBuilder().build())
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(10).build())
				.build());

		given(contracts.getFailureTransactionRecord(any(), any(), any())).willReturn(record);

		// when:
		subject.incorporateConsensusTxn(platformTxn, now, 666);

		// then:
		verify(contracts).getFailureTransactionRecord(txnBody, now, MEMO_TOO_LONG);
	}

	@Test
	@DisplayName("creates a contract with small memo size")
	public void contractCreateInstanceIsCreated() {
		// setup:
		final byte[] contractByteCode = new byte[] { 100 };
		final SequenceNumber sequenceNumber = new SequenceNumber();
		final Instant now = Instant.now();
		final Instant then = now.minusMillis(10L);
		final IssEventInfo eventInfo = mock(IssEventInfo.class);
		final TransactionRecord record = mock(TransactionRecord.class);
		given(eventInfo.status()).willReturn(NO_KNOWN_ISS);

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);
		given(ctx.addressBook().getAddress(666).getStake()).willReturn(1L);
		given(ctx.issEventInfo()).willReturn(eventInfo);
		given(ctx.seqNo()).willReturn(sequenceNumber);

		given(txnCtx.consensusTime()).willReturn(now);
		given(txnBody.hasContractCreateInstance()).willReturn(true);
		given(txnBody.getContractCreateInstance()).willReturn(ContractCreateTransactionBody.newBuilder()
				.setMemo("This is a very small memo")
				.setFileID(FileID.newBuilder().build())
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(10).build())
				.build());
		given(hfs.cat(any())).willReturn(contractByteCode);


		// when:
		subject.incorporateConsensusTxn(platformTxn, now, 666);

		// then:
		verify(contracts).createContract(txnBody, now, contractByteCode, sequenceNumber);
	}

	@Test
	@DisplayName("creates a contract with no memo")
	public void contractCreateInstanceIsCreatedNoMemo() {
		// setup:
		final byte[] contractByteCode = new byte[] { 100 };
		final SequenceNumber sequenceNumber = new SequenceNumber();
		final Instant now = Instant.now();
		final Instant then = now.minusMillis(10L);
		final IssEventInfo eventInfo = mock(IssEventInfo.class);
		final TransactionRecord record = mock(TransactionRecord.class);
		given(eventInfo.status()).willReturn(NO_KNOWN_ISS);

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);
		given(ctx.addressBook().getAddress(666).getStake()).willReturn(1L);
		given(ctx.issEventInfo()).willReturn(eventInfo);
		given(ctx.seqNo()).willReturn(sequenceNumber);

		given(txnCtx.consensusTime()).willReturn(now);
		given(txnBody.hasContractCreateInstance()).willReturn(true);
		given(txnBody.getContractCreateInstance()).willReturn(ContractCreateTransactionBody.newBuilder()
				.setFileID(FileID.newBuilder().build())
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(10).build())
				.build());
		given(hfs.cat(any())).willReturn(contractByteCode);


		// when:
		subject.incorporateConsensusTxn(platformTxn, now, 666);

		// then:
		verify(contracts).createContract(txnBody, now, contractByteCode, sequenceNumber);
	}

	@Test
	@DisplayName("incorporateConsensusTxn assigns a failure due to memo size for ContractUpdateInstance")
	public void shortCircuitsOnMemoSizeForContractUpdate() {
		// setup:
		final Instant now = Instant.now();
		final Instant then = now.minusMillis(10L);
		final IssEventInfo eventInfo = mock(IssEventInfo.class);
		final TransactionRecord record = mock(TransactionRecord.class);
		given(eventInfo.status()).willReturn(NO_KNOWN_ISS);

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);
		given(ctx.addressBook().getAddress(666).getStake()).willReturn(1L);
		given(ctx.issEventInfo()).willReturn(eventInfo);
		given(txnCtx.consensusTime()).willReturn(now);
		given(txnBody.hasContractUpdateInstance()).willReturn(true);
		given(txnBody.getContractUpdateInstance()).willReturn(ContractUpdateTransactionBody.newBuilder()
				.setMemo("This is a very long memo because it contains more than 100 characters, " +
						"which is greater than it is expected")
				.setFileID(FileID.newBuilder().build())
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(10).build())
				.build());

		given(contracts.getFailureTransactionRecord(any(), any(), any())).willReturn(record);

		// when:
		subject.incorporateConsensusTxn(platformTxn, now, 666);

		// then:
		verify(contracts).getFailureTransactionRecord(txnBody, now, MEMO_TOO_LONG);
	}

	@Test
	@DisplayName("ContractUpdateInstance is updated in contracts")
	public void contractUpdateInstanceIsUpdate() {
		// setup:
		final Instant now = Instant.now();
		final Instant then = now.minusMillis(10L);
		final IssEventInfo eventInfo = mock(IssEventInfo.class);
		final TransactionRecord record = mock(TransactionRecord.class);
		given(eventInfo.status()).willReturn(NO_KNOWN_ISS);

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);
		given(ctx.addressBook().getAddress(666).getStake()).willReturn(1L);
		given(ctx.issEventInfo()).willReturn(eventInfo);
		given(txnCtx.consensusTime()).willReturn(now);
		given(txnBody.hasContractUpdateInstance()).willReturn(true);
		given(txnBody.getContractUpdateInstance()).willReturn(ContractUpdateTransactionBody.newBuilder()
				.setMemo("This is a very small memo")
				.setFileID(FileID.newBuilder().build())
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(10).build())
				.build());


		// when:
		subject.incorporateConsensusTxn(platformTxn, now, 666);

		// then:
		verify(contracts).updateContract(txnBody, now);
	}
}