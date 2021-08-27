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

import com.google.common.base.Enums;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.fees.CustomFee;
import com.hedera.services.store.models.fees.FixedFee;
import com.hedera.services.store.models.fees.FractionalFee;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.validation.CustomFeeValidator;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.TokenTypesMapper;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.txns.validation.TokenListChecks.checkKeys;
import static com.hedera.services.txns.validation.TokenListChecks.suppliesCheck;
import static com.hedera.services.txns.validation.TokenListChecks.supplyTypeCheck;
import static com.hedera.services.txns.validation.TokenListChecks.typeCheck;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;

/**
 * Provides the state transition for token creation.
 */
@Singleton
public class TokenCreateTransitionLogic implements TransitionLogic {
	private final OptionValidator validator;
	private final TransactionContext txnCtx;
	private final GlobalDynamicProperties dynamicProperties;
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;
	private final EntityIdSource ids;
	private final TypedTokenStore typedTokenStore;
	private final AccountStore accountStore;

	@Inject
	public TokenCreateTransitionLogic(
			OptionValidator validator,
			TypedTokenStore typedTokenStore,
			AccountStore accountStore,
			TransactionContext txnCtx,
			GlobalDynamicProperties dynamicProperties,
			EntityIdSource ids
	) {
		this.validator = validator;
		this.txnCtx = txnCtx;
		this.dynamicProperties = dynamicProperties;
		this.ids = ids;
		this.accountStore = accountStore;
		this.typedTokenStore = typedTokenStore;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenCreation();
		final var treasuryGrpcId = op.getTreasury();
		validateExpiry(op);

		/* --- Load model objects --- */
		final var treasury = accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(treasuryGrpcId), INVALID_TREASURY_ACCOUNT_FOR_TOKEN);
		Account autoRenewModel = null;
		if (op.hasAutoRenewAccount()) {
			final var autoRenewGrpcId = op.getAutoRenewAccount();
			autoRenewModel = accountStore.loadAccountOrFailWith(Id.fromGrpcAccount(autoRenewGrpcId), INVALID_AUTORENEW_ACCOUNT);
		}

		/* --- Create the token --- */
		final var tokenId = ids.newTokenId(txnCtx.activePayer());
		final var id = Id.fromGrpcToken(tokenId);

		/* --- Validate and initialize custom fees list --- */
		validateFalse(op.getCustomFeesCount() > dynamicProperties.maxCustomFeesAllowed(), CUSTOM_FEES_LIST_TOO_LONG);
		final var customFeesList = new ArrayList<CustomFee>();
		for (final var grpcFee : op.getCustomFeesList()) {
			final var collectorId = Id.fromGrpcAccount(grpcFee.getFeeCollectorAccountId());
			final var collector = accountStore.loadAccountOrFailWith(collectorId, INVALID_CUSTOM_FEE_COLLECTOR);
			var fee = validateCustomFee(grpcFee, collector, Enums.getIfPresent(TokenType.class, op.getTokenType().name()).orNull(), id);
			customFeesList.add(fee);
		}

		final var created = Token.fromGrpcTokenCreate(id, op, treasury, autoRenewModel, customFeesList, txnCtx.consensusTime().getEpochSecond());
		final var relationsToPersist = updateAccountRelations(created, treasury, customFeesList);

		if (op.getInitialSupply() > 0) {
			created.mint(relationsToPersist.get(0), op.getInitialSupply(), true);
		}

		/* --- Persist anything modified/new --- */
		for (final var rel : relationsToPersist) {
			accountStore.persistAccount(rel.getAccount());
		}
		typedTokenStore.persistNew(created);
		typedTokenStore.persistTokenRelationships(relationsToPersist);
	}

	/**
	 * Associates the created token with the treasury and all the fee collectors.
	 * Note: the first returned {@link TokenRelationship} is always the treasury rel.
	 *
	 * @param created    the token created in the transition
	 * @param treasury   treasury account for the token
	 * @param customFees a list of valid custom fees to be applied
	 * @return list of formed relationships between the token and the account.
	 */
	private List<TokenRelationship> updateAccountRelations(Token created, Account treasury, List<CustomFee> customFees) {
		final var relations = new ArrayList<TokenRelationship>();
		final var associatedAccounts = new HashSet<Id>();

		final var treasuryRel = created.newEnabledRelationship(treasury);
		treasury.associateWith(List.of(created), dynamicProperties.maxTokensPerAccount());
		relations.add(treasuryRel);
		associatedAccounts.add(treasury.getId());

		for (var fee : customFees) {
			if (fee.shouldCollectorBeAutoAssociated()) {
				final var collector = fee.getCollector();
				if (!associatedAccounts.contains(collector.getId())) {
					final var collectorRelation = created.newEnabledRelationship(collector);
					if (!collector.getAssociatedTokens().contains(created.getId())) {
						collector.associateWith(List.of(created), dynamicProperties.maxTokensPerAccount());
					}
					relations.add(collectorRelation);
				}
			}
		}

		return relations;
	}

	private void validateExpiry(TokenCreateTransactionBody op) {
		validateFalse(op.hasExpiry() && !validator.isValidExpiry(op.getExpiry()), INVALID_EXPIRATION_TIME);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenCreation;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	@Override
	public void reclaimCreatedIds() {
		ids.reclaimProvisionalIds();
	}

	@Override
	public void resetCreatedIds() {
		ids.resetProvisionalIds();
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenCreateTransactionBody op = txnBody.getTokenCreation();

		if (TokenTypesMapper.grpcTokenTypeToModelType(op.getTokenType()) == TokenType.NON_FUNGIBLE_UNIQUE && !dynamicProperties.areNftsEnabled()) {
			return NOT_SUPPORTED;
		}

		var validity = validator.memoCheck(op.getMemo());
		if (validity != OK) {
			return validity;
		}

		validity = validator.tokenSymbolCheck(op.getSymbol());
		if (validity != OK) {
			return validity;
		}

		validity = validator.tokenNameCheck(op.getName());
		if (validity != OK) {
			return validity;
		}

		validity = typeCheck(op.getTokenType(), op.getInitialSupply(), op.getDecimals());
		if (validity != OK) {
			return validity;
		}

		validity = supplyTypeCheck(op.getSupplyType(), op.getMaxSupply());
		if (validity != OK) {
			return validity;
		}

		validity = suppliesCheck(op.getInitialSupply(), op.getMaxSupply());
		if (validity != OK) {
			return validity;
		}

		if (!op.hasTreasury()) {
			return INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
		}

		validity = checkKeys(
				op.hasAdminKey(), op.getAdminKey(),
				op.hasKycKey(), op.getKycKey(),
				op.hasWipeKey(), op.getWipeKey(),
				op.hasSupplyKey(), op.getSupplyKey(),
				op.hasFreezeKey(), op.getFreezeKey(),
				op.hasFeeScheduleKey(), op.getFeeScheduleKey());
		if (validity != OK) {
			return validity;
		}

		if (op.getFreezeDefault() && !op.hasFreezeKey()) {
			return TOKEN_HAS_NO_FREEZE_KEY;
		}
		return validateAutoRenewAccount(op);
	}

	private ResponseCodeEnum validateAutoRenewAccount(final TokenCreateTransactionBody op) {
		ResponseCodeEnum validity = OK;
		if (op.hasAutoRenewAccount()) {
			validity = validator.isValidAutoRenewPeriod(op.getAutoRenewPeriod()) ? OK : INVALID_RENEWAL_PERIOD;
			return validity;
		} else {
			if (op.getExpiry().getSeconds() <= txnCtx.consensusTime().getEpochSecond()) {
				return INVALID_EXPIRATION_TIME;
			}
		}
		return validity;
	}

	/**
	 * Validates custom fees, extracting them from the transaction body and treating
	 * each different grpc fee as a separate model - a {@link CustomFee}, which holds a
	 * {@link FractionalFee} or/and {@link FixedFee}.
	 * Fees are being validated at the moment of their instantiation.
	 *
	 * @param grpcFee       protobuf fee object
	 * @param collector     fee collector account
	 * @param tokenType     the type of token which is being created
	 * @param provisionalId the token which is being created
	 */
	private CustomFee validateCustomFee(com.hederahashgraph.api.proto.java.CustomFee grpcFee, Account collector, TokenType tokenType, Id provisionalId) {
		final var fee = CustomFee.fromGrpc(grpcFee, collector);

		if (grpcFee.hasFixedFee()) {
			if (grpcFee.getFixedFee().hasDenominatingTokenId()) {
				final var demomTokenId = grpcFee.getFixedFee().getDenominatingTokenId();
				if (demomTokenId.getTokenNum() != 0) {
					final var denominatingToken = typedTokenStore.loadTokenOrFailWith(
							Id.fromGrpcToken(demomTokenId),
							INVALID_TOKEN_ID_IN_CUSTOM_FEES);
					CustomFeeValidator.validateFixedFee(grpcFee, fee, denominatingToken, collector);
					CustomFeeValidator.initFixedFee(grpcFee, fee, denominatingToken.getId());
				}
			} else {
				CustomFeeValidator.validateFixedFee(grpcFee, fee, null, collector);
			}
		} else if (grpcFee.hasRoyaltyFee()) {
			final var grpcRoyaltyFee = grpcFee.getRoyaltyFee();

			if (grpcRoyaltyFee.getFallbackFee().hasDenominatingTokenId()) {
				final var fallbackDenominatingTokenId = grpcRoyaltyFee.getFallbackFee().getDenominatingTokenId();
				if (fallbackDenominatingTokenId.getTokenNum() != 0) {
					//Validate fallback denominating token
					typedTokenStore.loadTokenOrFailWith(
							Id.fromGrpcToken(fallbackDenominatingTokenId),
							INVALID_TOKEN_ID_IN_CUSTOM_FEES);
				}
				CustomFeeValidator.validateRoyaltyFee(grpcFee, tokenType, collector);
				initRoyaltyFee(grpcFee, fee, provisionalId);
			} else {
				CustomFeeValidator.validateRoyaltyFee(grpcFee, tokenType, collector);
				if (fee.getRoyaltyFee().getFallbackFee() != null) {
					fee.getRoyaltyFee().getFallbackFee().setDenominatingTokenId(null);
				}
			}
		}
		return fee;
	}

	/**
	 * Sets proper denominating token id
	 *
	 * @param grpcFee             protobuf fee object
	 * @param fee                 store model fee object
	 * @param denominationTokenId provisionalId
	 */
	private void initRoyaltyFee(
			com.hederahashgraph.api.proto.java.CustomFee grpcFee,
			CustomFee fee,
			Id denominationTokenId
	) {
		final var grpcRoyaltyFee = grpcFee.getRoyaltyFee();
		final var fallbackGrpc = grpcRoyaltyFee.getFallbackFee();
		if (fallbackGrpc.hasDenominatingTokenId()) {
			final var denomTokenId = fallbackGrpc.getDenominatingTokenId();
			if (denomTokenId.getTokenNum() != 0) {
				fee.getRoyaltyFee().getFallbackFee().setDenominatingTokenId(Id.fromGrpcToken(denomTokenId));
			} else {
				fee.getRoyaltyFee().getFallbackFee().setDenominatingTokenId(denominationTokenId);
			}
		} else {
			fee.getRoyaltyFee().getFallbackFee().setDenominatingTokenId(null);
		}
	}
}