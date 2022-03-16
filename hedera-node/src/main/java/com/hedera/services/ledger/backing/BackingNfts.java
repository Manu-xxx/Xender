package com.hedera.services.ledger.backing;

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

import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;
import com.swirlds.virtualmap.VirtualMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

@Singleton
public class BackingNfts implements BackingStore<NftId, UniqueTokenValue> {
	private final Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> delegate;

	@Inject
	public BackingNfts(Supplier<VirtualMap<UniqueTokenKey, UniqueTokenValue>> delegate) {
		this.delegate = delegate;
	}

	@Override
	public void rebuildFromSources() {
		/* No-op */
	}

	@Override
	public UniqueTokenValue getRef(NftId id) {
		return delegate.get().getForModify(UniqueTokenKey.fromNftId(id));
	}

	@Override
	public UniqueTokenValue getImmutableRef(NftId id) {
		return delegate.get().get(UniqueTokenKey.fromNftId(id));
	}

	@Override
	public void put(NftId id, UniqueTokenValue nft) {
		UniqueTokenKey key = UniqueTokenKey.fromNftId(id);
		if (!delegate.get().containsKey(key)) {
			delegate.get().put(key, nft);
		}
	}

	@Override
	public void remove(NftId id) {
		delegate.get().remove(UniqueTokenKey.fromNftId(id));
	}

	@Override
	public boolean contains(NftId id) {
		return delegate.get().containsKey(UniqueTokenKey.fromNftId(id));
	}

	@Override
	public Set<NftId> idSet() {
		// TODO: Blocked on issue #2994
		return new HashSet<>();
		/*
		return delegate.get()
				.stream()
				.map(EntityNumPair::asTokenNumAndSerialPair)
				.map(pair -> NftId.withDefaultShardRealm(pair.getLeft(), pair.getRight()))
				.collect(Collectors.toSet());
		 */
	}

	@Override
	public long size() {
		return delegate.get().size();
	}
}
