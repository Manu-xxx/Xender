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
package com.hedera.node.app.service.token.impl.handlers;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

class CryptoDeleteHandlerTest extends CryptoHandlerTestBase {
	private final AccountID deleteAccountId = asAccount("0.0.3213");
	private final AccountID transferAccountId = asAccount("0.0.32134");
	private final Long deleteAccountNum = deleteAccountId.getAccountNum();
	private final Long transferAccountNum = transferAccountId.getAccountNum();

	@Mock
	private MerkleAccount deleteAccount;
	@Mock
	private MerkleAccount transferAccount;

	private final CryptoDeleteHandler subject = new CryptoDeleteHandler();

	@Test
	void preHandlesCryptoDeleteIfNoReceiverSigRequired() {
		final var keyUsed = (JKey) payerKey;

		given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
		given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
		given(deleteAccount.getAccountKey()).willReturn(keyUsed);
		given(transferAccount.getAccountKey()).willReturn(keyUsed);
		given(transferAccount.isReceiverSigRequired()).willReturn(false);

		final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

		final var meta = subject.preHandle(txn, payer, store);

		assertEquals(txn, meta.txnBody());
		assertEquals(payerKey, meta.payerKey());
		basicMetaAssertions(meta, 1, false, OK);
		assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
	}

	@Test
	void preHandlesCryptoDeleteIfReceiverSigRequiredVanilla() {
		final var keyUsed = (JKey) payerKey;

		given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
		given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
		given(deleteAccount.getAccountKey()).willReturn(keyUsed);
		given(transferAccount.getAccountKey()).willReturn(keyUsed);
		given(transferAccount.isReceiverSigRequired()).willReturn(true);

		final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);

		final var meta = subject.preHandle(txn, payer, store);

		assertEquals(txn, meta.txnBody());
		basicMetaAssertions(meta, 2, false, OK);
		assertEquals(payerKey, meta.payerKey());
		assertIterableEquals(List.of(keyUsed, keyUsed), meta.requiredNonPayerKeys());
	}

	@Test
	void doesntAddBothKeysAccountsSameAsPayerForCryptoDelete() {
		final var txn = deleteAccountTransaction(payer, payer);

		final var meta = subject.preHandle(txn, payer, store);

		assertEquals(txn, meta.txnBody());
		basicMetaAssertions(meta, 0, false, OK);
		assertEquals(payerKey, meta.payerKey());
		assertIterableEquals(List.of(), meta.requiredNonPayerKeys());
	}

	@Test
	void doesntAddTransferKeyIfAccountSameAsPayerForCryptoDelete() {
		final var keyUsed = (JKey) payerKey;

		given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
		given(deleteAccount.getAccountKey()).willReturn(keyUsed);

		final var txn = deleteAccountTransaction(deleteAccountId, payer);

		final var meta = subject.preHandle(txn, payer, store);

		assertEquals(txn, meta.txnBody());
		assertEquals(payerKey, meta.payerKey());
		basicMetaAssertions(meta, 1, false, OK);
		assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
	}

	@Test
	void doesntAddDeleteKeyIfAccountSameAsPayerForCryptoDelete() {
		final var keyUsed = (JKey) payerKey;

		given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
		given(transferAccount.getAccountKey()).willReturn(keyUsed);
		given(transferAccount.isReceiverSigRequired()).willReturn(true);

		final var txn = deleteAccountTransaction(payer, transferAccountId);

		final var meta = subject.preHandle(txn, payer, store);

		assertEquals(txn, meta.txnBody());
		basicMetaAssertions(meta, 1, false, OK);
		assertEquals(payerKey, meta.payerKey());
		assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
	}

	@Test
	void failsWithResponseCodeIfAnyAccountMissingForCryptoDelete() {
		final var keyUsed = (JKey) payerKey;

		/* ------ payerAccount missing, so deleteAccount and transferAccount will not be added  ------ */
		final var txn = deleteAccountTransaction(deleteAccountId, transferAccountId);
		given(accounts.get(payerNum)).willReturn(Optional.empty());
		given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
		given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));
		given(deleteAccount.getAccountKey()).willReturn(keyUsed);

		var meta = subject.preHandle(txn, payer, store);
		basicMetaAssertions(meta, 0, true, INVALID_PAYER_ACCOUNT_ID);
		assertNull(meta.payerKey());
		assertIterableEquals(List.of(), meta.requiredNonPayerKeys());

		/* ------ deleteAccount missing, so transferAccount will not be added ------ */
		given(accounts.get(payerNum)).willReturn(Optional.of(payerAccount));
		given(payerAccount.getAccountKey()).willReturn(keyUsed);
		given(accounts.get(deleteAccountNum)).willReturn(Optional.empty());
		given(accounts.get(transferAccountNum)).willReturn(Optional.of(transferAccount));

		meta = subject.preHandle(txn, payer, store);

		basicMetaAssertions(meta, 0, true, INVALID_ACCOUNT_ID);
		assertEquals(payerKey, meta.payerKey());
		assertIterableEquals(List.of(), meta.requiredNonPayerKeys());

		/* ------ transferAccount missing ------ */
		given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
		given(deleteAccount.getAccountKey()).willReturn(keyUsed);
		given(accounts.get(transferAccountNum)).willReturn(Optional.empty());

		meta = subject.preHandle(txn, payer, store);

		basicMetaAssertions(meta, 1, true, INVALID_TRANSFER_ACCOUNT_ID);
		assertEquals(payerKey, meta.payerKey());
		assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
	}

	@Test
	void doesntExecuteIfAccountIdIsDefaultInstance() {
		final var keyUsed = (JKey) payerKey;

		given(accounts.get(deleteAccountNum)).willReturn(Optional.of(deleteAccount));
		given(deleteAccount.getAccountKey()).willReturn(keyUsed);

		final var txn = deleteAccountTransaction(deleteAccountId, AccountID.getDefaultInstance());

		final var meta = subject.preHandle(txn, payer, store);

		assertEquals(txn, meta.txnBody());
		basicMetaAssertions(meta, 1, false, OK);
		assertEquals(payerKey, meta.payerKey());
		assertIterableEquals(List.of(keyUsed), meta.requiredNonPayerKeys());
	}

	@Test
	void handleNotImplemented() {
		assertThrows(UnsupportedOperationException.class, () -> subject.handle(metaToHandle));
	}

	private TransactionBody deleteAccountTransaction(
			final AccountID deleteAccountId, final AccountID transferAccountId) {
		final var transactionID =
				TransactionID.newBuilder()
						.setAccountID(payer)
						.setTransactionValidStart(consensusTimestamp);
		final var deleteTxBody =
				CryptoDeleteTransactionBody.newBuilder()
						.setDeleteAccountID(deleteAccountId)
						.setTransferAccountID(transferAccountId);

		return TransactionBody.newBuilder()
				.setTransactionID(transactionID)
				.setCryptoDelete(deleteTxBody)
				.build();
	}
}
