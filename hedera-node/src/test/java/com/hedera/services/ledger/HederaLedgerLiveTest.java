package com.hedera.services.ledger;

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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.exceptions.InconsistentAdjustmentsException;
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.ledger.accounts.HashMapBackingAccounts;
import com.hedera.services.ledger.accounts.HashMapBackingNfts;
import com.hedera.services.ledger.accounts.HashMapBackingTokenRels;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.mocks.TestContextValidator;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.verify;

public class HederaLedgerLiveTest extends BaseHederaLedgerTestHelper {
	long thisSecond = 1_234_567L;

	@BeforeEach
	void setup() {
		commonSetup();

		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class,
				MerkleAccount::new,
				new HashMapBackingAccounts(),
				new ChangeSummaryManager<>());
		FCMap<MerkleEntityId, MerkleToken> tokens = new FCMap<>();
		FCOneToManyRelation<PermHashInteger, Long> uniqueTokenOwnerships = new FCOneToManyRelation<>();
		FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAccountOwnerships = new FCOneToManyRelation<>();
		FCOneToManyRelation<PermHashInteger, Long> uniqueTokenTreasuryOwnerships = new FCOneToManyRelation<>();

		nftsLedger = new TransactionalLedger<>(
				NftProperty.class,
				MerkleUniqueToken::new,
				new HashMapBackingNfts(),
				new ChangeSummaryManager<>());
		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class,
				MerkleTokenRelStatus::new,
				new HashMapBackingTokenRels(),
				new ChangeSummaryManager<>());
		tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);
		final var viewManager = new UniqTokenViewsManager(
				() -> uniqueTokenOwnerships,
				() -> uniqueTokenAccountOwnerships,
				() -> uniqueTokenTreasuryOwnerships,
				false);
		tokenStore = new HederaTokenStore(
				ids,
				TestContextValidator.TEST_VALIDATOR,
				viewManager,
				new MockGlobalDynamicProps(),
				() -> tokens,
				tokenRelsLedger,
				nftsLedger);
		subject = new HederaLedger(tokenStore, ids, creator, validator, historian, dynamicProps, accountsLedger);
	}

	@Test
	void throwsOnCommittingInconsistentAdjustments() {
		// when:
		subject.begin();
		subject.adjustBalance(genesis, -1L);

		// then:
		assertThrows(InconsistentAdjustmentsException.class, () -> subject.commit());
	}

	@Test
	void resetsNetTransfersAfterCommit() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.commit();
		// and:
		subject.begin();
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));

		// then:
		assertEquals(2L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	void doesntIncludeZeroAdjustsInNetTransfers() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.delete(a, genesis);

		// then:
		assertEquals(0L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	void doesntAllowDestructionOfRealCurrency() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.destroy(a);

		// then:
		assertThrows(InconsistentAdjustmentsException.class, () -> subject.commit());
	}

	@Test
	void allowsDestructionOfEphemeralCurrency() {
		// when:
		subject.begin();
		AccountID a = asAccount("1.2.3");
		subject.spawn(a, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.destroy(a);
		subject.commit();

		// then:
		assertFalse(subject.exists(a));
		assertEquals(GENESIS_BALANCE, subject.getBalance(genesis));
	}

	@Test
	void recordsCreationOfAccountDeletedInSameTxn() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.delete(a, genesis);
		int numNetTransfers = subject.netTransfersInTxn().getAccountAmountsCount();
		subject.commit();

		// then:
		assertEquals(0, numNetTransfers);
		assertTrue(subject.exists(a));
		assertEquals(GENESIS_BALANCE, subject.getBalance(genesis));
	}

	@Test
	void addsRecordsAndEntitiesBeforeCommitting() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.commit();

		// then:
		verify(historian).finalizeExpirableTransactionRecord();
		verify(historian).noteNewExpirationEvents();
	}

	@Test
	void resetsNetTransfersAfterRollback() {
		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		subject.rollback();
		// and:
		subject.begin();
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));

		// then:
		assertEquals(2L, subject.netTransfersInTxn().getAccountAmountsList().size());
	}

	@Test
	void returnsNetTransfersInBalancedTxn() {
		setup();
		// and:
		TokenID tA, tB;

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1_000L, new HederaAccountCustomizer().memo("a"));
		AccountID b = subject.create(genesis, 2_000L, new HederaAccountCustomizer().memo("b"));
		AccountID c = subject.create(genesis, 3_000L, new HederaAccountCustomizer().memo("c"));
		AccountID d = subject.create(genesis, 4_000L, new HederaAccountCustomizer().memo("d"));

		Account aa = new Account(Id.fromGrpcAccount(a));
		aa.setAssociatedTokens(new CopyOnWriteIds());

		Account ba = new Account(Id.fromGrpcAccount(b));
		ba.setAssociatedTokens(new CopyOnWriteIds());

		Account ca = new Account(Id.fromGrpcAccount(c));
		ca.setAssociatedTokens(new CopyOnWriteIds());

		Account da = new Account(Id.fromGrpcAccount(d));
		da.setAssociatedTokens(new CopyOnWriteIds());

		// and:
		tA = ids.newTokenId(a);
		tB = ids.newTokenId(b);
		var rA = Token.fromGrpcTokenCreate(Id.fromGrpcToken(tA), stdWith("MINE", "MINE", a), aa, null, List.of(), thisSecond);
		var rB = Token.fromGrpcTokenCreate(Id.fromGrpcToken(tB), stdWith("YOURS", "YOURS", b), ba, null, List.of(), thisSecond);
		// and:
		aa.associateWith(List.of(rA, rB), 10);
		ba.associateWith(List.of(rA, rB), 10);
		ca.associateWith(List.of(rA, rB), 10);
		da.associateWith(List.of(rA, rB), 10);
		// and:
		subject.doTransfer(d, a, 1_000L);
		subject.delete(d, b);
		subject.adjustBalance(c, 1_000L);
		subject.adjustBalance(genesis, -1_000L);
		subject.doTransfers(TxnUtils.withAdjustments(a, -500L, b, 250L, c, 250L));
		// and:
		subject.adjustTokenBalance(a, tA, +10_000);
		subject.adjustTokenBalance(a, tA, -5_000);
		subject.adjustTokenBalance(a, tB, +1);
		subject.adjustTokenBalance(a, tB, -1);

		subject.adjustTokenBalance(b, tB, +10_000);
		subject.adjustTokenBalance(c, tB, +50);
		subject.adjustTokenBalance(c, tB, +50);
		subject.adjustTokenBalance(c, tB, -50);
		subject.adjustTokenBalance(c, tA, +5000);
		subject.freeze(a, tB);
		subject.adjustTokenBalance(a, tB, +1_000_000);

		// then:
		assertThat(
				subject.netTransfersInTxn().getAccountAmountsList(),
				containsInAnyOrder(
						AccountAmount.newBuilder().setAccountID(a).setAmount(1_500L).build(),
						AccountAmount.newBuilder().setAccountID(b).setAmount(5_250L).build(),
						AccountAmount.newBuilder().setAccountID(c).setAmount(4_250L).build(),
						AccountAmount.newBuilder().setAccountID(genesis).setAmount(-11_000L).build()));
	}

	@Test
	void recognizesPendingCreates() {
		setup();

		// when:
		subject.begin();
		AccountID a = subject.create(genesis, 1L, new HederaAccountCustomizer().memo("a"));

		// then:
		assertTrue(subject.isPendingCreation(a));
		assertFalse(subject.isPendingCreation(genesis));
	}

	private TokenCreateTransactionBody stdWith(String symbol, String tokenName, AccountID account) {
		var key = TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey();
		return TokenCreateTransactionBody.newBuilder()
				.setAdminKey(key)
				.setFreezeKey(TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT.asKey())
				.setSymbol(symbol)
				.setName(tokenName)
				.setInitialSupply(0)
				.setTreasury(account)
				.setExpiry(Timestamp.newBuilder().setSeconds(2 * thisSecond))
				.setDecimals(0)
				.setFreezeDefault(false)
				.build();
	}

	private TokenTransferList construct(TokenID token, AccountAmount... xfers) {
		return TokenTransferList.newBuilder()
				.setToken(token)
				.addAllTransfers(List.of(xfers))
				.build();
	}

}
