package com.hedera.services.state.migration;

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

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.utils.MiscUtils.forEach;

public class UniqueTokensMigrator {
	public static final int TARGET_RELEASE = StateVersions.RELEASE_0270_VERSION;

	private static final Logger log = LogManager.getLogger(UniqueTokensMigrator.class);

	/**
	 * Migrate tokens from MerkleMap data structure to VirtualMap data structure.
	 *
	 * @param initializingState the ServicesState containing the MerkleMap to migrate.
	 */
	public static void migrateFromUniqueTokenMerkleMap(final ServicesState initializingState) {
		final var virtualMapFactory = new VirtualMapFactory(JasperDbBuilder::new);
		final MerkleMap<EntityNumPair, MerkleUniqueToken> legacyUniqueTokens = initializingState.getChild(
				StateChildIndices.UNIQUE_TOKENS);
		final VirtualMap<UniqueTokenKey, UniqueTokenValue> vmUniqueTokens =
				virtualMapFactory.newVirtualizedUniqueTokenStorage();
		AtomicInteger count = new AtomicInteger();

		forEach(legacyUniqueTokens, (entityNumPair, legacyToken) -> {
			var numSerialPair = entityNumPair.asTokenNumAndSerialPair();
			var newTokenKey = new UniqueTokenKey(numSerialPair.getLeft(), numSerialPair.getRight());
			var newTokenValue = UniqueTokenValue.from(legacyToken);
			vmUniqueTokens.put(newTokenKey, newTokenValue);
			count.incrementAndGet();
		});

		initializingState.setChild(StateChildIndices.UNIQUE_TOKENS, vmUniqueTokens);
		log.info("Migrated {} unique tokens", count.get());
	}

	private UniqueTokensMigrator() { /* disallow construction */ }
}
