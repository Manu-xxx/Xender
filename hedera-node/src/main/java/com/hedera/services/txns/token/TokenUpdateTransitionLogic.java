package com.hedera.services.txns.token;

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
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.annotations.AreTreasuryWildcardsEnabled;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

/**
 * Provides the state transition for token updates.
 */
@Singleton
public class TokenUpdateTransitionLogic implements TransitionLogic {
	private static final Logger log = LogManager.getLogger(TokenUpdateTransitionLogic.class);

	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final boolean allowChangedTreasuryToOwnNfts;
	private final TokenStore tokenStore;
	private final HederaLedger ledger;
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final SigImpactHistorian sigImpactHistorian;
	private final Predicate<TokenUpdateTransactionBody> affectsExpiryOnly;

	@Inject
	public TokenUpdateTransitionLogic(
			final @AreTreasuryWildcardsEnabled boolean allowChangedTreasuryToOwnNfts,
			final OptionValidator validator,
			final TokenStore tokenStore,
			final HederaLedger ledger,
			final TransactionContext txnCtx,
			final SigImpactHistorian sigImpactHistorian,
			final Predicate<TokenUpdateTransactionBody> affectsExpiryOnly
	) {
		this.validator = validator;
		this.tokenStore = tokenStore;
		this.ledger = ledger;
		this.txnCtx = txnCtx;
		this.affectsExpiryOnly = affectsExpiryOnly;
		this.sigImpactHistorian = sigImpactHistorian;
		this.allowChangedTreasuryToOwnNfts = allowChangedTreasuryToOwnNfts;
	}

	@Override
	public void doStateTransition() {
		try {
			transitionFor(txnCtx.accessor().getTxn().getTokenUpdate());
		} catch (Exception e) {
			log.warn("Unhandled error while processing :: {}!", txnCtx.accessor().getSignedTxnWrapper(), e);
			abortWith(FAIL_INVALID);
		}
	}

	private void transitionFor(TokenUpdateTransactionBody op) {
		var id = tokenStore.resolve(op.getToken());
		if (id == MISSING_TOKEN) {
			txnCtx.setStatus(INVALID_TOKEN_ID);
			return;
		}

		var outcome = OK;
		MerkleToken token = tokenStore.get(id);

		if (op.hasExpiry() && !validator.isValidExpiry(op.getExpiry())) {
			txnCtx.setStatus(INVALID_EXPIRATION_TIME);
			return;
		}

		if (token.adminKey().isEmpty() && !affectsExpiryOnly.test(op)) {
			txnCtx.setStatus(TOKEN_IS_IMMUTABLE);
			return;
		}

		if (token.isDeleted()) {
			txnCtx.setStatus(TOKEN_WAS_DELETED);
			return;
		}

		if (token.isPaused()) {
			txnCtx.setStatus(TOKEN_IS_PAUSED);
			return;
		}

		outcome = autoRenewAttachmentCheck(op, token);
		if (outcome != OK) {
			txnCtx.setStatus(outcome);
			return;
		}

		AccountID newTreasury = null;
		Optional<AccountID> replacedTreasury = Optional.empty();
		if (op.hasTreasury()) {
			final var newTreasuryLookUp = tokenStore.lookUpAccountIdAndValidate(op.getTreasury(), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
			if (newTreasuryLookUp.response() != OK) {
				txnCtx.setStatus(newTreasuryLookUp.response());
				return;
			}
			if (!tokenStore.associationExists(newTreasuryLookUp.resolvedId(), id)) {
				txnCtx.setStatus(INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
				return;
			}
			final var existingTreasury = token.treasury().toGrpcAccountId();
			if (!allowChangedTreasuryToOwnNfts && token.tokenType() == NON_FUNGIBLE_UNIQUE) {
				var existingTreasuryBalance = ledger.getTokenBalance(existingTreasury, id);
				if (existingTreasuryBalance > 0L) {
					abortWith(CURRENT_TREASURY_STILL_OWNS_NFTS);
					return;
				}
			}
			if (!newTreasuryLookUp.resolvedId().equals(existingTreasury)) {
				if (ledger.isDetached(existingTreasury)) {
					txnCtx.setStatus(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
					return;
				}
				outcome = prepNewTreasury(id, token, newTreasuryLookUp.resolvedId());
				if (outcome != OK) {
					abortWith(outcome);
					return;
				}
				replacedTreasury = Optional.of(token.treasury().toGrpcAccountId());
				newTreasury = newTreasuryLookUp.resolvedId();
			}
		}

		outcome = tokenStore.update(op, txnCtx.consensusTime().getEpochSecond());
		if (outcome == OK && replacedTreasury.isPresent()) {
			final var oldTreasury = replacedTreasury.get();
			long replacedTreasuryBalance = ledger.getTokenBalance(oldTreasury, id);
			if (replacedTreasuryBalance > 0) {
				if (token.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
					outcome = ledger.doTokenTransfer(
							id,
							oldTreasury,
							newTreasury,
							replacedTreasuryBalance);
				} else {
					outcome = tokenStore.changeOwnerWildCard(
							new NftId(id.getShardNum(), id.getRealmNum(), id.getTokenNum(), -1), oldTreasury,
							newTreasury);
				}
			}
		}
		if (outcome != OK) {
			abortWith(outcome);
			return;
		}

		txnCtx.setStatus(SUCCESS);
		sigImpactHistorian.markEntityChanged(id.getTokenNum());
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenUpdate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenUpdateTransactionBody op = txnBody.getTokenUpdate();

		if (!op.hasToken()) {
			return INVALID_TOKEN_ID;
		}

		var validity = !op.hasMemo() ? OK : validator.memoCheck(op.getMemo().getValue());
		if (validity != OK) {
			return validity;
		}


		var hasNewSymbol = op.getSymbol().length() > 0;
		if (hasNewSymbol) {
			validity = validator.tokenSymbolCheck(op.getSymbol());
			if (validity != OK) {
				return validity;
			}
		}

		var hasNewTokenName = op.getName().length() > 0;
		if (hasNewTokenName) {
			validity = validator.tokenNameCheck(op.getName());
			if (validity != OK) {
				return validity;
			}
		}

		validity = checkKeys(
				op.hasAdminKey(), op.getAdminKey(),
				op.hasKycKey(), op.getKycKey(),
				op.hasWipeKey(), op.getWipeKey(),
				op.hasSupplyKey(), op.getSupplyKey(),
				op.hasFreezeKey(), op.getFreezeKey(),
				op.hasFeeScheduleKey(), op.getFeeScheduleKey(),
				op.hasPauseKey(), op.getPauseKey());
		if (validity != OK) {
			return validity;
		}

		return validity;
	}

	private ResponseCodeEnum autoRenewAttachmentCheck(TokenUpdateTransactionBody op, MerkleToken token) {
		if (op.hasAutoRenewAccount()) {
			final var newAutoRenewAccountLookUp = tokenStore.lookUpAccountIdAndValidate(
					op.getAutoRenewAccount(), INVALID_AUTORENEW_ACCOUNT);
			if (newAutoRenewAccountLookUp.response() != OK) {
				return newAutoRenewAccountLookUp.response();
			}

			if (token.hasAutoRenewAccount()) {
				final var existingAutoRenew = token.autoRenewAccount().toGrpcAccountId();
				if (ledger.isDetached(existingAutoRenew)) {
					return ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
				}
			}
		}
		return OK;
	}

	private ResponseCodeEnum prepNewTreasury(TokenID id, MerkleToken token, AccountID newTreasury) {
		var status = OK;
		if (token.hasFreezeKey()) {
			status = ledger.unfreeze(newTreasury, id);
		}
		if (status == OK && token.hasKycKey()) {
			status = ledger.grantKyc(newTreasury, id);
		}
		return status;
	}

	private void abortWith(ResponseCodeEnum cause) {
		ledger.dropPendingTokenChanges();
		txnCtx.setStatus(cause);
	}
}
