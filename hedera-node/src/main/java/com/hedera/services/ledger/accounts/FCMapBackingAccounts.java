package com.hedera.services.ledger.accounts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.swirlds.fcmap.FCMap;

import java.util.Collections;
import java.util.Set;

import static com.hedera.services.state.merkle.MerkleEntityId.fromPojoAccountId;

public class FCMapBackingAccounts implements BackingAccounts<AccountID, MerkleAccount> {
	static final Set<AccountID> NO_KNOWN_ACCOUNTS = Collections.emptySet();

	Set<AccountID> knownAccounts = NO_KNOWN_ACCOUNTS;
	private final FCMap<MerkleEntityId, MerkleAccount> delegate;

	public FCMapBackingAccounts(FCMap<MerkleEntityId, MerkleAccount> delegate) {
		this.delegate = delegate;
	}

	public FCMapBackingAccounts(
			Set<AccountID> knownAccounts,
			FCMap<MerkleEntityId, MerkleAccount> delegate
	) {
		this.delegate = delegate;
		this.knownAccounts = knownAccounts;
		sync(delegate, knownAccounts);
	}

	public void syncKnownAccounts() {
		sync(delegate, knownAccounts);
	}

	static void sync(FCMap<MerkleEntityId, MerkleAccount> source, Set<AccountID> dest) {
		source.keySet().stream()
				.map(id -> AccountID.newBuilder()
						.setShardNum(id.getShard())
						.setRealmNum(id.getRealm())
						.setAccountNum(id.getNum())
						.build())
				.forEach(dest::add);
	}

	@Override
	public MerkleAccount getUnsafeRef(AccountID id) {
		return delegate.get(fromPojoAccountId(id));
	}

	@Override
	public MerkleAccount getMutableRef(AccountID id) {
		return delegate.getForModify(fromPojoAccountId(id));
	}

	@Override
	public void replace(AccountID id, MerkleAccount account) {
		MerkleEntityId delegateId = fromPojoAccountId(id);
		if (!contains(id)) {
			delegate.put(delegateId, account);
		} else {
			delegate.replace(delegateId, account);
		}
	}

	@Override
	public boolean contains(AccountID id) {
		return knownAccounts == NO_KNOWN_ACCOUNTS
				? delegate.containsKey(fromPojoAccountId(id))
				: knownAccounts.contains(id);
	}

	@Override
	public void remove(AccountID id) {
		delegate.remove(fromPojoAccountId(id));
	}
}
