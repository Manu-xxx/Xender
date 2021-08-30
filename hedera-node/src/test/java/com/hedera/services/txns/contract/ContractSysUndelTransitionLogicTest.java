package com.hedera.services.txns.contract;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.swirlds.virtualmap.VirtualMap;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class ContractSysUndelTransitionLogicTest {
	final private AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
	final private ContractID target = ContractID.newBuilder().setContractNum(9_999L).build();

	private Instant consensusTime;
	private OptionValidator validator;
	private ContractSysUndelTransitionLogic.LegacySystemUndeleter delegate;
	private TransactionBody contractSysUndelTxn;
	private TransactionContext txnCtx;
	private PlatformTxnAccessor accessor;
	VirtualMap<MerkleEntityId, MerkleAccount> contracts;
	ContractSysUndelTransitionLogic subject;

	@BeforeEach
	private void setup() {
		consensusTime = Instant.now();

		delegate = mock(ContractSysUndelTransitionLogic.LegacySystemUndeleter.class);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.consensusTime()).willReturn(consensusTime);
		accessor = mock(PlatformTxnAccessor.class);
		validator = mock(OptionValidator.class);
		withRubberstampingValidator();

		subject = new ContractSysUndelTransitionLogic(validator, txnCtx, delegate, () -> contracts);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		// expect:
		assertTrue(subject.applicability().test(contractSysUndelTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void capturesBadUndelete() {
		// setup:
		TransactionRecord sysUndelRec = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(INVALID_CONTRACT_ID)
						.build())
				.build();

		givenValidTxnCtx();
		// and:
		given(delegate.perform(contractSysUndelTxn, consensusTime)).willReturn(sysUndelRec);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(INVALID_CONTRACT_ID);
	}

	@Test
	void followsHappyPathWithOverrides() {
		// setup:
		TransactionRecord sysUndelRec = TransactionRecord.newBuilder()
				.setReceipt(TransactionReceipt.newBuilder()
						.setStatus(SUCCESS)
						.build())
				.build();

		givenValidTxnCtx();
		// and:
		given(delegate.perform(contractSysUndelTxn, consensusTime)).willReturn(sysUndelRec);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(SUCCESS);
	}

	@Test
	void acceptsOkSyntax() {
		givenValidTxnCtx();

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(contractSysUndelTxn));
	}

	@Test
	void acceptsDeletedContract() {
		givenValidTxnCtx();
		// and:
		given(validator.queryableContractStatus(target, contracts)).willReturn(CONTRACT_DELETED);

		// expect:
		assertEquals(OK, subject.semanticCheck().apply(contractSysUndelTxn));
	}

	@Test
	void rejectsInvalidCid() {
		givenValidTxnCtx();
		// and:
		given(validator.queryableContractStatus(target, contracts)).willReturn(INVALID_CONTRACT_ID);

		// expect:
		assertEquals(INVALID_CONTRACT_ID, subject.semanticCheck().apply(contractSysUndelTxn));
	}

	@Test
	void translatesUnknownException() {
		givenValidTxnCtx();

		given(delegate.perform(any(), any())).willThrow(IllegalStateException.class);

		// when:
		subject.doStateTransition();

		// then:
		verify(txnCtx).setStatus(FAIL_INVALID);
	}

	private void givenValidTxnCtx() {
		var op = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setSystemUndelete(
						SystemUndeleteTransactionBody.newBuilder()
								.setContractID(target));
		contractSysUndelTxn = op.build();
		given(accessor.getTxn()).willReturn(contractSysUndelTxn);
		given(txnCtx.accessor()).willReturn(accessor);
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(payer)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private void withRubberstampingValidator() {
		given(validator.queryableContractStatus(target, contracts)).willReturn(OK);
	}
}
