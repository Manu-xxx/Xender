/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.merkle.map.internal;

import com.swirlds.common.test.set.RandomAccessHashSet;
import com.swirlds.common.test.set.RandomAccessSet;
import com.swirlds.demo.merkle.map.FCMConfig;
import com.swirlds.demo.merkle.map.FCMFamily;
import com.swirlds.demo.platform.PAYLOAD_TYPE;
import com.swirlds.demo.platform.PayloadCfgSimple;
import com.swirlds.demo.platform.PayloadConfig;
import com.swirlds.demo.platform.nft.NftId;
import com.swirlds.merkle.map.test.lifecycle.EntityType;
import com.swirlds.merkle.map.test.lifecycle.ExpectedValue;
import com.swirlds.merkle.map.test.lifecycle.LifecycleStatus;
import com.swirlds.merkle.map.test.lifecycle.TransactionState;
import com.swirlds.merkle.map.test.lifecycle.TransactionType;
import com.swirlds.merkle.map.test.pta.MapKey;
import com.swirlds.merkle.map.test.pta.MapValue;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Implementation of {#link ExpectedFCMFamily} that doesn't keep track of available keys in the nodes and just
 * updates, transfers or deletes keys generated by the current node.
 *
 * Given its simplicity, there is no shared state between the Swirld State and the generation of transactions.
 * Hence, this implementation doesn't add any overhead to handleTransaction.
 *
 * This implementation is more suitable when troubleshooting issues related to memory leaks/footprint or an
 * increase in secTransH.
 */
public class DummyExpectedFCMFamily implements ExpectedFCMFamily {

    private static final Random RANDOM = new SecureRandom();

    private final long nodeId;
    private int count;
    private int limit;
    private Map<MapKey, ExpectedValue> map;
    private final transient RandomAccessSet<NftId> availableNfts;

    public DummyExpectedFCMFamily(final long nodeId) {
        this.nodeId = nodeId;
        this.count = 0;
        this.limit = 0;
        this.availableNfts = new RandomAccessHashSet<>();
    }

    @Override
    public void addEntityToExpectedMap(final MapKey mapKey, final ExpectedValue expectedValue) {}

    @Override
    public LifecycleStatus buildLifecycleStatusFromPayload(final byte[] payload, final PayloadConfig payloadConfig) {
        return null;
    }

    @Override
    public boolean entityHasBeenRemoved(final MapKey mapKey) {
        return false;
    }

    @Override
    public Map<MapKey, ExpectedValue> getExpectedMap() {
        if (this.map == null) {
            this.map = new HashMap<>();
        }

        return this.map;
    }

    public void setExpectedMap(final Map<MapKey, ExpectedValue> map) {
        this.map = map;
    }

    @Override
    public MapKey getMapKeyForFCMTx(
            final TransactionType txType,
            final EntityType entityType,
            final boolean performOnDeleted,
            final boolean operateEntitiesOfSameNode,
            final int restrictedCount) {
        final MapKey mapKey = new MapKey(nodeId, nodeId, count++);
        if (count >= limit) {
            count = 0;
        }

        return mapKey;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<NftId> getAnyNftId() {
        final NftId nftId = availableNfts.get(RANDOM);
        if (nftId == null) {
            return Optional.empty();
        }

        return Optional.of(nftId);
    }

    @Override
    public boolean doesTokenWithIdExist(final NftId id) {
        return availableNfts.contains(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNftId(final NftId nftId) {
        this.availableNfts.add(nftId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeNftid(final NftId nftId) {
        return this.availableNfts.remove(nftId);
    }

    @Override
    public long getNextIdToCreate() {
        return limit++;
    }

    @Override
    public boolean insertMissingEntity(
            final byte[] payload,
            final ExpectedFCMFamily expectedFCMFamily,
            final MapKey key,
            final PayloadConfig payloadConfig) {
        return false;
    }

    @Override
    public void rebuildExpectedMap(final FCMFamily fcmFamily, final boolean isRestart, final long timestamp) {}

    @Override
    public void setLatestHandledStatusForKey(
            final MapKey mapKey,
            final EntityType entityType,
            final MapValue mapValue,
            final TransactionState state,
            final TransactionType transactionType,
            final long timestamp,
            final long nodeId,
            final boolean error) {
        map.get(mapKey).setLatestHandledStatus(new LifecycleStatus(state, transactionType, timestamp, nodeId));
    }

    @Override
    public boolean shouldHandleForKeys(
            final List<MapKey> mapKeys,
            final TransactionType transactionType,
            final PayloadCfgSimple payloadCfgSimple,
            final EntityType entityType,
            final long epochMillis,
            final long originId) {
        return true;
    }

    @Override
    public void waitWhileExpectedMapEmpty(final EntityType entityType, final PAYLOAD_TYPE type) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public MapKey getMapKeyForQuery(final EntityType entityType) throws IllegalArgumentException {
        return new MapKey(nodeId, nodeId, count);
    }

    @Override
    public void setNodeId(final long nodeId) {}

    @Override
    public void setFcmConfig(final FCMConfig fcmConfig) {}

    @Override
    public void setWeightedNodeNum(final int weightedNodeNum) {}
}
