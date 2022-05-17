package com.hedera.services.ledger.accounts.staking;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Supplier;

public class RewardCalculator {
	public static final EntityNum stakingFundAccount = EntityNum.fromLong(800L);
	private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
	private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo;

	public static final ZoneId zoneUTC = ZoneId.of("UTC");

	@Inject
	public RewardCalculator(final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
			final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfo) {
		this.accounts = accounts;
		this.stakingInfo = stakingInfo;
	}

	public final long computeAndApplyRewards(final EntityNum accountNum) {
		long todayNumber = LocalDate.now(zoneUTC).toEpochDay();
		final var account = accounts.get().getForModify(accountNum);
		final var stakePeriodStart = account.getStakePeriodStart();

		if (stakePeriodStart > -1 && stakePeriodStart < todayNumber - 365) {
			account.setStakePeriodStart(todayNumber - 365);
		}

		final var newStakePeriodStart = account.getStakePeriodStart();
		if (isWithinRange(newStakePeriodStart, todayNumber) && !noRewardToBeEarned(stakePeriodStart, todayNumber)) {
			final long reward = computeReward(account, account.getStakedId(), todayNumber);
			account.setStakePeriodStart(todayNumber - 1);
			return reward;
		}
		return 0;
	}

	private boolean isWithinRange(final long newStakePeriodStart, final long todayNumber) {
		return newStakePeriodStart > -1 && newStakePeriodStart < todayNumber - 1;
	}

	long computeReward(final MerkleAccount account, final long stakedNode, final long todayNumber) {
		final var stakedNodeAccount = stakingInfo.get().get(EntityNum.fromLong(stakedNode));
		final var rewardSumHistory = stakedNodeAccount.getRewardSumHistory();
		final var stakePeriodStart = account.getStakePeriodStart();
		// stakedNode.rewardSumHistory[0] is the reward for all days up to and including the full day todayNumber - 1,
		// since today is not finished yet.
		return account.isDeclinedReward() ? 0 :
				account.getBalance() * (rewardSumHistory[0] - rewardSumHistory[(int) (todayNumber - 1 - (stakePeriodStart - 1))]);
	}

	boolean noRewardToBeEarned(final long stakePeriodStart, final long todayNumber) {
		// if stakePeriodStart = -1 then it is not staked or staked to an account
		// If it equals todayNumber, that means the staking changed today (later than the start of today),
		// so it had no effect on consensus weights today, and should never be rewarded for helping consensus
		// throughout today.  If it equals todayNumber-1, that means it either started yesterday or has already been
		// rewarded for yesterday. Either way, it might be rewarded for today after today ends, but shouldn't yet be
		// rewarded for today, because today hasn't finished yet.
		return (stakePeriodStart == todayNumber - 365) || (stakePeriodStart == todayNumber - 1) || (stakePeriodStart == todayNumber);
	}
}
