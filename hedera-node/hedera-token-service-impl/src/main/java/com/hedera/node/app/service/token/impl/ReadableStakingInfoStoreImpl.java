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

package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;

import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link ReadableStakingInfoStore}
 */
public class ReadableStakingInfoStoreImpl implements ReadableStakingInfoStore {

    /** The underlying data storage class that holds node staking data. */
    private final ReadableKVState<Long, StakingNodeInfo> stakingInfoState;
    /**
     * Create a new {@link ReadableStakingInfoStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableStakingInfoStoreImpl(@NonNull final ReadableStates states) {
        this.stakingInfoState = states.get(STAKING_INFO_KEY);
    }

    @Nullable
    @Override
    public StakingNodeInfo get(final long nodeId) {
        return stakingInfoState.get(nodeId);
    }

    public List<Long> getStakingNodeIds() {
        final var nodeIds = new ArrayList<Long>();
        final var keys = stakingInfoState.keys();
        while (keys.hasNext()) {
            nodeIds.add(keys.next());
        }
        return nodeIds;
    }
}
