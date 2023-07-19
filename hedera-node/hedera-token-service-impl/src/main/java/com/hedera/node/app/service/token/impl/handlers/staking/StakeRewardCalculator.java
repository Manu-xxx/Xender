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

package com.hedera.node.app.service.token.impl.handlers.staking;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.ReadableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

public interface StakeRewardCalculator {
    long computePendingReward(
            @NonNull final Account account,
            @NonNull final ReadableStakingInfoStore stakingInfoStore,
            @NonNull final ReadableNetworkStakingRewardsStore rewardsStore,
            @NonNull final Instant consensusNow);

    long estimatePendingRewards(
            @NonNull final Account account,
            @Nullable final StakingNodeInfo nodeStakingInfo,
            @NonNull final ReadableNetworkStakingRewardsStore rewardsStore);

    long epochSecondAtStartOfPeriod(final long stakePeriod);
}
