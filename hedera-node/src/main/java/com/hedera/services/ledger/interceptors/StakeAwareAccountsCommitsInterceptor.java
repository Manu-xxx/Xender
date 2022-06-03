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

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.ledger.accounts.staking.StakeChangeManager;
import com.hedera.services.ledger.accounts.staking.StakeInfoManager;
import com.hedera.services.ledger.accounts.staking.StakePeriodManager;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalBalanceGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalDeclineRewardGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.finalStakedToMeGiven;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.hasStakeFieldChanges;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.roundedToHbar;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateBalance;
import static com.hedera.services.ledger.accounts.staking.StakingUtils.updateStakedToMe;
import static com.hedera.services.ledger.interceptors.StakeChangeScenario.FROM_ACCOUNT_TO_ACCOUNT;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;

public class StakeAwareAccountsCommitsInterceptor extends AccountsCommitInterceptor {
	private static final int INITIAL_CHANGE_CAPACITY = 32;
	private static final Logger log = LogManager.getLogger(StakeAwareAccountsCommitsInterceptor.class);

	private final StakeChangeManager stakeChangeManager;
	private final Supplier<MerkleNetworkContext> networkCtx;
	private final RewardCalculator rewardCalculator;
	private final SideEffectsTracker sideEffectsTracker;
	private final GlobalDynamicProperties dynamicProperties;
	private final StakePeriodManager stakePeriodManager;
	private final StakeInfoManager stakeInfoManager;
	private final AccountNumbers accountNumbers;

	// The current and new staked ids of the account being processed
	private long curStakedId;
	private long newStakedId;
	// If staking is not activated, the new balance of 0.0.800 after the changes
	private long newFundingBalance;
	// Whether the currently processed account has a stake metadata change
	private boolean stakeMetaChanged;
	// Whether rewards are active
	private boolean rewardsActivated;
	// The new stakedToMe values of accounts in the change set
	private long[] stakedToMeUpdates = new long[INITIAL_CHANGE_CAPACITY];
	// Whether each account in the change set has been rewarded yet
	private boolean[] hasBeenRewarded = new boolean[INITIAL_CHANGE_CAPACITY];
	// Whether each account in the change set has been rewarded yet
	private boolean[] wasStakeMetaChanged = new boolean[INITIAL_CHANGE_CAPACITY];
	// The stake change scenario for each account in the change set
	private StakeChangeScenario[] stakeChangeScenarios = new StakeChangeScenario[INITIAL_CHANGE_CAPACITY];
	// Function objects to be used by the ledger to apply final staking changes to a mutable account
	private Consumer<MerkleAccount>[] finishers = new Consumer[INITIAL_CHANGE_CAPACITY];

	public StakeAwareAccountsCommitsInterceptor(
			final SideEffectsTracker sideEffectsTracker,
			final Supplier<MerkleNetworkContext> networkCtx,
			final GlobalDynamicProperties dynamicProperties,
			final RewardCalculator rewardCalculator,
			final StakeChangeManager stakeChangeManager,
			final StakePeriodManager stakePeriodManager,
			final StakeInfoManager stakeInfoManager,
			final AccountNumbers accountNumbers
	) {
		super(sideEffectsTracker);
		this.networkCtx = networkCtx;
		this.accountNumbers = accountNumbers;
		this.stakeInfoManager = stakeInfoManager;
		this.rewardCalculator = rewardCalculator;
		this.dynamicProperties = dynamicProperties;
		this.stakePeriodManager = stakePeriodManager;
		this.stakeChangeManager = stakeChangeManager;
		this.sideEffectsTracker = sideEffectsTracker;
	}

	@Override
	public void preview(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		final var n = pendingChanges.size();
		if (n == 0) {
			return;
		}
		prepareAuxiliaryArraysFor(n);

		// Once rewards are activated, they remain activated
		rewardsActivated = rewardsActivated || networkCtx.get().areRewardsActivated();
		// Will only be updated and consulted if rewards are not active
		newFundingBalance = -1;

		// Iterates through the change set, maintaining two invariants:
		//   1. At the beginning of iteration i, any account in the [0, i) range that was reward-able due to
		//      a change in balance, stakedAccountId, stakedNodeId, or declineRewards fields has been rewarded.
		//      (IMPORTANT: this reward could be zero if the effective declineRewards is true.)
		//   2. Any account whose stakedToMe balance was affected by one or more changes in the [0, i) range
		//      has been, if not already present, added to the pendingChanges; and its updated stakedToMe is
		//      reflected in stakedToMeUpdates.
		updateRewardsAndElections(pendingChanges);
		// Updates node stakes and constructs any finishers the ledger will use to set stakedToMe and
		// stakePeriodStart fields on the mutable account instances
		finalizeStakeMetadata(pendingChanges);
		finalizeRewardBalance(pendingChanges);

		super.preview(pendingChanges);

		if (!rewardsActivated && newFundingBalance >= dynamicProperties.getStakingStartThreshold()) {
			activateStakingRewards();
		}
	}

	@Override
	public Consumer<MerkleAccount> finisherFor(int i) {
		return finishers[i];
	}

	private void updateRewardsAndElections(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var origN = pendingChanges.size();
		// We re-compute pendingChanges.size() in the for condition b/c stakeToMe side effects can increase it
		for (int i = 0; i < pendingChanges.size(); i++) {
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);
			stakeChangeScenarios[i] = scenarioFor(account, changes);

			if (!hasBeenRewarded[i] && isRewardSituation(account, stakedToMeUpdates[i], changes)) {
				payReward(i, account, changes);
				wasStakeMetaChanged[i] = true;
			} else if (!hasBeenRewarded[i]) {
				wasStakeMetaChanged[i] = stakeMetaChanged;
			}
			// If we are outside the original change set, this is a stakee account; and its stakedId cannot
			// have changed directly. Furthermore, its balance can only have changed via reward---but if so,
			// it must be staked to a node, and again staked-to-me side effects are impossible
			if (i < origN) {
				updateStakedToMeSideEffects(account, stakeChangeScenarios[i], changes, pendingChanges);
			}
			if (!rewardsActivated && pendingChanges.id(i).getAccountNum() == accountNumbers.stakingRewardAccount()) {
				newFundingBalance = finalBalanceGiven(account, changes);
			}
		}
	}

	private StakeChangeScenario scenarioFor(
			@Nullable final MerkleAccount account,
			@NotNull Map<AccountProperty, Object> changes
	) {
		setCurrentAndNewIds(account, changes);
		return StakeChangeScenario.forCase(curStakedId, newStakedId);
	}

	private void finalizeRewardBalance(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		final var rewardsPaid = rewardCalculator.rewardsPaidInThisTxn();
		if (rewardsPaid > 0) {
			final var fundingI = stakeChangeManager.findOrAdd(accountNumbers.stakingRewardAccount(), pendingChanges);
			updateBalance(-rewardsPaid, fundingI, pendingChanges);
			// No need to update newFundingBalance because if rewardsPaid > 0, rewards
			// are already activated, and we don't need to consult newFundingBalance
		}
	}

	private void finalizeStakeMetadata(final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges) {
		for (int i = 0, n = pendingChanges.size(); i < n; i++) {
			final var scenario = stakeChangeScenarios[i];
			final var account = pendingChanges.entity(i);
			final var changes = pendingChanges.changes(i);
			setCurrentAndNewIds(account, changes);
			// Because awardStake() and withdrawStake() are very fast, we don't worry about optimizing
			// the FROM_NODE_TO_NODE case with curStakedId == newStakedId, despite how common it is
			if (scenario.withdrawsFromNode()) {
				stakeChangeManager.withdrawStake(
						-curStakedId - 1,
						roundedToHbar(account.getBalance() + account.getStakedToMe()),
						account.isDeclinedReward());
			}
			if (scenario.awardsToNode()) {
				stakeChangeManager.awardStake(
						-newStakedId - 1,
						roundedToHbar(finalBalanceGiven(account, changes) + finalStakedToMeGiven(i, account, stakedToMeUpdates)),
						finalDeclineRewardGiven(account, changes));
			}
			// This will be null if the stake period manager determines there is no metadata to set
			finishers[i] = stakePeriodManager.finisherFor(
					curStakedId, newStakedId, stakedToMeUpdates[i], hasBeenRewarded[i], wasStakeMetaChanged[i]);
		}
	}

	@VisibleForTesting
	void updateStakedToMeSideEffects(
			final MerkleAccount account,
			final StakeChangeScenario scenario,
			final Map<AccountProperty, Object> changes,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		if (scenario == FROM_ACCOUNT_TO_ACCOUNT && curStakedId == newStakedId) {
			final var roundedFinalBalance = roundedToHbar(finalBalanceGiven(account, changes));
			final var roundedInitialBalance = roundedToHbar(account.getBalance());
			// Common case that deserves performance optimization
			final var delta = roundedFinalBalance - roundedInitialBalance;
			alterStakedToMe(curStakedId, delta, pendingChanges);
		} else {
			if (scenario.withdrawsFromAccount()) {
				final var roundedInitialBalance = roundedToHbar(account.getBalance());
				alterStakedToMe(curStakedId, -roundedInitialBalance, pendingChanges);
			}
			if (scenario.awardsToAccount()) {
				final var roundedFinalBalance = roundedToHbar(finalBalanceGiven(account, changes));
				alterStakedToMe(newStakedId, roundedFinalBalance, pendingChanges);
			}
		}
	}

	private void alterStakedToMe(
			final long accountNum,
			final long delta,
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges
	) {
		if (delta != 0) {
			final var stakeeI = stakeChangeManager.findOrAdd(accountNum, pendingChanges);
			updateStakedToMe(stakeeI, delta, stakedToMeUpdates, pendingChanges);
			if (!hasBeenRewarded[stakeeI]) {
				// If this stakee has already been previewed, and wasn't rewarded, we should
				// re-check if this stakedToMe change has now made it eligible for a reward
				payRewardIfRewardable(pendingChanges, stakeeI);
			}
		}
	}

	private void payRewardIfRewardable(
			final EntityChangeSet<AccountID, MerkleAccount, AccountProperty> pendingChanges,
			final int stakeeI
	) {
		final var account = pendingChanges.entity(stakeeI);
		final var changes = pendingChanges.changes(stakeeI);
		if (isRewardSituation(account, stakedToMeUpdates[stakeeI], changes)) {
			payReward(stakeeI, account, changes);
		}
		wasStakeMetaChanged[stakeeI] = stakeMetaChanged;
	}

	private void payReward(
			final int accountI,
			@NotNull final MerkleAccount account,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		final var reward = rewardCalculator.computeAndApplyReward(account, changes);
		sideEffectsTracker.trackRewardPayment(account.number(), reward);
		hasBeenRewarded[accountI] = true;
	}

	/**
	 * Checks if this is a <i>reward situation</i>, in the terminology of HIP-406; please see
	 * <a href="URL#value">https://hips.hedera.com/hip/hip-406</a> for details.
	 *
	 * @param account
	 * 		the account being checked
	 * @param stakedToMeUpdate
	 * 		its new stakedToMe field, or -1 if unchanged
	 * @param changes
	 * 		all pending user-controlled property changes
	 * @return true if this is a reward situation, false otherwise
	 */
	@VisibleForTesting
	boolean isRewardSituation(
			@Nullable final MerkleAccount account,
			final long stakedToMeUpdate,
			@NotNull final Map<AccountProperty, Object> changes
	) {
		stakeMetaChanged = (stakedToMeUpdate != -1 || hasStakeFieldChanges(changes));
		return account != null
				&& rewardsActivated
				&& account.getStakedId() < 0
				&& stakeMetaChanged
				&& stakePeriodManager.isRewardable(account.getStakePeriodStart());
	}

	/**
	 * Checks and activates staking rewards if the staking funding account balance reaches threshold
	 */
	private void activateStakingRewards() {
		long todayNumber = stakePeriodManager.currentStakePeriod();

		networkCtx.get().setStakingRewardsActivated(true);
		stakeInfoManager.clearRewardsHistory();
		stakeChangeManager.initializeAllStakingStartsTo(todayNumber);
		log.info("Staking rewards is activated and rewardSumHistory is cleared");
	}

	@SuppressWarnings("unchecked")
	private void prepareAuxiliaryArraysFor(final int n) {
		// Each pending change could potentially affect stakedToMe of two accounts not yet included in the
		// change set; and if rewards were paid without 0.0.800 in the change set, it will be included too
		final var maxImpliedChanges = 3 * n + 1;
		if (hasBeenRewarded.length < maxImpliedChanges) {
			hasBeenRewarded = new boolean[maxImpliedChanges];
			wasStakeMetaChanged = new boolean[maxImpliedChanges];
			stakedToMeUpdates = new long[maxImpliedChanges];
			stakeChangeScenarios = new StakeChangeScenario[maxImpliedChanges];
			finishers = new Consumer[maxImpliedChanges];
		}
		Arrays.fill(stakedToMeUpdates, -1);
		Arrays.fill(hasBeenRewarded, false);
		Arrays.fill(wasStakeMetaChanged, false);
		// The stakeChangeScenarios and finishers arrays are filled and used left-to-right only
	}

	private void setCurrentAndNewIds(
			@Nullable final MerkleAccount account,
			@NotNull Map<AccountProperty, Object> changes
	) {
		curStakedId = account == null ? 0L : account.getStakedId();
		newStakedId = (long) changes.getOrDefault(STAKED_ID, curStakedId);
	}

	/* only used for unit tests */
	@VisibleForTesting
	public void setRewardsActivated(final boolean rewardsActivated) {
		this.rewardsActivated = rewardsActivated;
	}

	@VisibleForTesting
	boolean[] getHasBeenRewarded() {
		return hasBeenRewarded;
	}

	@VisibleForTesting
	long[] getStakedToMeUpdates() {
		return stakedToMeUpdates;
	}

	@VisibleForTesting
	StakeChangeScenario[] getStakeChangeScenarios() {
		return stakeChangeScenarios;
	}

	@VisibleForTesting
	void setHasBeenRewarded(final boolean[] hasBeenRewarded) {
		this.hasBeenRewarded = hasBeenRewarded;
	}

	@VisibleForTesting
	void setCurStakedId(long curStakedId) {
		this.curStakedId = curStakedId;
	}

	@VisibleForTesting
	void setNewStakedId(long newStakedId) {
		this.newStakedId = newStakedId;
	}
}
