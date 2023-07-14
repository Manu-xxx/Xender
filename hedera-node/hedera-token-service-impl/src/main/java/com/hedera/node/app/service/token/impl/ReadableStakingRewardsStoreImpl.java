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

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_REWARDS_KEY;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.StakingRewards;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.ReadableStakingRewardsStore;
import com.hedera.node.app.spi.state.ReadableSingletonState;
import com.hedera.node.app.spi.state.ReadableStates;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Default implementation of {@link ReadableStakingInfoStore}
 */
public class ReadableStakingRewardsStoreImpl implements ReadableStakingRewardsStore {

    /** The underlying data storage class that holds staking reward data for all nodes. */
    private final ReadableSingletonState<StakingRewards> stakingRewardsState;
    /**
     * Create a new {@link ReadableStakingRewardsStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableStakingRewardsStoreImpl(@NonNull final ReadableStates states) {
        this.stakingRewardsState = requireNonNull(states).getSingleton(STAKING_REWARDS_KEY);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isStakingRewardsActivated() {
        return requireNonNull(stakingRewardsState.get()).stakingRewardsActivated();
    }

    /** {@inheritDoc} */
    @Override
    public long getTotalStakeRewardStart() {
        return requireNonNull(stakingRewardsState.get()).totalStakedRewardStart();
    }

    /** {@inheritDoc} */
    @Override
    public long getTotalStakedStart() {
        return requireNonNull(stakingRewardsState.get()).totalStakedStart();
    }

    /** {@inheritDoc} */
    @Override
    public long pendingRewards() {
        return requireNonNull(stakingRewardsState.get()).pendingRewards();
    }
}
