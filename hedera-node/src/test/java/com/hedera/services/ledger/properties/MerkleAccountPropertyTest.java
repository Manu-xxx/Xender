package com.hedera.services.ledger.properties;

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

import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.tokens.TokenScope;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.ledger.properties.AccountProperty.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.AccountProperty.FUNDS_RECEIVED_RECORD_THRESHOLD;
import static com.hedera.services.ledger.properties.AccountProperty.FUNDS_SENT_RECORD_THRESHOLD;
import static com.hedera.services.ledger.properties.AccountProperty.HISTORY_RECORDS;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_KYC_GRANTED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.KEY;
import static com.hedera.services.ledger.properties.AccountProperty.MEMO;
import static com.hedera.services.ledger.properties.AccountProperty.PAYER_RECORDS;
import static com.hedera.services.ledger.properties.AccountProperty.PROXY;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
public class MerkleAccountPropertyTest {
	@Test
	public void cannotSetNegativeBalance() {
		// expect:
		assertThrows(
				IllegalArgumentException.class,
				() -> BALANCE.setter().accept(new MerkleAccount(), -1L));
	}

	@Test
	public void gettersAndSettersWork() throws Exception {
		// given:
		boolean origIsDeleted = false;
		boolean origIsReceiverSigReq = false;
		boolean origIsContract = false;
		long origBalance = 1L;
		long origReceivedRecordThresh = 1L;
		long origSendRecordThresh = 1L;
		long origAutoRenew = 1L;
		long origExpiry = 1L;
		Key origKey = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
		String origMemo = "a";
		AccountID origProxy = AccountID.getDefaultInstance();
		List<ExpirableTxnRecord> origRecords = new ArrayList<>();
		origRecords.add(expirableRecord(ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT));
		origRecords.add(expirableRecord(ResponseCodeEnum.INVALID_PAYER_SIGNATURE));
		List<ExpirableTxnRecord> origPayerRecords = new ArrayList<>();
		origPayerRecords.add(expirableRecord(ResponseCodeEnum.INVALID_CHUNK_NUMBER));
		origPayerRecords.add(expirableRecord(ResponseCodeEnum.INSUFFICIENT_TX_FEE));
		// and:
		boolean newIsDeleted = true;
		boolean newIsReceiverSigReq = true;
		boolean newIsContract = true;
		long newBalance = 2L;
		long newReceivedRecordThresh = 2L;
		long newSendRecordThresh = 2L;
		long newAutoRenew = 2L;
		long newExpiry = 2L;
		JKey newKey = new JKeyList();
		String newMemo = "b";
		EntityId newProxy = new EntityId(0, 0, 2);
		FCQueue<ExpirableTxnRecord> newRecords = new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER);
		newRecords.offer(expirableRecord(ResponseCodeEnum.SUCCESS));
		FCQueue<ExpirableTxnRecord> newPayerRecords = new FCQueue<>(ExpirableTxnRecord.LEGACY_PROVIDER);
		newPayerRecords.offer(expirableRecord(ResponseCodeEnum.INVALID_FILE_ID));
		// and:
		MerkleAccount account = new HederaAccountCustomizer()
				.fundsReceivedRecordThreshold(origReceivedRecordThresh)
				.fundsSentRecordThreshold(origSendRecordThresh)
				.key(JKey.mapKey(origKey))
				.expiry(origExpiry)
				.proxy(EntityId.ofNullableAccountId(origProxy))
				.autoRenewPeriod(origAutoRenew)
				.isDeleted(origIsDeleted)
				.memo(origMemo)
				.isSmartContract(origIsContract)
				.isReceiverSigRequired(origIsReceiverSigReq)
				.customizing(new MerkleAccount());
		account.setBalance(origBalance);
		account.records().offer(origRecords.get(0));
		account.records().offer(origRecords.get(1));
		account.payerRecords().offer(origPayerRecords.get(0));
		account.payerRecords().offer(origPayerRecords.get(1));
		// and:
		var unfrozenTokenId = IdUtils.tokenWith(123);
		var frozenTokenId = IdUtils.tokenWith(321);
		var newTokenBalance = 1_234_567L;
		var adminKey = TOKEN_ADMIN_KT.asJKeyUnchecked();
		var unfrozenToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"UnfrozenToken", "UnfrozenTokenName", false, true,
				new EntityId(1, 2, 3));
		unfrozenToken.setFreezeKey(adminKey);
		unfrozenToken.setKycKey(adminKey);
		var unfrozenTokenScope = TokenScope.scopeOf(unfrozenTokenId, unfrozenToken);
		var tokenBalance = new TokenScopedPropertyValue(unfrozenTokenId, unfrozenToken, newTokenBalance);
		var frozenToken = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"FrozenToken", "FrozenTokenName", true, false,
				new EntityId(1, 2, 3));
		frozenToken.setFreezeKey(adminKey);
		frozenToken.setKycKey(adminKey);
		var frozenTokenScope = TokenScope.scopeOf(frozenTokenId, frozenToken);
		var tokenFreeze = new TokenScopedPropertyValue(frozenTokenId, frozenToken, true);
		var tokenUnfreeze = new TokenScopedPropertyValue(frozenTokenId, frozenToken, false);
		var tokenGrantKyc = new TokenScopedPropertyValue(frozenTokenId, frozenToken, true);
		var tokenRevokeKyc = new TokenScopedPropertyValue(unfrozenTokenId, unfrozenToken, false);

		// expect:
		assertFalse((boolean)IS_FROZEN.scopedGetter().apply(account, unfrozenTokenScope));
		assertTrue((boolean)IS_FROZEN.scopedGetter().apply(account, frozenTokenScope));
		assertTrue((boolean)IS_KYC_GRANTED.scopedGetter().apply(account, unfrozenTokenScope));
		assertFalse((boolean)IS_KYC_GRANTED.scopedGetter().apply(account, frozenTokenScope));
		// and when:
		IS_DELETED.setter().accept(account, newIsDeleted);
		IS_RECEIVER_SIG_REQUIRED.setter().accept(account, newIsReceiverSigReq);
		IS_SMART_CONTRACT.setter().accept(account, newIsContract);
		BALANCE.setter().accept(account, newBalance);
		FUNDS_RECEIVED_RECORD_THRESHOLD.setter().accept(account, newReceivedRecordThresh);
		FUNDS_SENT_RECORD_THRESHOLD.setter().accept(account, newSendRecordThresh);
		AUTO_RENEW_PERIOD.setter().accept(account, newAutoRenew);
		EXPIRY.setter().accept(account, newExpiry);
		KEY.setter().accept(account, newKey);
		MEMO.setter().accept(account, newMemo);
		PROXY.setter().accept(account, newProxy);
		HISTORY_RECORDS.setter().accept(account, newRecords);
		PAYER_RECORDS.setter().accept(account, newPayerRecords);
		// and:
		BALANCE.setter().accept(account, tokenBalance);
		IS_FROZEN.setter().accept(account, tokenUnfreeze);
		IS_KYC_GRANTED.setter().accept(account, tokenGrantKyc);
		IS_KYC_GRANTED.setter().accept(account, tokenRevokeKyc);

		// then:
		assertEquals(newIsDeleted, IS_DELETED.getter().apply(account));
		assertEquals(newIsReceiverSigReq, IS_RECEIVER_SIG_REQUIRED.getter().apply(account));
		assertEquals(newIsContract, IS_SMART_CONTRACT.getter().apply(account));
		assertEquals(newBalance, BALANCE.getter().apply(account));
		assertEquals(newReceivedRecordThresh, FUNDS_RECEIVED_RECORD_THRESHOLD.getter().apply(account));
		assertEquals(newSendRecordThresh, FUNDS_SENT_RECORD_THRESHOLD.getter().apply(account));
		assertEquals(newAutoRenew, AUTO_RENEW_PERIOD.getter().apply(account));
		assertEquals(newExpiry, EXPIRY.getter().apply(account));
		assertEquals(newKey, KEY.getter().apply(account));
		assertEquals(newMemo, MEMO.getter().apply(account));
		assertEquals(newProxy, PROXY.getter().apply(account));
		assertEquals(newRecords, HISTORY_RECORDS.getter().apply(account));
		assertEquals(newPayerRecords, PAYER_RECORDS.getter().apply(account));
		// and:
		assertEquals(newTokenBalance, BALANCE.scopedGetter().apply(account, unfrozenTokenScope));
		assertFalse((boolean)IS_FROZEN.scopedGetter().apply(account, frozenTokenScope));
		// and when:
		IS_FROZEN.setter().accept(account, tokenFreeze);
		// then:
		assertTrue((boolean)IS_FROZEN.scopedGetter().apply(account, frozenTokenScope));
		assertFalse((boolean)IS_KYC_GRANTED.scopedGetter().apply(account, unfrozenTokenScope));
		assertTrue((boolean)IS_KYC_GRANTED.scopedGetter().apply(account, frozenTokenScope));
	}

	private ExpirableTxnRecord expirableRecord(ResponseCodeEnum status) {
		return ExpirableTxnRecord.fromGprc(
				TransactionRecord.newBuilder()
					.setReceipt(TransactionReceipt.newBuilder().setStatus(status))
				.build()
		);
	}

	@Test
	public void delegatesToNonScopedGetterAsExpected() {
		// setup:
		var subject = mock(AccountProperty.class);

		// given:
		willCallRealMethod().given(subject).scopedGetter();

		// expect:
		assertThrows(UnsupportedOperationException.class, subject::scopedGetter);
	}
}
