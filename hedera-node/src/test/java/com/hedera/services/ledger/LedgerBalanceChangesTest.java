package com.hedera.services.ledger;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.ledger.accounts.HashMapBackingAccounts;
import com.hedera.services.ledger.accounts.HashMapBackingTokenRels;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.fcmap.FCMap;
import com.swirlds.fcmap.internal.FCMLeaf;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class LedgerBalanceChangesTest {
	private BackingStore<AccountID, MerkleAccount> backingAccounts = new HashMapBackingAccounts();
	private BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> backingRels = new HashMapBackingTokenRels();

	private TokenStore tokenStore;
	private FCMap<MerkleEntityId, MerkleToken> tokens = new FCMap<>();
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
	private TransactionalLedger<
			Pair<AccountID, TokenID>,
			TokenRelProperty,
			MerkleTokenRelStatus> tokenRelsLedger;

	@Mock
	private EntityIdSource ids;
	@Mock
	private ExpiringCreations creator;
	@Mock
	private OptionValidator validator;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private AccountRecordsHistorian historian;

	private HederaLedger subject;

	@BeforeEach
	void setUp() throws ConstructableRegistryException {
		accountsLedger = new TransactionalLedger<>(
				AccountProperty.class, MerkleAccount::new, backingAccounts, new ChangeSummaryManager<>());
		tokenRelsLedger = new TransactionalLedger<>(
				TokenRelProperty.class, MerkleTokenRelStatus::new, backingRels, new ChangeSummaryManager<>());
		tokenRelsLedger.setKeyToString(BackingTokenRels::readableTokenRel);

		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FCMLeaf.class, FCMLeaf::new));
		tokens.put(tokenKey, tokenWithTreasury(aModel));
		tokens.put(anotherTokenKey, tokenWithTreasury(aModel));
		tokens.put(yetAnotherTokenKey, tokenWithTreasury(aModel));
		tokenStore = new HederaTokenStore(ids, validator, dynamicProperties, () -> tokens, tokenRelsLedger);

		subject = new HederaLedger(tokenStore, ids, creator, validator, historian, dynamicProperties, accountsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);
	}

	@Test
	void rejectsContractInAccountAmounts() {
		givenInitialBalances();
		backingAccounts.getRef(asGprcAccount(aModel)).setSmartContract(true);

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());

		subject.commit();

		// then:
		assertEquals(INVALID_ACCOUNT_ID, result);
		// and:
		assertInitialBalanceUnchanged();
	}

	@Test
	void rejectsMissingAccount() {
		givenInitialBalances();
		backingAccounts.remove(asGprcAccount(aModel));

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());

		subject.commit();

		// then:
		assertEquals(INVALID_ACCOUNT_ID, result);
		// and:
		assertInitialBalanceUnchanged(-1L);
	}

	@Test
	void rejectsInsufficientBalance() {
		givenInitialBalances();
		backingAccounts.getRef(asGprcAccount(aModel)).setBalanceUnchecked(0L);

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());

		subject.commit();

		// then:
		assertEquals(INSUFFICIENT_ACCOUNT_BALANCE, result);
		// and:
		assertInitialBalanceUnchanged(0L);
	}

	@Test
	void rejectsDetachedAccount() {
		givenInitialBalances();
		given(dynamicProperties.autoRenewEnabled()).willReturn(true);

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());

		subject.commit();

		// then:
		assertEquals(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL, result);
		// and:
		assertInitialBalanceUnchanged();
	}

	@Test
	void rejectsDeletedAccount() {
		givenInitialBalances();
		// and:
		backingAccounts.getRef(asGprcAccount(cModel)).setDeleted(true);

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());
		subject.commit();

		// then:
		assertEquals(ACCOUNT_DELETED, result);
		// and:
		assertInitialBalanceUnchanged();
	}

	@Test
	void rejectsMissingToken() {
		// setup:
		tokens.clear();
		tokens.put(anotherTokenKey.copy(), tokenWithTreasury(aModel));
		tokens.put(yetAnotherTokenKey.copy(), tokenWithTreasury(aModel));
		tokenStore = new HederaTokenStore(ids, validator, dynamicProperties, () -> tokens, tokenRelsLedger);

		subject = new HederaLedger(tokenStore, ids, creator, validator, historian, dynamicProperties, accountsLedger);
		subject.setTokenRelsLedger(tokenRelsLedger);

		givenInitialBalances();

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());
		subject.commit();

		// then:
		assertEquals(INVALID_TOKEN_ID, result);
		// and:
		assertInitialBalanceUnchanged();
	}

	@Test
	void rejectsInsufficientTokenBalance() {
		givenInitialBalances();
		// and:
		backingRels.getRef(rel(bModel, token)).setBalance(0L);

		// when:
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());
		subject.commit();

		// then:
		assertEquals(INSUFFICIENT_TOKEN_BALANCE, result);
		// and:
		assertInitialTokenBalanceUnchanged(0L);
	}

	@Test
	void happyPathScans() {
		givenInitialBalances();


		// when:
		TransferList inProgress;
		List<TokenTransferList> inProgressTokens;
		subject.begin();
		// and:
		final var result = subject.doZeroSum(fixtureChanges());
		inProgress = subject.netTransfersInTxn();
		inProgressTokens = subject.netTokenTransfersInTxn();
		// and:
		subject.commit();

		// then:
		assertEquals(OK, result);
		// and:
		assertEquals(
				aStartBalance + aHbarChange,
				backingAccounts.getImmutableRef(asGprcAccount(aModel)).getBalance());
		assertEquals(
				bStartBalance + bHbarChange,
				backingAccounts.getImmutableRef(asGprcAccount(bModel)).getBalance());
		assertEquals(
				cStartBalance + cHbarChange,
				backingAccounts.getImmutableRef(asGprcAccount(cModel)).getBalance());
		// and:
		assertEquals(
				bTokenStartBalance + bTokenChange,
				backingRels.getImmutableRef(rel(bModel, token)).getBalance());
		assertEquals(
				cTokenStartBalance + cTokenChange,
				backingRels.getImmutableRef(rel(cModel, token)).getBalance());
		// and:
		assertEquals(
				aAnotherTokenStartBalance + aAnotherTokenChange,
				backingRels.getImmutableRef(rel(aModel, anotherToken)).getBalance());
		assertEquals(
				bAnotherTokenStartBalance + bAnotherTokenChange,
				backingRels.getImmutableRef(rel(bModel, anotherToken)).getBalance());
		assertEquals(
				cAnotherTokenStartBalance + cAnotherTokenChange,
				backingRels.getImmutableRef(rel(cModel, anotherToken)).getBalance());
		// and:
		assertEquals(
				aYetAnotherTokenBalance + aYetAnotherTokenChange,
				backingRels.getImmutableRef(rel(aModel, yetAnotherToken)).getBalance());
		assertEquals(
				bYetAnotherTokenBalance + bYetAnotherTokenChange,
				backingRels.getImmutableRef(rel(bModel, yetAnotherToken)).getBalance());
		// and:
		assertEquals(expectedXfers(), inProgress);
		assertEquals(expectedTokenXfers(), inProgressTokens);
	}

	private TransferList expectedXfers() {
		return TransferList.newBuilder()
				.addAccountAmounts(aaBuilderWith(asGprcAccount(aModel), aHbarChange))
				.addAccountAmounts(aaBuilderWith(asGprcAccount(bModel), bHbarChange))
				.addAccountAmounts(aaBuilderWith(asGprcAccount(cModel), cHbarChange))
				.build();
	}

	private List<TokenTransferList> expectedTokenXfers() {
		return List.of(
				TokenTransferList.newBuilder()
						.setToken(asGprcToken(token))
						.addTransfers(aaBuilderWith(asGprcAccount(bModel), bTokenChange))
						.addTransfers(aaBuilderWith(asGprcAccount(cModel), cTokenChange))
						.build(),
				TokenTransferList.newBuilder()
						.setToken(asGprcToken(anotherToken))
						.addTransfers(aaBuilderWith(asGprcAccount(aModel), aAnotherTokenChange))
						.addTransfers(aaBuilderWith(asGprcAccount(bModel), bAnotherTokenChange))
						.addTransfers(aaBuilderWith(asGprcAccount(cModel), cAnotherTokenChange))
						.build(),
				TokenTransferList.newBuilder()
						.setToken(asGprcToken(yetAnotherToken))
						.addTransfers(aaBuilderWith(asGprcAccount(aModel), aYetAnotherTokenChange))
						.addTransfers(aaBuilderWith(asGprcAccount(bModel), bYetAnotherTokenChange))
						.build()
		);
	}

	private AccountAmount.Builder aaBuilderWith(AccountID account, long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
	}

	private void assertInitialTokenBalanceUnchanged(long modifiedBTokenBalance) {
		assertInitialBalanceUnchanged(aStartBalance, modifiedBTokenBalance);
	}

	private void assertInitialBalanceUnchanged() {
		assertInitialBalanceUnchanged(aStartBalance, bTokenStartBalance);
	}

	private void assertInitialBalanceUnchanged(long modifiedABalance) {
		assertInitialBalanceUnchanged(modifiedABalance, bTokenStartBalance);
	}

	private void assertInitialBalanceUnchanged(long modifiedABalance, long modifiedBTokenBalance) {
		if (modifiedABalance >= 0L) {
			assertEquals(
					modifiedABalance,
					backingAccounts.getImmutableRef(asGprcAccount(aModel)).getBalance());
		}
		assertEquals(
				bStartBalance,
				backingAccounts.getImmutableRef(asGprcAccount(bModel)).getBalance());
		assertEquals(
				cStartBalance,
				backingAccounts.getImmutableRef(asGprcAccount(cModel)).getBalance());
		// and:
		assertEquals(
				modifiedBTokenBalance,
				backingRels.getImmutableRef(rel(bModel, token)).getBalance());
		assertEquals(
				cTokenStartBalance,
				backingRels.getImmutableRef(rel(cModel, token)).getBalance());
		// and:
		assertEquals(
				aAnotherTokenStartBalance,
				backingRels.getImmutableRef(rel(aModel, anotherToken)).getBalance());
		assertEquals(
				bAnotherTokenStartBalance,
				backingRels.getImmutableRef(rel(bModel, anotherToken)).getBalance());
		assertEquals(
				cAnotherTokenStartBalance,
				backingRels.getImmutableRef(rel(cModel, anotherToken)).getBalance());
		// and:
		assertEquals(
				aYetAnotherTokenBalance,
				backingRels.getImmutableRef(rel(aModel, yetAnotherToken)).getBalance());
		assertEquals(
				bYetAnotherTokenBalance,
				backingRels.getImmutableRef(rel(bModel, yetAnotherToken)).getBalance());
	}


	private void givenInitialBalances() {
		final var aAccount = MerkleAccountFactory.newAccount().balance(aStartBalance).get();
		backingAccounts.put(asGprcAccount(aModel), aAccount);
		final var bAccount = MerkleAccountFactory.newAccount().balance(bStartBalance).get();
		backingAccounts.put(asGprcAccount(bModel), bAccount);
		final var cAccount = MerkleAccountFactory.newAccount().balance(cStartBalance).get();
		backingAccounts.put(asGprcAccount(cModel), cAccount);

		Pair<AccountID, TokenID> bTokenKey = rel(bModel, token);
		final var bTokenRel = new MerkleTokenRelStatus(bTokenStartBalance, false, true);
		backingRels.put(bTokenKey, bTokenRel);
		Pair<AccountID, TokenID> cTokenKey = rel(cModel, token);
		final var cTokenRel = new MerkleTokenRelStatus(cTokenStartBalance, false, true);
		backingRels.put(cTokenKey, cTokenRel);
		Pair<AccountID, TokenID> aAnotherTokenKey = rel(aModel, anotherToken);
		final var aAnotherTokenRel = new MerkleTokenRelStatus(aAnotherTokenStartBalance, false, true);
		backingRels.put(aAnotherTokenKey, aAnotherTokenRel);
		Pair<AccountID, TokenID> bAnotherTokenKey = rel(bModel, anotherToken);
		final var bAnotherTokenRel = new MerkleTokenRelStatus(bAnotherTokenStartBalance, false, true);
		backingRels.put(bAnotherTokenKey, bAnotherTokenRel);
		Pair<AccountID, TokenID> cAnotherTokenKey = rel(cModel, anotherToken);
		final var cAnotherTokenRel = new MerkleTokenRelStatus(cAnotherTokenStartBalance, false, true);
		backingRels.put(cAnotherTokenKey, cAnotherTokenRel);
		Pair<AccountID, TokenID> aYaTokenKey = rel(aModel, yetAnotherToken);
		final var aYaTokenRel = new MerkleTokenRelStatus(aYetAnotherTokenBalance, false, true);
		backingRels.put(aYaTokenKey, aYaTokenRel);
		Pair<AccountID, TokenID> bYaTokenKey = rel(bModel, yetAnotherToken);
		final var bYaTokenRel = new MerkleTokenRelStatus(bYetAnotherTokenBalance, false, true);
		backingRels.put(bYaTokenKey, bYaTokenRel);
	}

	private List<BalanceChange> fixtureChanges() {
		return List.of(new BalanceChange[] {
						BalanceChange.tokenAdjust(yetAnotherToken, aModel, aYetAnotherTokenChange),
						BalanceChange.hbarAdjust(aModel, aHbarChange),
						BalanceChange.hbarAdjust(bModel, bHbarChange),
						BalanceChange.tokenAdjust(anotherToken, aModel, aAnotherTokenChange),
						BalanceChange.tokenAdjust(anotherToken, cModel, cAnotherTokenChange),
						BalanceChange.hbarAdjust(cModel, cHbarChange),
						BalanceChange.tokenAdjust(token, bModel, bTokenChange),
						BalanceChange.tokenAdjust(token, cModel, cTokenChange),
						BalanceChange.tokenAdjust(anotherToken, bModel, bAnotherTokenChange),
						BalanceChange.tokenAdjust(yetAnotherToken, bModel, bYetAnotherTokenChange),
				}
		);
	}

	private Pair<AccountID, TokenID> rel(Id account, Id token) {
		return Pair.of(asGprcAccount(account), asGprcToken(token));
	}

	private AccountID asGprcAccount(Id id) {
		return AccountID.newBuilder()
				.setShardNum(id.getShard())
				.setRealmNum(id.getRealm())
				.setAccountNum(id.getNum())
				.build();
	}

	private TokenID asGprcToken(Id id) {
		return TokenID.newBuilder()
				.setShardNum(id.getShard())
				.setRealmNum(id.getRealm())
				.setTokenNum(id.getNum())
				.build();
	}

	private MerkleToken tokenWithTreasury(Id treasury) {
		final var token = new MerkleToken();
		token.setTreasury(new EntityId(treasury.getShard(), treasury.getRealm(), treasury.getNum()));
		return token;
	}

	private final Id aModel = new Id(1, 2, 3);
	private final Id bModel = new Id(2, 3, 4);
	private final Id cModel = new Id(3, 4, 5);
	private final Id token = new Id(0, 0, 75231);
	private final Id anotherToken = new Id(0, 0, 75232);
	private final Id yetAnotherToken = new Id(0, 0, 75233);
	private final MerkleEntityId tokenKey = new MerkleEntityId(0, 0, 75231);
	private final MerkleEntityId anotherTokenKey = new MerkleEntityId(0, 0, 75232);
	private final MerkleEntityId yetAnotherTokenKey = new MerkleEntityId(0, 0, 75233);

	private final long aStartBalance = 1_000L;
	private final long bStartBalance = 0L;
	private final long cStartBalance = 3_000L;
	private final long bTokenStartBalance = 123;
	private final long cTokenStartBalance = 234;
	private final long aAnotherTokenStartBalance = 345;
	private final long bAnotherTokenStartBalance = 456;
	private final long cAnotherTokenStartBalance = 567;
	private final long aYetAnotherTokenBalance = 678;
	private final long bYetAnotherTokenBalance = 789;
	private final long aHbarChange = -100L;
	private final long bHbarChange = +50L;
	private final long cHbarChange = +50L;
	private final long aAnotherTokenChange = -50L;
	private final long bAnotherTokenChange = +25L;
	private final long cAnotherTokenChange = +25L;
	private final long bTokenChange = -100L;
	private final long cTokenChange = +100L;
	private final long aYetAnotherTokenChange = -15L;
	private final long bYetAnotherTokenChange = +15L;
}