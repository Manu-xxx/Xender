/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl;

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.TransactionMetadata;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;

import javax.annotation.Nonnull;
import java.util.Objects;
import static com.hedera.node.app.spi.state.StateKeys.ACCOUNT_STORE;
import static com.hedera.node.app.spi.state.StateKeys.ALIASES_STORE;
import static com.hedera.services.utils.EntityIdUtils.isAlias;

/**
 * Provides methods for interacting with the underlying data storage mechanisms for working with Accounts.
 * <p>
 * This class is not exported from the module. It is an internal implementation detail.
 */
public class AccountStore {
	/**
	 * The underlying data storage class that holds the account data.
	 */
	private final State<EntityNum, MerkleAccount> accountState;
	/**
	 * The underlying data storage class that holds the aliases data built from the state.
	 */
	private final State<ByteString, EntityNum> aliases;

	/**
	 * Create a new {@link AccountStore} instance.
	 *
	 * @param states The state to use.
	 */
	public AccountStore(@Nonnull States states) {
		this.accountState = states.get(ACCOUNT_STORE);
		this.aliases = states.get(ALIASES_STORE);
		Objects.requireNonNull(accountState);
		Objects.requireNonNull(aliases);
	}

	public TransactionMetadata createAccountSigningMetadata(final Transaction tx, final AccountID payer){
		final var payerNum = getAccountNum(payer);
		final var merkleAccount = getAccountLeaf(payerNum);
		final var key = merkleAccount.getAccountKey();
		return new TransactionMetadata(tx, false, key);
	}

	/**
	 * Returns the account leaf for the given account number. If the account doesn't
	 * exist throws {@link IllegalArgumentException}
	 * @param accountNumber given account number
	 * @return merkle leaf for the given account number
	 */
	private MerkleAccount getAccountLeaf(final EntityNum accountNumber) {
		final var account = accountState.get(accountNumber);
		if(!account.isPresent()){
			throw new IllegalArgumentException("Provided account doesn't exist");
		}
		return account.get();
	}

	/**
	 * Get account number if the provided account id is an alias. If not, returns the account's number
	 * @param id provided account id
	 * @return account number
	 */
	private EntityNum getAccountNum(final AccountID id){
		return isAlias(id) ? aliases.get(id.getAlias()).get() : EntityNum.fromLong(id.getAccountNum());
	}
}
