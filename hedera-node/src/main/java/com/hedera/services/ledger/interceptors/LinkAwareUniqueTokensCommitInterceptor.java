package com.hedera.services.ledger.interceptors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.ledger.CommitInterceptor;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;

import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;

/**
 * Placeholder for upcoming work.
 */
public class LinkAwareUniqueTokensCommitInterceptor implements CommitInterceptor<NftId, UniqueTokenValue, NftProperty> {
	private final UniqueTokensLinkManager uniqueTokensLinkManager;

	public LinkAwareUniqueTokensCommitInterceptor(final UniqueTokensLinkManager uniqueTokensLinkManager) {
		this.uniqueTokensLinkManager = uniqueTokensLinkManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void preview(final EntityChangeSet<NftId, UniqueTokenValue, NftProperty> pendingChanges) {
		final var n = pendingChanges.size();
		if (n == 0) {
			return;
		}
		for (int i = 0; i < n; i++) {
			final var entity = pendingChanges.entity(i);
			final var change = pendingChanges.changes(i);
			final var nftId = UniqueTokenKey.from(pendingChanges.id(i));

			if (entity != null) {
				final var fromAccount = entity.getOwner();
				if (change == null && !entity.getOwner().equals(MISSING_ENTITY_ID)) {
					uniqueTokensLinkManager.updateLinks(
							fromAccount.asNum(),
							null,
							nftId);
				} else if (change != null && change.containsKey(OWNER)) {
					final var toAccount = (EntityId) change.get(OWNER);
					uniqueTokensLinkManager.updateLinks(
							fromAccount.asNum(),
							toAccount.asNum(),
							nftId);
				}
			}
		}
	}
}
