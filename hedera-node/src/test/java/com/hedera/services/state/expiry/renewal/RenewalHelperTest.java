package com.hedera.services.state.expiry.renewal;

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
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.backing.BackingAccounts;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.TokenAssociationMetadata;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.services.context.primitives.StateView.REMOVED_TOKEN;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_ACCOUNT;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_ACCOUNT_GRACE_PERIOD_OVER;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.EXPIRED_ACCOUNT_READY_TO_RENEW;
import static com.hedera.services.state.expiry.renewal.ExpiredEntityClassification.OTHER;
import static com.hedera.services.state.expiry.renewal.RenewalRecordsHelperTest.adjustmentsFrom;
import static com.hedera.services.state.expiry.renewal.RenewalRecordsHelperTest.tokensFrom;
import static com.hedera.services.utils.EntityNumPair.fromAccountTokenRel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RenewalHelperTest {
	private final long tokenBalance = 1_234L;
	private final long now = 1_234_567L;
	private final long nonZeroBalance = 1L;
	private final MockGlobalDynamicProps dynamicProps = new MockGlobalDynamicProps();

	private final MerkleAccount nonExpiredAccount = MerkleAccountFactory.newAccount()
			.balance(0).expirationTime(now + 1)
			.alias(ByteString.copyFromUtf8("aaaa"))
			.get();
	private final MerkleAccount expiredAccountZeroBalance = MerkleAccountFactory.newAccount()
			.balance(0).expirationTime(now - 1)
			.alias(ByteString.copyFromUtf8("bbbb"))
			.get();
	private final MerkleAccount expiredDeletedAccount = MerkleAccountFactory.newAccount()
			.balance(0)
			.deleted(true)
			.alias(ByteString.copyFromUtf8("cccc"))
			.expirationTime(now - 1)
			.get();
	private final MerkleAccount expiredAccountNonZeroBalance = MerkleAccountFactory.newAccount()
			.balance(nonZeroBalance).expirationTime(now - 1)
			.alias(ByteString.copyFromUtf8("dddd"))
			.get();
	private final MerkleAccount fundingAccount = MerkleAccountFactory.newAccount()
			.balance(0)
			.alias(ByteString.copyFromUtf8("eeee"))
			.get();
	private final MerkleAccount contractAccount = MerkleAccountFactory.newAccount()
			.isSmartContract(true)
			.balance(0).expirationTime(now - 1)
			.get();
	private final long nonExpiredAccountNum = 1L, brokeExpiredAccountNum = 2L, fundedExpiredAccountNum = 3L,
			expiredAccountNum = 4L;
	private final EntityId expiredTreasuryId = new EntityId(0, 0, brokeExpiredAccountNum);
	private final EntityId treasuryId = new EntityId(0, 0, 666L);
	private final AccountID treasuryGrpcId = treasuryId.toGrpcAccountId();
	private final MerkleToken deletedToken = new MerkleToken(
			Long.MAX_VALUE, 1L, 0,
			"GONE", "Long lost dream",
			true, true, expiredTreasuryId);
	private final MerkleToken longLivedToken = new MerkleToken(
			Long.MAX_VALUE, 1L, 0,
			"HERE", "Dreams never die",
			true, true, treasuryId);
	private final long deletedTokenNum = 1234L, survivedTokenNum = 4321L;
	private final EntityNum deletedTokenId = EntityNum.fromLong(deletedTokenNum);
	private final EntityNum survivedTokenId = EntityNum.fromLong(survivedTokenNum);
	private final TokenID deletedTokenGrpcId = deletedTokenId.toGrpcTokenId();
	private final TokenID survivedTokenGrpcId = survivedTokenId.toGrpcTokenId();
	private final TokenID missingTokenGrpcId = TokenID.newBuilder().setTokenNum(5678L).build();
	private final EntityNumPair deletedAssociationId =
			EntityNumPair.fromLongs(brokeExpiredAccountNum, deletedTokenNum);
	private final EntityNumPair survivedAssociationId =
			EntityNumPair.fromLongs(brokeExpiredAccountNum, survivedTokenNum);
	private final EntityNumPair missingAssociationId =
			EntityNumPair.fromLongs(brokeExpiredAccountNum, missingTokenGrpcId.getTokenNum());

	{
		deletedToken.setDeleted(true);
	}

	@Mock
	private MerkleMap<EntityNum, MerkleToken> tokens;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> accounts;
	@Mock
	private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;
	@Mock
	private BackingAccounts backingAccounts;
	@Mock
	private TokenStore tokenStore;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private SigImpactHistorian sigImpactHistorian;
	@Mock
	private TokenAssociationMetadata tokenAssociationMetadata;

	private RenewalHelper subject;

	@BeforeEach
	void setUp() {
		subject = new RenewalHelper(tokenStore, sigImpactHistorian, dynamicProps,
				() -> tokens, () -> accounts, () -> tokenRels, backingAccounts, aliasManager);
	}

	private void linkWellKnownEntities(final AliasManager liveAliasManager) {
		liveAliasManager.link(nonExpiredAccount.getAlias(), EntityNum.fromLong(nonExpiredAccountNum));
		liveAliasManager.link(expiredAccountZeroBalance.getAlias(), EntityNum.fromLong(brokeExpiredAccountNum));
		liveAliasManager.link(expiredDeletedAccount.getAlias(), EntityNum.fromLong(expiredAccountNum));
		liveAliasManager.link(expiredAccountNonZeroBalance.getAlias(), EntityNum.fromLong(fundedExpiredAccountNum));
		liveAliasManager.link(fundingAccount.getAlias(), EntityNum.fromLong(98));
	}

	@Test
	void classifiesNonAccount() {
		// expect:
		assertEquals(OTHER, subject.classify(4L, now));
	}

	@Test
	void classifiesNonExpiredAccount() {
		givenPresent(nonExpiredAccountNum, nonExpiredAccount);

		// expect:
		assertEquals(OTHER, subject.classify(nonExpiredAccountNum, now));
	}

	@Test
	void classifiesContractAccount() {
		givenPresent(nonExpiredAccountNum, contractAccount);

		// expect:
		assertEquals(OTHER, subject.classify(nonExpiredAccountNum, now));
	}

	@Test
	void classifiesDeletedAccountAfterExpiration() {
		givenPresent(brokeExpiredAccountNum, expiredDeletedAccount);

		// expect:
		assertEquals(
				DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
				subject.classify(brokeExpiredAccountNum, now));
	}

	@Test
	void classifiesDetachedAccountAfterGracePeriod() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);

		// expect:
		assertEquals(
				DETACHED_ACCOUNT_GRACE_PERIOD_OVER,
				subject.classify(brokeExpiredAccountNum, now + dynamicProps.autoRenewGracePeriod()));
	}

	@Test
	void classifiesDetachedAccountAfterGracePeriodAsOtherIfTokenNotYetRemoved() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);
		given(tokenStore.isKnownTreasury(grpcIdWith(brokeExpiredAccountNum))).willReturn(true);

		// expect:
		assertEquals(
				DETACHED_TREASURY_GRACE_PERIOD_OVER_BEFORE_TOKEN,
				subject.classify(brokeExpiredAccountNum, now + dynamicProps.autoRenewGracePeriod()));
	}

	@Test
	void classifiesDetachedAccount() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);

		// expect:
		assertEquals(
				DETACHED_ACCOUNT,
				subject.classify(brokeExpiredAccountNum, now));
	}

	@Test
	void classifiesFundedExpiredAccount() {
		givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance);

		// expect:
		assertEquals(EXPIRED_ACCOUNT_READY_TO_RENEW, subject.classify(fundedExpiredAccountNum, now));
		// and:
		assertEquals(expiredAccountNonZeroBalance, subject.getLastClassifiedAccount());
	}

	@Test
	void throwsOnRemovingIfLastClassifiedHadNonzeroBalance() {
		givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance);

		// when:
		subject.classify(fundedExpiredAccountNum, now);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.removeLastClassifiedAccount());
	}

	@Test
	void throwsOnRemovingIfNoLastClassified() {
		// expect:
		assertThrows(IllegalStateException.class, () -> subject.removeLastClassifiedAccount());
	}

	@Test
	void shortCircuitsToJustRemovingRelIfZeroBalance() {
		// setup:
		final var expiredKey = EntityNum.fromLong(brokeExpiredAccountNum);
		final var expiredMeta = new TokenAssociationMetadata(
				3, 0, EntityNumPair.fromLongs(brokeExpiredAccountNum, deletedTokenNum));

		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);
		expiredAccountZeroBalance.setTokenAssociationMetadata(expiredMeta);
		givenTokenPresent(deletedTokenId, deletedToken);
		givenTokenPresent(survivedTokenId, longLivedToken);
		final var missingTokenNum = EntityNum.fromTokenId(missingTokenGrpcId);
		given(tokens.getOrDefault(missingTokenNum, REMOVED_TOKEN)).willReturn(REMOVED_TOKEN);
		givenRelPresentWith(expiredKey, deletedTokenId, 0,
				deletedAssociationId,
				survivedAssociationId,
				EntityNumPair.MISSING_NUM_PAIR);
		givenRelPresentWith(expiredKey, survivedTokenId, 0,
				survivedAssociationId,
				missingAssociationId,
				deletedAssociationId);
		givenRelPresentWith(expiredKey, missingTokenNum, 0,
				missingAssociationId,
				EntityNumPair.MISSING_NUM_PAIR,
				survivedAssociationId);

		// when:
		subject.classify(brokeExpiredAccountNum, now);
		// and:
		var displacedTokens = subject.removeLastClassifiedAccount();

		// then:
		verify(backingAccounts).remove(expiredKey.toGrpcAccountId());
		verify(tokenRels).remove(fromAccountTokenRel(grpcIdWith(brokeExpiredAccountNum), deletedTokenGrpcId));
		verify(tokenRels).remove(fromAccountTokenRel(grpcIdWith(brokeExpiredAccountNum), survivedTokenGrpcId));
		verify(tokenRels).remove(fromAccountTokenRel(grpcIdWith(brokeExpiredAccountNum), missingTokenGrpcId));
		verify(aliasManager).forgetAliasIfPresent(expiredKey, accounts);
		// and:
		assertTrue(displacedTokens.getLeft().isEmpty());
	}

	@Test
	void removesLastClassifiedIfAppropriate() {
		final var expiredKey = EntityNum.fromLong(brokeExpiredAccountNum);

		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);
		final var expiredMeta = new TokenAssociationMetadata(
				3, 0, EntityNumPair.fromLongs(brokeExpiredAccountNum, deletedTokenNum));
		expiredAccountZeroBalance.setTokenAssociationMetadata(expiredMeta);
		givenTokenPresent(deletedTokenId, deletedToken);
		givenTokenPresent(survivedTokenId, longLivedToken);
		final var missingTokenNum = EntityNum.fromTokenId(missingTokenGrpcId);
		given(tokens.getOrDefault(missingTokenNum, REMOVED_TOKEN)).willReturn(REMOVED_TOKEN);

		givenRelPresentWith(expiredKey, deletedTokenId, Long.MAX_VALUE,
				deletedAssociationId,
				survivedAssociationId,
				EntityNumPair.MISSING_NUM_PAIR);
		givenRelPresentWith(expiredKey, survivedTokenId, tokenBalance,
				survivedAssociationId,
				missingAssociationId,
				deletedAssociationId);
		givenRelPresentWith(expiredKey, missingTokenNum, 0,
				missingAssociationId,
				EntityNumPair.MISSING_NUM_PAIR,
				survivedAssociationId);
		givenModifiableRelPresent(EntityNum.fromAccountId(treasuryGrpcId), survivedTokenId, 0L);
		given(accounts.get(expiredKey)).willReturn(expiredAccountZeroBalance);
		given(aliasManager.forgetAliasIfPresent(expiredKey, accounts)).willReturn(true);

		subject.classify(brokeExpiredAccountNum, now);

		var displacedTokens = subject.removeLastClassifiedAccount();

		verify(backingAccounts).remove(expiredKey.toGrpcAccountId());
		verify(sigImpactHistorian).markEntityChanged(brokeExpiredAccountNum);
		verify(tokenRels).remove(fromAccountTokenRel(grpcIdWith(brokeExpiredAccountNum), deletedTokenGrpcId));
		verify(tokenRels).remove(fromAccountTokenRel(grpcIdWith(brokeExpiredAccountNum), survivedTokenGrpcId));
		verify(tokenRels).remove(fromAccountTokenRel(grpcIdWith(brokeExpiredAccountNum), survivedTokenGrpcId));
		verify(aliasManager).forgetAliasIfPresent(expiredKey, accounts);
		verify(sigImpactHistorian).markAliasChanged(expiredAccountZeroBalance.getAlias());
		// and:
		final var ttls = List.of(
				ttlOf(survivedTokenGrpcId, grpcIdWith(brokeExpiredAccountNum), treasuryGrpcId, tokenBalance));
		assertEquals(tokensFrom(ttls), displacedTokens.getLeft());
		assertEquals(adjustmentsFrom(ttls), displacedTokens.getRight());
	}

	@Test
	void removesAutoAccountEntityWhenExpired() {
		MerkleMap<EntityNum, MerkleAccount> accountsMap = new MerkleMap<>();
		accountsMap.put(EntityNum.fromLong(nonExpiredAccountNum), nonExpiredAccount);
		accountsMap.put(EntityNum.fromLong(brokeExpiredAccountNum), expiredAccountZeroBalance);
		final var expiredMeta = new TokenAssociationMetadata(
				3, 0, EntityNumPair.fromLongs(brokeExpiredAccountNum, deletedTokenNum));
		expiredAccountZeroBalance.setTokenAssociationMetadata(expiredMeta);

		final FCHashMap<ByteString, EntityNum> aliases = new FCHashMap<>();
		AliasManager liveAliasManager = new AliasManager(() -> aliases);
		linkWellKnownEntities(liveAliasManager);

		final var backingAccounts = new BackingAccounts(() -> accountsMap);
		backingAccounts.put(IdUtils.asAccount("0.0." + nonExpiredAccountNum), nonExpiredAccount);
		backingAccounts.put(IdUtils.asAccount("0.0." + brokeExpiredAccountNum), expiredAccountZeroBalance);

		subject = new RenewalHelper(
				tokenStore, sigImpactHistorian, dynamicProps, 
                                () -> tokens, () -> accountsMap, () -> tokenRels,
				backingAccounts, liveAliasManager);

		final var expiredKey = EntityNum.fromLong(brokeExpiredAccountNum);

		givenTokenPresent(deletedTokenId, deletedToken);
		givenTokenPresent(survivedTokenId, longLivedToken);
		final var missingTokenNum = EntityNum.fromTokenId(missingTokenGrpcId);
		given(tokens.getOrDefault(missingTokenNum, REMOVED_TOKEN)).willReturn(REMOVED_TOKEN);
		givenRelPresentWith(expiredKey, deletedTokenId, 0,
				deletedAssociationId,
				survivedAssociationId,
				EntityNumPair.MISSING_NUM_PAIR);
		givenRelPresentWith(expiredKey, survivedTokenId, 0,
				survivedAssociationId,
				missingAssociationId,
				deletedAssociationId);
		givenRelPresentWith(expiredKey, missingTokenNum, 0,
				missingAssociationId,
				EntityNumPair.MISSING_NUM_PAIR,
				survivedAssociationId);

		assertTrue(liveAliasManager.contains(expiredAccountZeroBalance.getAlias()));
		assertTrue(backingAccounts.contains(AccountID.newBuilder().setAccountNum(brokeExpiredAccountNum).build()));

		subject.classify(brokeExpiredAccountNum, now);
		subject.removeLastClassifiedAccount();

		assertFalse(backingAccounts.contains(AccountID.newBuilder().setAccountNum(brokeExpiredAccountNum).build()));
	}

	@Test
	void renewsLastClassifiedAsRequested() {
		// setup:
		var key = EntityNum.fromLong(fundedExpiredAccountNum);
		var fundingKey = EntityNum.fromInt(98);

		givenPresent(fundedExpiredAccountNum, expiredAccountNonZeroBalance, true);
		givenPresent(98, fundingAccount, true);

		// when:
		subject.classify(fundedExpiredAccountNum, now);
		// and:
		subject.renewLastClassifiedWith(nonZeroBalance, 3600L);

		// then:
		verify(accounts).getForModify(key);
		verify(accounts).getForModify(fundingKey);
		verify(aliasManager, never()).forgetAliasIfPresent(fundingKey, accounts);
	}

	@Test
	void cannotRenewIfNoLastClassified() {
		// expect:
		assertThrows(IllegalStateException.class,
				() -> subject.renewLastClassifiedWith(nonZeroBalance, 3600L));
	}

	@Test
	void rejectsAsIseIfFeeIsUnaffordable() {
		givenPresent(brokeExpiredAccountNum, expiredAccountZeroBalance);

		// when:
		subject.classify(brokeExpiredAccountNum, now);
		// expect:
		assertThrows(IllegalStateException.class,
				() -> subject.renewLastClassifiedWith(nonZeroBalance, 3600L));
	}

	private EntityNumPair assoc(EntityNum a, EntityNum b) {
		return EntityNumPair.fromLongs(a.longValue(), b.longValue());
	}

	private AccountID grpcIdWith(long num) {
		return AccountID.newBuilder().setAccountNum(num).build();
	}

	private void givenPresent(long num, MerkleAccount account) {
		givenPresent(num, account, false);
	}

	private void givenTokenPresent(EntityNum id, MerkleToken token) {
		given(tokens.getOrDefault(id, REMOVED_TOKEN)).willReturn(token);
		token.setKey(id);
	}

	private void givenRelPresentWith(
			final EntityNum account,
			final EntityNum token,
			final long balance,
			final EntityNumPair key,
			final EntityNumPair nextKey,
			final EntityNumPair prevKey
	) {
		final var rel = assoc(account, token);
		final var asc  = new MerkleTokenRelStatus(balance, false, false, false);
		asc.setKey(key);
		asc.setPrevKey(prevKey);
		asc.setNextKey(nextKey);
		given(tokenRels.get(rel)).willReturn(asc);
	}

	private void givenModifiableRelPresent(EntityNum account, EntityNum token, long balance) {
		var rel = EntityNumPair.fromLongs(account.longValue(), token.longValue());
		given(tokenRels.getForModify(rel)).willReturn(new MerkleTokenRelStatus(balance, false, false, true));
	}

	private void givenPresent(long num, MerkleAccount account, boolean modifiable) {
		var key = EntityNum.fromLong(num);
		if (num != 98) {
			given(accounts.containsKey(key)).willReturn(true);
			given(accounts.get(key)).willReturn(account);
		}
		if (modifiable) {
			given(accounts.getForModify(key)).willReturn(account);
		}
	}

	static TokenTransferList ttlOf(TokenID scope, AccountID src, AccountID dest, long amount) {
		return TokenTransferList.newBuilder()
				.setToken(scope)
				.addTransfers(aaOf(src, -amount))
				.addTransfers(aaOf(dest, +amount))
				.build();
	}

	static AccountAmount aaOf(AccountID id, long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(id)
				.setAmount(amount)
				.build();
	}
}
