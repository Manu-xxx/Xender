/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.dispatcher;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStoreImpl;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.file.ReadableUpgradeStore;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.file.impl.ReadableUpgradeStoreImpl;
import com.hedera.node.app.service.networkadmin.FreezeService;
import com.hedera.node.app.service.networkadmin.NetworkService;
import com.hedera.node.app.service.networkadmin.ReadableRunningHashLeafStore;
import com.hedera.node.app.service.networkadmin.ReadableUpdateFileStore;
import com.hedera.node.app.service.networkadmin.impl.ReadableRunningHashLeafStoreImpl;
import com.hedera.node.app.service.networkadmin.impl.ReadableUpdateFileStoreImpl;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStoreImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableNetworkStakingRewardsStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableNftStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableStakingInfoStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenRelationStoreImpl;
import com.hedera.node.app.service.token.impl.ReadableTokenStoreImpl;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Factory for all readable stores. It creates new readable stores based on the {@link HederaState}.
 *
 * <p>The initial implementation creates all known stores hard-coded. In a future version, this will be replaced by a
 * dynamic approach.
 */
public class ReadableStoreFactory {

    // This is the hard-coded part that needs to be replaced by a dynamic approach later,
    // e.g. services have to register their stores
    private static final Map<Class<?>, StoreEntry> STORE_FACTORY = createFactoryMap();

    private static Map<Class<?>, StoreEntry> createFactoryMap() {
        Map<Class<?>, StoreEntry> newMap = new HashMap<>();
        // Tokens and accounts
        newMap.put(ReadableAccountStore.class, new StoreEntry(TokenService.NAME, ReadableAccountStoreImpl::new));
        newMap.put(ReadableNftStore.class, new StoreEntry(TokenService.NAME, ReadableNftStoreImpl::new));
        newMap.put(ReadableStakingInfoStore.class, new StoreEntry(TokenService.NAME, ReadableStakingInfoStoreImpl::new));
        newMap.put(ReadableTokenStore.class, new StoreEntry(TokenService.NAME, ReadableTokenStoreImpl::new));
        newMap.put(ReadableTokenRelationStore.class, new StoreEntry(TokenService.NAME, ReadableTokenRelationStoreImpl::new));
        newMap.put(ReadableNetworkStakingRewardsStore.class, new StoreEntry(TokenService.NAME, ReadableNetworkStakingRewardsStoreImpl::new));
        // Topics
        newMap.put(ReadableTopicStore.class, new StoreEntry(ConsensusService.NAME, ReadableTopicStoreImpl::new));
        // Schedules
        newMap.put(ReadableScheduleStore.class, new StoreEntry(ScheduleService.NAME, ReadableScheduleStoreImpl::new));
        // Files
        newMap.put(ReadableFileStore.class, new StoreEntry(FileService.NAME, ReadableFileStoreImpl::new));
        newMap.put(ReadableUpgradeStore.class, new StoreEntry(FileService.NAME, ReadableUpgradeStoreImpl::new));
        // Network Admin
        newMap.put(ReadableUpdateFileStore.class, new StoreEntry(FreezeService.NAME, ReadableUpdateFileStoreImpl::new));
        // Util
        newMap.put(
                ReadableRunningHashLeafStore.class,
                new StoreEntry(NetworkService.NAME, ReadableRunningHashLeafStoreImpl::new));
        return Collections.unmodifiableMap(newMap);
    }

    private final HederaState state;

    /**
     * Constructor of {@code ReadableStoreFactory}
     *
     * @param state the {@link HederaState} to use
     */
    public ReadableStoreFactory(@NonNull final HederaState state) {
        this.state = requireNonNull(state, "The supplied argument 'state' cannot be null!");
    }

    /**
     * Create a new store given the store's interface. This gives read-only access to the store.
     *
     * @param storeInterface The store interface to find and create a store for
     * @param <C> Interface class for a Store
     * @return An implementation of the provided store interface
     * @throws IllegalArgumentException if the storeInterface class provided is unknown to the app
     * @throws NullPointerException if {@code storeInterface} is {@code null}
     */
    @NonNull
    public <C> C getStore(@NonNull final Class<C> storeInterface) throws IllegalArgumentException {
        requireNonNull(storeInterface, "The supplied argument 'storeInterface' cannot be null!");
        final var entry = STORE_FACTORY.get(storeInterface);
        if (entry != null) {
            final var readableStates = state.createReadableStates(entry.name);
            final var store = entry.factory.apply(readableStates);
            assert storeInterface.isInstance(store); // This needs to be ensured while stores are registered
            return storeInterface.cast(store);
        }
        throw new IllegalArgumentException("No store of class " + storeInterface + " is available");
    }

    private record StoreEntry(@NonNull String name, @NonNull Function<ReadableStates, ?> factory) {}
}
