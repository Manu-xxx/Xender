package com.hedera.services.ledger.accounts;

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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.forEach;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Handles a map with all the accounts that are auto-created. The map will be re-built on restart, reconnect.
 * Entries from the map are removed when the entity expires
 */
@Singleton
public class AliasManager {
	private static final Logger log = LogManager.getLogger(AliasManager.class);

	private Map<ByteString, EntityNum> aliases;

	@Inject
	public AliasManager() {
		this.aliases = new HashMap<>();
	}

	public void link(final ByteString alias, final EntityNum num) {
		aliases.put(alias, num);
	}

	public void unlink(final ByteString alias) {
		aliases.remove(alias);
	}

	/**
	 * From given MerkleMap of accounts, populate the auto accounts creations map. Iterate through
	 * each account in accountsMap and add an entry to autoAccountsMap if {@code alias} exists on the account.
	 *
	 * @param accounts
	 * 		the current accounts
	 */
	public void rebuildAliasesMap(final MerkleMap<EntityNum, MerkleAccount> accounts) {
		aliases.clear();
		forEach(accounts, (k, v) -> {
			if (!v.getAlias().isEmpty()) {
				aliases.put(v.getAlias(), k);
			}
		});
		log.info("Rebuild complete : No.of accounts with aliases {} ", aliases.size());
	}

	/**
	 * Removes an entry from the autoAccountsMap when an entity is expired and deleted from the ledger.
	 *
	 * @param expiredId
	 * 		entity id that is expired
	 * @param accounts
	 * 		current accounts map
	 * @return whether the alias was forgotten
	 */
	public boolean forgetAliasIfPresent(final EntityNum expiredId, final MerkleMap<EntityNum, MerkleAccount> accounts) {
		final var alias = accounts.get(expiredId).getAlias();
		if (!alias.isEmpty()) {
			aliases.remove(alias);
			return true;
		}
		return false;
	}

	/**
	 * Returns if there is an account linked the given alias.
	 *
	 * @param alias
	 * 		the alias of interest
	 * @return whether there is a linked account
	 */
	public boolean contains(final ByteString alias) {
		return aliases.containsKey(alias);
	}

	/**
	 * Returns the entityNum for the given alias
	 *
	 * @param alias
	 * 		alias of the accountId
	 * @return EntityNum mapped to the given alias.
	 */
	public EntityNum lookupIdBy(final ByteString alias) {
		return aliases.getOrDefault(alias, MISSING_NUM);
	}

	/* --- Only used by unit tests --- */
	void setAliases(final Map<ByteString, EntityNum> aliases) {
		this.aliases = aliases;
	}

	Map<ByteString, EntityNum> getAliases() {
		return aliases;
	}


	/* --- Helpers for lookup --- */

	/**
	 * Look up aliased payer accountID and if it doesn't exist returns INVALID_PAYER_ACCOUNT_ID
	 *
	 * @param id
	 * 		payer accountID to look up
	 * @return result from alias look up
	 */
	public AliasLookup lookUpPayer(final AccountID id) {
		return lookUpAccount(id, INVALID_PAYER_ACCOUNT_ID);
	}

	/**
	 * Look up an aliased accountID and if it doesn't exist returns INVALID_ACCOUNT_ID
	 *
	 * @param id
	 * 		accountID to look up
	 * @return result from alias look up
	 */
	public AliasLookup lookUpAccount(final AccountID id) {
		return lookUpAccount(id, INVALID_ACCOUNT_ID);
	}

	/**
	 * Look up an aliased accountID and if it doesn't exist returns error response provided
	 *
	 * @param grpcId
	 * 		accountID to look up
	 * @param errResponse
	 * 		error response to be returned if the alias doesn't exist
	 * @return result from alias look up
	 */
	public AliasLookup lookUpAccount(final AccountID grpcId, final ResponseCodeEnum errResponse) {
		final var defaultResponse = OK;
		AccountID id = grpcId;

		if (isAlias(grpcId)) {
			var accountNum = lookupIdBy(grpcId.getAlias());
			if (accountNum == MISSING_NUM) {
				return AliasLookup.of(grpcId, errResponse);
			}
			id = accountNum.toGrpcAccountId();
		}

		return AliasLookup.of(id, defaultResponse);
	}
}
