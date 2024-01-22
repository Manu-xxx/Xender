/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static com.hedera.node.app.service.token.api.AccountSummariesApi.SENTINEL_NODE_ID;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.ACCOUNT_AMOUNT_COMPARATOR;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtilities.hasStakeMetaChanges;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Helper class for staking reward calculations
 */
@Singleton
public class StakingRewardsHelper {
    private static final Logger log = LogManager.getLogger(StakingRewardsHelper.class);
    public static final long MAX_PENDING_REWARDS = 50_000_000_000L * HBARS_TO_TINYBARS;

    @Inject
    public StakingRewardsHelper() {
        // Exists for Dagger injection
    }

    /**
     * Looks through all the accounts modified in state and returns a list of accounts which are staked to a node
     * and has stakedId or stakedToMe or balance or declineReward changed in this transaction.
     *
     * @param writableAccountStore   The store to write to for updated values and original values
     * @param specialRewardReceivers The accounts which are staked to a node and are special reward receivers
     * @return A list of accounts which are staked to a node and could possibly receive a reward
     */
    public static Set<AccountID> getAllRewardReceivers(
            final WritableAccountStore writableAccountStore, final Set<AccountID> specialRewardReceivers) {
        final var possibleRewardReceivers = new LinkedHashSet<>(specialRewardReceivers);
        for (final AccountID id : writableAccountStore.modifiedAccountsInState()) {
            final var modifiedAcct = writableAccountStore.get(id);
            final var originalAcct = writableAccountStore.getOriginalValue(id);
            // It is possible that original account is null if the account was created in this transaction
            // In that case it is not a reward situation
            // If the account existed before this transaction and is staked to a node,
            // and the current transaction modified the stakedToMe field or declineReward or
            // the stakedId field, then it is a reward situation
            if (isRewardSituation(modifiedAcct, originalAcct)) {
                possibleRewardReceivers.add(id);
            }
        }
        return possibleRewardReceivers;
    }

    /**
     * Returns true if the account is staked to a node and the current transaction modified the stakedToMe field
     * (by changing balance of the current account or the account which is staking to current account) or
     * declineReward or the stakedId field
     * @param modifiedAccount the account which is modified in the current transaction and is in modifications
     * @param originalAccount the account before the current transaction
     * @return true if the account is staked to a node and the current transaction modified the stakedToMe field
     */
    private static boolean isRewardSituation(
            @NonNull final Account modifiedAccount, @Nullable final Account originalAccount) {
        requireNonNull(modifiedAccount);
        if (originalAccount == null || originalAccount.stakedNodeIdOrElse(SENTINEL_NODE_ID) == SENTINEL_NODE_ID) {
            return false;
        }

        // No need to check for stakeMetaChanges again here, since they are captured in possibleRewardReceivers
        // in previous step
        final var hasBalanceChange = modifiedAccount.tinybarBalance() != originalAccount.tinybarBalance();
        final var hasStakeMetaChanges = hasStakeMetaChanges(originalAccount, modifiedAccount);
        // We do this for backward compatibility with mono-service
        final var isCalledContract = modifiedAccount.smartContract();
        return (isCalledContract || hasBalanceChange || hasStakeMetaChanges);
    }

    /**
     * Decrease pending rewards on the network staking rewards store by the given amount.
     * Once we pay reward to an account, the pending rewards on the network should be
     * reduced by that amount, since they no more need to be paid.
     *
     * @param stakingInfoStore
     * @param stakingRewardsStore The store to write to for updated values
     * @param amount              The amount to decrease by
     * @param nodeId              The node id to decrease pending rewards for
     */
    public void decreasePendingRewardsBy(
            @NonNull final WritableStakingInfoStore stakingInfoStore,
            @NonNull final WritableNetworkStakingRewardsStore stakingRewardsStore,
            final long amount,
            @NonNull final Long nodeId) {
        // decrement the total pending rewards being tracked for the network
        final var currentPendingRewards = stakingRewardsStore.pendingRewards();
        var newPendingRewards = currentPendingRewards - amount;
        if (newPendingRewards < 0) {
            log.error(
                    "Pending rewards decreased by {} to a meaningless {}, fixing to zero hbar",
                    amount,
                    newPendingRewards);
            newPendingRewards = 0;
        }
        final var stakingRewards = stakingRewardsStore.get();
        final var copy = stakingRewards.copyBuilder();
        stakingRewardsStore.put(copy.pendingRewards(newPendingRewards).build());

        // decrement pendingRewards per node also
        final var stakingInfo = stakingInfoStore.get(nodeId);
        final var currentNodePendingRewards = stakingInfo.pendingRewards();
        var newNodePendingRewards = currentNodePendingRewards - amount;
        if (newNodePendingRewards < 0) {
            log.error(
                    "Pending rewards decreased by {} to a meaningless {}, fixing to zero hbar",
                    amount,
                    newNodePendingRewards);
            newNodePendingRewards = 0;
        }
        final var stakingInfoCopy =
                stakingInfo.copyBuilder().pendingRewards(newNodePendingRewards).build();
        stakingInfoStore.put(nodeId, stakingInfoCopy);
    }

    /**
     * Increase pending rewards on the network staking rewards store by the given amount.
     * This is called in EndOdStakingPeriod when we calculate the pending rewards on the network
     * to be paid in next staking period
     *
     * @param stakingRewardsStore The store to write to for updated values
     * @param amount              The amount to increase by
     * @param currStakingInfo    The current staking info
     * @return The clamped pending rewards
     */
    StakingNodeInfo increasePendingRewardsBy(
            final WritableNetworkStakingRewardsStore stakingRewardsStore,
            long amount,
            final StakingNodeInfo currStakingInfo) {
        // increment the total pending rewards being tracked for the network
        final var currentPendingRewards = stakingRewardsStore.pendingRewards();
        long nodePendingRewards = currStakingInfo.pendingRewards();
        long newNetworkPendingRewards;
        long newNodePendingRewards;
        if (!currStakingInfo.deleted()) {
            newNetworkPendingRewards = currentPendingRewards + amount;
            newNodePendingRewards = nodePendingRewards + amount;
        } else {
            newNetworkPendingRewards = currentPendingRewards;
            newNodePendingRewards = 0L;
        }
        if (newNetworkPendingRewards > MAX_PENDING_REWARDS) {
            log.error(
                    "Pending rewards increased by {} to an un-payable {}, fixing to 50B hbar",
                    amount,
                    newNetworkPendingRewards);
            newNetworkPendingRewards = MAX_PENDING_REWARDS;
        }
        final var stakingRewards = stakingRewardsStore.get();
        final var copy = stakingRewards.copyBuilder();
        stakingRewardsStore.put(copy.pendingRewards(newNetworkPendingRewards).build());

        // Update the individual node pending node rewards
        return currStakingInfo
                .copyBuilder()
                .pendingRewards(newNodePendingRewards)
                .build();
    }

    /**
     * Display the rewards paid map values as AccountAmounts
     * @param rewardsPaid The rewards paid
     * @return The rewards paid as AccountAmounts
     */
    public static List<AccountAmount> asAccountAmounts(@NonNull final Map<AccountID, Long> rewardsPaid) {
        final var accountAmounts = new ArrayList<AccountAmount>();
        for (final var entry : rewardsPaid.entrySet()) {
            accountAmounts.add(AccountAmount.newBuilder()
                    .accountID(entry.getKey())
                    .amount(entry.getValue())
                    .build());
        }
        accountAmounts.sort(ACCOUNT_AMOUNT_COMPARATOR);
        return accountAmounts;
    }
}
