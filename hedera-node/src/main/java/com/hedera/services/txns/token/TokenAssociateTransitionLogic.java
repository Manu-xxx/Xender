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
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.TransitionLogic;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.TokenListChecks.repeatsItself;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;

/**
 * Provides the state transition for associating tokens to an account.
 */
@Singleton
public class TokenAssociateTransitionLogic implements TransitionLogic {
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;


	private final TransactionContext txnCtx;
	private final AssociateLogic associateLogic;

	// TODO: Global dynamic properties are not being used in the transition logic itself and is being moved to the
	//  	 Associate logic. Confirm, that this solution is appropriate.
	@Inject
	public TokenAssociateTransitionLogic(final TransactionContext txnCtx, final AssociateLogic associateLogic) {
		this.txnCtx = txnCtx;
		this.associateLogic = associateLogic;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		final var op = txnCtx.accessor().getTxn().getTokenAssociate();
		final var grpcId = op.getAccount();
		final var accountId = new Id(grpcId.getShardNum(), grpcId.getRealmNum(), grpcId.getAccountNum());
		final var tokensList = op.getTokensList();

		associateLogic.associate(accountId, tokensList);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenAssociate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenAssociateTransactionBody op = txnBody.getTokenAssociate();

		if (!op.hasAccount()) {
			return INVALID_ACCOUNT_ID;
		}

		if (repeatsItself(op.getTokensList())) {
			return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
		}

		return OK;
	}
}
