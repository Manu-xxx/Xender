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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.IterableStorageUtils;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.RandomExtended;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.state.migration.StateChildIndices.CONTRACT_STORAGE;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;

public class ReleaseTwentySixMigration {
	private static final Logger log = LogManager.getLogger(ReleaseTwentySixMigration.class);

	public static final int THREAD_COUNT = 32;
	public static final int INSERTIONS_PER_COPY = 100;
	public static final int SEVEN_DAYS_IN_SECONDS = 604800;
	private static final RandomExtended random = new RandomExtended(8682588012L);

	public static void makeStorageIterable(
			final ServicesState initializingState,
			final MigratorFactory migratorFactory,
			final MigrationUtility migrationUtility,
			final VirtualMap<ContractKey, IterableContractValue> iterableContractStorage
	) {
		final var contracts = initializingState.accounts();
		final VirtualMap<ContractKey, ContractValue> contractStorage = initializingState.getChild(CONTRACT_STORAGE);
		final var migrator = migratorFactory.from(
				INSERTIONS_PER_COPY, contracts, IterableStorageUtils::overwritingUpsertMapping, iterableContractStorage);
		try {
			log.info("Migrating contract storage into iterable VirtualMap with {} threads", THREAD_COUNT);
			final var watch = StopWatch.createStarted();
			migrationUtility.extractVirtualMapData(contractStorage, migrator, THREAD_COUNT);
			log.info("Done in {}ms", watch.getTime(TimeUnit.MILLISECONDS));
		} catch (InterruptedException e) {
			log.error("Interrupted while making contract storage iterable", e);
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
		migrator.finish();
		initializingState.setChild(CONTRACT_STORAGE, migrator.getMigratedStorage());
	}

	public static void grantFreeAutoRenew(
			final ServicesState initializingState,
			final Instant upgradeTime) {
		final var contracts = initializingState.accounts();

		log.info("Granting free auto renewal for all smart contracts by ~90 days.");
		final var watch = StopWatch.createStarted();
		contracts.forEach((id, account) -> {
			if (account.isSmartContract()) {
				setNewExpiry(upgradeTime, contracts, id, random);
			}
		});
		log.info("Done in {}ms", watch.getTime(TimeUnit.MILLISECONDS));
	}

	private static void setNewExpiry(
			final Instant upgradeTime,
			final MerkleMap<EntityNum, MerkleAccount> contracts,
			final EntityNum key,
			final RandomExtended rand) {
		final var account = contracts.getForModify(key);
		final var currentExpiry = account.getExpiry();
		final var newExpiry = Math.max(currentExpiry,
				upgradeTime.getEpochSecond()
						+ THREE_MONTHS_IN_SECONDS
						+ rand.nextLong(0, SEVEN_DAYS_IN_SECONDS));
		account.setExpiry(newExpiry);
	}

	@FunctionalInterface
	public interface MigratorFactory {
		KvPairIterationMigrator from(
				int insertionsPerCopy,
				MerkleMap<EntityNum, MerkleAccount> contracts,
				SizeLimitedStorage.IterableStorageUpserter storageUpserter,
				VirtualMap<ContractKey, IterableContractValue> iterableContractStorage);
	}

	@FunctionalInterface
	public interface MigrationUtility {
		void extractVirtualMapData(
				VirtualMap<ContractKey, ContractValue> source,
				InterruptableConsumer<Pair<ContractKey, ContractValue>> handler,
				int threadCount) throws InterruptedException;
	}

	private ReleaseTwentySixMigration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
