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

import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.txns.validation.TokenListChecks.repeatsItself;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;

@Singleton
public class DissociateLogic {
	private final OptionValidator validator;
	private final TypedTokenStore tokenStore;
	private final AccountStore accountStore;
	private final DissociationFactory dissociationFactory;

	@Inject
	public DissociateLogic(OptionValidator validator,
						   TypedTokenStore tokenStore,
						   AccountStore accountStore,
						   DissociationFactory dissociationFactory) {
		this.validator = validator;
		this.tokenStore = tokenStore;
		this.accountStore = accountStore;
		this.dissociationFactory = dissociationFactory;
	}

	public void dissociate(Id accountId, List<TokenID> tokenIDList) {
		/* --- Load the model objects --- */
		final var account = accountStore.loadAccount(accountId);
		final List<Dissociation> dissociations = new ArrayList<>();
		for (var tokenId : tokenIDList) {
			dissociations.add(dissociationFactory.loadFrom(tokenStore, account, Id.fromGrpcToken(tokenId)));
		}

		/* --- Do the business logic --- */
		final var touchedRels = account.dissociateUsing(dissociations, tokenStore, validator);

		/* --- Persist the updated models --- */
		accountStore.commitAccount(account);
		final List<TokenRelationship> allUpdatedRels = new ArrayList<>(touchedRels);
		for (var dissociation : dissociations) {
			dissociation.addUpdatedModelRelsTo(allUpdatedRels);
		}
		tokenStore.commitTokenRelationships(allUpdatedRels);
	}

	public ResponseCodeEnum validateSyntax(final TransactionBody txn) {
		TokenDissociateTransactionBody op = txn.getTokenDissociate();
		if (!op.hasAccount()) {
			return INVALID_ACCOUNT_ID;
		}
		if (repeatsItself(op.getTokensList())) {
			return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
		}
		return OK;
	}
}
