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

import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.ledger.HederaLedger.TOKEN_ID_COMPARATOR;
import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.utils.EntityIdUtils.readableId;

/**
 * A store that provides efficient access to the mutable representations
 * of token relationships, indexed by ({@code AccountID}, {@code TokenID})
 * pairs. This class is <b>not</b> thread-safe, and should never be used
 * by any thread other than the {@code handleTransaction} thread.
 *
 * @author Michael Tinker
 */
public class BackingTokenRels implements BackingStore<Pair<AccountID, TokenID>, MerkleTokenRelStatus> {
	public static final Comparator<Pair<AccountID, TokenID>> REL_CMP =
			Comparator.<Pair<AccountID, TokenID>, AccountID>comparing(Pair::getLeft, ACCOUNT_ID_COMPARATOR)
					.thenComparing(Pair::getRight, TOKEN_ID_COMPARATOR);

	Set<Pair<AccountID, TokenID>> existingRels = new HashSet<>();
	Map<Pair<AccountID, TokenID>, MerkleTokenRelStatus> cache = new HashMap<>();

	private final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> delegate;

	public BackingTokenRels(Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> delegate) {
		this.delegate = delegate;
		rebuildFromSources();
	}

	@Override
	public void rebuildFromSources() {
		existingRels.clear();
		delegate.get().keySet().stream()
				.map(MerkleEntityAssociation::asAccountTokenRel)
				.forEach(existingRels::add);
	}

	@Override
	public void clearRefCache() {
		cache.clear();
	}

	@Override
	public boolean contains(Pair<AccountID, TokenID> key) {
		return existingRels.contains(key);
	}

	@Override
	public MerkleTokenRelStatus getRef(Pair<AccountID, TokenID> key) {
		return cache.computeIfAbsent(
				key,
				ignore -> delegate.get().getForModify(fromAccountTokenRel(key.getLeft(), key.getRight())));
	}

	@Override
	public void put(Pair<AccountID, TokenID> key, MerkleTokenRelStatus status) {
		if (!existingRels.contains(key)) {
			delegate.get().put(fromAccountTokenRel(key), status);
			existingRels.add(key);
		} else if (!cache.containsKey(key) || cache.get(key) != status) {
			throw new IllegalArgumentException(String.format(
					"Argument 'key=%s' does not map to a mutable ref!",
					fromAccountTokenRel(key).toAbbrevString()));
		}
	}

	@Override
	public void remove(Pair<AccountID, TokenID> id) {
		existingRels.remove(id);
		delegate.get().remove(fromAccountTokenRel(id));
	}

	@Override
	public MerkleTokenRelStatus getUnsafeRef(Pair<AccountID, TokenID> id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<Pair<AccountID, TokenID>> idSet() {
		throw new UnsupportedOperationException();
	}

	public static Pair<AccountID, TokenID> asTokenRel(AccountID account, TokenID token) {
		return Pair.of(account, token);
	}

	public static String readableTokenRel(Pair<AccountID, TokenID> rel) {
		return String.format("%s <-> %s", readableId(rel.getLeft()), readableId(rel.getRight()));
	}
}
