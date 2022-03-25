package com.hedera.services.txns.crypto;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoRemoveAllowance;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenRemoveAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class CryptoDeleteAllowanceTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final DeleteAllowanceChecks allowanceChecks;
	private final Map<Long, Account> entitiesChanged;
	private final Set<UniqueToken> nftsTouched;

	@Inject
	public CryptoDeleteAllowanceTransitionLogic(
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final DeleteAllowanceChecks allowanceChecks,
			final TypedTokenStore tokenStore) {
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
		this.allowanceChecks = allowanceChecks;
		this.tokenStore = tokenStore;
		this.entitiesChanged = new HashMap<>();
		this.nftsTouched = new HashSet<>();
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		final TransactionBody cryptoDeleteAllowanceTxn = txnCtx.accessor().getTxn();
		final AccountID payer = cryptoDeleteAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
		entitiesChanged.clear();
		nftsTouched.clear();

		/* --- Use models --- */
		final Id payerId = Id.fromGrpcAccount(payer);
		final var payerAccount = accountStore.loadAccount(payerId);

		/* --- Do the business logic --- */
		deleteCryptoAllowances(op.getCryptoAllowancesList(), payerAccount);
		deleteFungibleTokenAllowances(op.getTokenAllowancesList(), payerAccount);
		deleteNftSerials(op.getNftAllowancesList());

		/* --- Persist the owner accounts and nfts --- */
		for (final var nft : nftsTouched) {
			tokenStore.persistNft(nft);
		}
		for (final var entry : entitiesChanged.entrySet()) {
			accountStore.commitAccount(entry.getValue());
		}

		txnCtx.setStatus(SUCCESS);
	}

	private void deleteNftSerials(final List<NftRemoveAllowance> nftAllowances) {
		if (nftAllowances.isEmpty()) {
			return;
		}

		final var nfts = new ArrayList<UniqueToken>();
		for (var allowance : nftAllowances) {
			final var serialNums = allowance.getSerialNumbersList();
			final var tokenId = Id.fromGrpcToken(allowance.getTokenId());

			for (var serial : serialNums) {
				final var token = tokenStore.loadUniqueToken(tokenId, serial);
				token.clearSpender();
				nfts.add(token);
			}
			nftsTouched.addAll(nfts);
			nfts.clear();
		}
	}

	private void deleteFungibleTokenAllowances(final List<TokenRemoveAllowance> tokenAllowances,
			final Account payerAccount) {
		if (tokenAllowances.isEmpty()) {
			return;
		}
		for (var allowance : tokenAllowances) {
			final var owner = allowance.getOwner();
			final var tokenId = allowance.getTokenId();
			final var accountToWipe = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			final var tokensMap = accountToWipe.getMutableFungibleTokenAllowances();

			for (Map.Entry<FcTokenAllowanceId, Long> e : tokensMap.entrySet()) {
				if (e.getKey().getTokenNum().longValue() == tokenId.getTokenNum()) {
					tokensMap.remove(e.getKey());
				}
			}
			entitiesChanged.put(accountToWipe.getId().num(), accountToWipe);
		}
	}

	private void deleteCryptoAllowances(final List<CryptoRemoveAllowance> cryptoAllowances,
			final Account payerAccount) {
		if (cryptoAllowances.isEmpty()) {
			return;
		}
		for (final var allowance : cryptoAllowances) {
			final var owner = allowance.getOwner();
			final var accountToWipe = fetchOwnerAccount(owner, payerAccount, accountStore, entitiesChanged);
			accountToWipe.getMutableCryptoAllowances().clear();
			entitiesChanged.put(accountToWipe.getId().num(), accountToWipe);
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoDeleteAllowance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoDeleteAllowanceTxn) {
		final AccountID payer = cryptoDeleteAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
		final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(payer));

		return allowanceChecks.deleteAllowancesValidation(
				op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(),
				op.getNftAllowancesList(),
				payerAccount);
	}
}
