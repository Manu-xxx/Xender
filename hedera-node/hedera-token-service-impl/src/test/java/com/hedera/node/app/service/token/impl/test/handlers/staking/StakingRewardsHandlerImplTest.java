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

package com.hedera.node.app.service.token.impl.test.handlers.staking;

import static com.hedera.node.app.service.mono.utils.Units.HBARS_TO_TINYBARS;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtilities.roundedToHbar;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingUtilities.totalStake;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.ReadableAccountStoreImpl;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeInfoHelper;
import com.hedera.node.app.service.token.impl.handlers.staking.StakePeriodManager;
import com.hedera.node.app.service.token.impl.handlers.staking.StakeRewardCalculatorImpl;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsDistributor;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandlerImpl;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.ConfigProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakingRewardsHandlerImplTest extends CryptoTokenHandlerTestBase {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    private StakingRewardsHandlerImpl subject;
    private StakePeriodManager stakePeriodManager;
    private StakingRewardsDistributor rewardsPayer;
    private StakeInfoHelper stakeInfoHelper;
    private StakeRewardCalculatorImpl stakeRewardCalculator;
    private StakingRewardsHelper stakingRewardHelper;
    private final Long node0Id = 0L;
    private final Long node1Id = 1L;
    private final long stakingRewardAccountNum = 800L;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();

        given(configProvider.getConfiguration()).willReturn(versionedConfig);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        givenStoresAndConfig(handleContext);

        stakingRewardHelper = new StakingRewardsHelper();
        stakePeriodManager = new StakePeriodManager(configProvider);
        stakeRewardCalculator = new StakeRewardCalculatorImpl(stakePeriodManager);
        rewardsPayer = new StakingRewardsDistributor(stakingRewardHelper, stakeRewardCalculator);
        stakeInfoHelper = new StakeInfoHelper();
        subject = new StakingRewardsHandlerImpl(rewardsPayer, stakePeriodManager, stakeInfoHelper);
    }

    @Test
    void changingKeyOnlyIsNotRewardSituation() {
        final var stakedToMeBefore = account.stakedToMe();
        final var stakePeriodStartBefore = account.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodBefore = account.stakeAtStartOfLastRewardedPeriod();

        noStakeChanges();

        final var rewards = subject.applyStakingRewards(handleContext);

        assertThat(rewards).isEmpty();
        final var modifiedAccount = writableAccountStore.get(payerId);
        final var stakedToMeAfter = modifiedAccount.stakedToMe();
        final var stakePeriodStartAfter = modifiedAccount.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodAfter = modifiedAccount.stakeAtStartOfLastRewardedPeriod();

        assertThat(stakedToMeAfter).isEqualTo(stakedToMeBefore);
        assertThat(stakePeriodStartAfter).isEqualTo(stakePeriodStartBefore);
        assertThat(stakeAtStartOfLastRewardedPeriodAfter).isEqualTo(stakeAtStartOfLastRewardedPeriodBefore);
    }

    @Test
    void rewardsWhenStakingFieldsModified() {
        final var stakedToMeBefore = account.stakedToMe();
        final var stakePeriodStartBefore = account.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodBefore = account.stakeAtStartOfLastRewardedPeriod();

        randomStakeNodeChanges();

        final var rewards = subject.applyStakingRewards(handleContext);

        // earned zero rewards due to zero stake
        assertThat(rewards).hasSize(1).containsEntry(payerId, 0L);

        final var modifiedAccount = writableAccountStore.get(payerId);
        // stakedToMe will not change as this is not staked by another account
        final var stakedToMeAfter = modifiedAccount.stakedToMe();
        // These should change as staking is triggered
        final var stakePeriodStartAfter = modifiedAccount.stakePeriodStart();
        final var stakeAtStartOfLastRewardedPeriodAfter = modifiedAccount.stakeAtStartOfLastRewardedPeriod();

        final var expectedStakePeriodStart = stakePeriodManager.currentStakePeriod(consensusInstant);
        assertThat(stakedToMeAfter).isEqualTo(stakedToMeBefore);
        assertThat(stakePeriodStartAfter).isNotEqualTo(stakePeriodStartBefore).isEqualTo(expectedStakePeriodStart);
        assertThat(stakeAtStartOfLastRewardedPeriodAfter).isEqualTo(stakeAtStartOfLastRewardedPeriodBefore);
    }

    @Test
    void anAccountThatStartedStakingBeforeCurrentPeriodAndHasntBeenRewardedUnclaimsStakeWhenChangingElection() {
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(55L * HBARS_TO_TINYBARS)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withStakedToMe(0L)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .stakedNodeId(0L)
                .stakeAtStartOfLastRewardedPeriod(-1)
                .build());

        // We use next stake period to trigger rewards
        Instant nextDayInstant = LocalDate.ofEpochDay(stakePeriodStart + 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        given(handleContext.consensusNow()).willReturn(nextDayInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        subject.applyStakingRewards(handleContext);

        final var payerAfter = writableAccountStore.get(payerId);
        final var node1Info = writableStakingInfoState.get(node1Id);

        assertThat(payerAfter.tinybarBalance()).isEqualTo(node1Info.unclaimedStakeRewardStart());
    }

    @Test
    void anAccountThatStartedStakingBeforeCurrentPeriodAndWasRewardedDaysAgoUnclaimsStakeWhenChangingElection() {
        final var newBalance = 55L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(newBalance / 5)
                .withStakedNodeId(node1Id)
                .withStakedToMe(0)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(
                writableAccountStore.get(payerId).copyBuilder().stakedNodeId(0L).build());

        // We use next stake period to trigger rewards.
        Instant nextDayInstant = originalInstant.plus(2, ChronoUnit.DAYS);

        given(handleContext.consensusNow()).willReturn(nextDayInstant);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(node1Id);
        // Since the node is rewarded in last period the unclaimed reward will be stakeAtStartOfLastRewardPeriod.
        // But the stakePeriodSTart is not the previous period, so the unclaimed reward will be total stake of the node.
        assertThat(node1Info.unclaimedStakeRewardStart()).isEqualTo(newBalance);
    }

    @Test
    void anAccountThatStartedStakingBeforeCurrentPeriodAndWasRewardedTodayUnclaimsStakeStartWhenChangingElection() {
        final var newBalance = 55L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(newBalance / 5)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withStakedToMe(0L)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(
                writableAccountStore.get(payerId).copyBuilder().stakedNodeId(0L).build());

        // We use next stake period to trigger rewards
        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(node1Id);
        // Since the node is rewarded in last period and stakePeriodStart is the previous period
        // the unclaimed reward will be stakeAtStartOfLastRewardPeriod.
        assertThat(node1Info.unclaimedStakeRewardStart()).isEqualTo(newBalance / 5);
    }

    @Test
    void anAccountThatStartedStakingAtCurrentPeriodDoesntUnclaimStakeWhenChangingElection() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(account.copyBuilder().stakedNodeId(0L).build());

        given(handleContext.consensusNow()).willReturn(stakePeriodStartInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(node1Id);

        assertThat(node1Info.unclaimedStakeRewardStart()).isZero();
    }

    @Test
    void anAccountThatDeclineRewardsDoesntUnclaimStakeWhenChangingElection() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(true)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        // Change node, so to trigger rewards
        writableAccountStore.put(
                writableAccountStore.get(payerId).copyBuilder().stakedNodeId(0L).build());

        given(handleContext.consensusNow()).willReturn(originalInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(node1Id);

        assertThat(node1Info.unclaimedStakeRewardStart()).isZero();
    }

    //    @Test
    //    void anAutoCreatedAccountShouldNotHaveStakeStartUpdated() {
    //        final var newId = AccountID.newBuilder().accountNum(10000000000L).build();
    //        writableAccountStore.put(givenValidAccountBuilder().accountId(newId).build());
    //
    //        given(handleContext.consensusNow()).willReturn(stakePeriodStartInstant);
    //        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    //
    //
    //        subject.applyStakingRewards(handleContext);
    //
    //        assertThat(0).isEqualTo(writableAccountStore.get(newId).stakeAtStartOfLastRewardedPeriod());
    //    }

    @Test
    void earningZeroRewardsWithStartBeforeLastNonRewardableStillUpdatesSASOLARP() {
        final var account = mock(Account.class);
        final var manager = mock(StakePeriodManager.class);
        given(manager.firstNonRewardableStakePeriod(readableRewardsStore, consensusInstant))
                .willReturn(3L);
        given(account.stakePeriodStart()).willReturn(2L);

        final StakingRewardsHandlerImpl impl = new StakingRewardsHandlerImpl(rewardsPayer, manager, stakeInfoHelper);

        assertThat(impl.shouldUpdateStakeStart(account, true, 0L, readableRewardsStore, consensusInstant))
                .isTrue();
    }

    @Test
    void anAccountWithAlreadyCollectedRewardShouldNotHaveStakeStartUpdated() {
        final var newBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(newBalance)
                .withStakeAtStartOfLastRewardPeriod(newBalance - 1)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        writableAccountStore.put(writableAccountStore
                .get(payerId)
                .copyBuilder()
                .tinybarBalance(2 * newBalance)
                .build());

        given(handleContext.consensusNow()).willReturn(stakePeriodStartInstant);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        subject.applyStakingRewards(handleContext);

        final var node1Info = writableStakingInfoState.get(node1Id);

        assertThat(node1Info.unclaimedStakeRewardStart()).isZero();
    }

    @Test
    void calculatesRewardIfNeededStakingToNode() {
        final var accountBalance = 55L * HBARS_TO_TINYBARS;
        final var ownerBalance = 11L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withStakedToMe(0L)
                .withDeclineReward(false)
                .withStakedNodeId(node1Id)
                .withDeleted(false)
                .build();
        final var ownerAccountBefore = new AccountCustomizer()
                .withAccount(ownerAccount)
                .withBalance(ownerBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withStakedToMe(0L)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore, ownerId, ownerAccountBefore));

        final var node1InfoBefore = writableStakingInfoState.get(node1Id);
        final var node0InfoBefore = writableStakingInfoState.get(node0Id);

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .stakedNodeId(0L)
                .build());
        writableAccountStore.put(ownerAccount
                .copyBuilder()
                .tinybarBalance(ownerBalance + HBARS_TO_TINYBARS)
                .stakedNodeId(0L)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 2)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(node1Id);
        final var node0InfoAfter = writableStakingInfoState.get(node0Id);

        assertThat(rewards).hasSize(1).containsEntry(payerId, 5500L).doesNotContainKey(ownerId);

        assertThat(node1InfoAfter.stakeToReward()).isEqualTo(node1InfoBefore.stakeToReward() - accountBalance);

        final var modifiedPayer = writableAccountStore.get(payerId);
        final var modifiedOwner = writableAccountStore.get(ownerId);

        assertThat(node0InfoAfter.stakeToReward())
                .isEqualTo(node0InfoBefore.stakeToReward()
                        + roundedToHbar(totalStake(modifiedPayer) + roundedToHbar(totalStake(modifiedOwner))));

        assertThat(node1InfoAfter.unclaimedStakeRewardStart())
                .isEqualTo(node1InfoBefore.unclaimedStakeRewardStart() + accountBalance);

        assertThat(node1Info.unclaimedStakeRewardStart()).isZero();
    }

    @Test
    void doesNotAwardStakeFromDeletedAccount() {
        final var accountBalance = 555L * HBARS_TO_TINYBARS;
        final var ownerBalance = 111L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(true)
                .build();
        final var ownerAccountBefore = new AccountCustomizer()
                .withAccount(ownerAccount)
                .withBalance(ownerBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore, ownerId, ownerAccountBefore));

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .stakedNodeId(0L)
                .build());
        writableAccountStore.put(ownerAccount
                .copyBuilder()
                .tinybarBalance(ownerBalance + HBARS_TO_TINYBARS)
                .stakedNodeId(0L)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 2)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        // TODO: this will change once transfer to beneficiary is implemented
        assertThatThrownBy(() -> subject.applyStakingRewards(handleContext)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToNodeWithNoStakingMetaChanges() {
        final var accountBalance = 55L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withStakedToMe(0L)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));
        final var initialStakePeriodStart = payerAccountBefore.stakePeriodStart();

        final var node1InfoBefore = writableStakingInfoState.get(node1Id);

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 2)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());

        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(node1Id);

        assertThat(rewards).hasSize(1).containsEntry(payerId, 5500L);

        assertThat(node1InfoAfter.stake()).isEqualTo(node1InfoBefore.stake());
        assertThat(node1InfoAfter.unclaimedStakeRewardStart()).isEqualTo(node1InfoBefore.unclaimedStakeRewardStart());
        assertThat(node1Info.unclaimedStakeRewardStart()).isZero();

        final var modifiedAccount = writableAccountStore.get(payerId);
        assertThat(modifiedAccount.tinybarBalance())
                .isEqualTo(accountBalance - HBARS_TO_TINYBARS + rewards.get(payerId));
        assertThat(modifiedAccount.stakePeriodStart()).isNotEqualTo(initialStakePeriodStart);
        assertThat(modifiedAccount.stakePeriodStart()).isNotEqualTo(stakePeriodStart + 2);
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToNodeWithNoStakingMetaChangesAndNoReward() {
        final var accountBalance = 555L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));
        final var initialStakePeriodStart = payerAccountBefore.stakePeriodStart();

        final var node1InfoBefore = writableStakingInfoState.get(node1Id);

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(node1Id);

        // No rewards rewarded
        assertThat(rewards).hasSize(1).containsEntry(payerId, 0L);

        assertThat(node1InfoAfter.stake()).isEqualTo(node1InfoBefore.stake());
        assertThat(node1InfoAfter.unclaimedStakeRewardStart()).isEqualTo(node1InfoBefore.unclaimedStakeRewardStart());
        assertThat(node1Info.unclaimedStakeRewardStart()).isZero();

        final var modifiedAccount = writableAccountStore.get(payerId);
        assertThat(modifiedAccount.tinybarBalance()).isEqualTo(accountBalance - HBARS_TO_TINYBARS);
        assertThat(modifiedAccount.stakePeriodStart()).isEqualTo(initialStakePeriodStart);
    }

    @Test
    void sasolarpMgmtWorksAsExpectedWhenStakingToNodeWithNoStakingMetaChangesAndNoReward() {
        final var accountBalance = 55L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withStakedToMe(0L)
                .withStakedNodeId(node1Id)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore));

        final var initialStakePeriodStart = payerAccountBefore.stakePeriodStart();
        final var node1InfoBefore = writableStakingInfoState.get(node1Id);
        writableAccountStore.put(payerAccountBefore
                .copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());

        // No rewards rewarded
        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(node1Id);

        // Since it has not declined rewards and has zero stake, no rewards rewarded
        assertThat(rewards).hasSize(1).containsEntry(payerId, 0L);

        assertThat(node1InfoAfter.stake()).isEqualTo(node1InfoBefore.stake());
        assertThat(node1InfoAfter.unclaimedStakeRewardStart()).isEqualTo(node1InfoBefore.unclaimedStakeRewardStart());
        assertThat(node1Info.unclaimedStakeRewardStart()).isZero();

        final var modifiedAccount = writableAccountStore.get(payerId);
        assertThat(modifiedAccount.tinybarBalance()).isEqualTo(accountBalance - HBARS_TO_TINYBARS);
        assertThat(modifiedAccount.stakePeriodStart()).isEqualTo(stakePeriodStart);
        assertThat(modifiedAccount.stakeAtStartOfLastRewardedPeriod())
                .isEqualTo(roundedToHbar(totalStake(payerAccountBefore)));
    }

    @Test
    void stakingEffectsWorkAsExpectedWhenStakingToAccount() {
        final var accountBalance = 55L * HBARS_TO_TINYBARS;
        final var ownerBalance = 11L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withStakedToMe(0L)
                .withDeleted(false)
                .build();
        final var ownerAccountBefore = new AccountCustomizer()
                .withAccount(ownerAccount)
                .withBalance(ownerBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart)
                .withDeclineReward(false)
                .withStakedToMe(0L)
                .withDeleted(false)
                .build();
        addToState(Map.of(payerId, payerAccountBefore, ownerId, ownerAccountBefore));

        final var node1InfoBefore = writableStakingInfoState.get(node1Id);

        writableAccountStore.put(payerAccountBefore
                .copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .stakedAccountId(ownerId)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart + 2)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());

        final var rewards = subject.applyStakingRewards(handleContext);

        final var node1InfoAfter = writableStakingInfoState.get(node1Id);

        assertThat(rewards).hasSize(1).containsEntry(payerId, 5500L);

        assertThat(node1InfoAfter.stakeToReward()).isEqualTo(node1InfoBefore.stakeToReward() - accountBalance);
        assertThat(node1InfoAfter.unclaimedStakeRewardStart()).isEqualTo(accountBalance);
        // stake field of the account is updated once a day

        final var modifiedOwner = writableAccountStore.get(ownerId);
        final var modifiedPayer = writableAccountStore.get(payerId);

        assertThat(modifiedOwner.stakedToMe()).isEqualTo(accountBalance);
        // stakePeriodStart is updated only when reward is applied
        assertThat(modifiedOwner.stakePeriodStart()).isEqualTo(stakePeriodStart);

        assertThat(modifiedPayer.stakedToMe()).isEqualTo(payerAccountBefore.stakedToMe());
        // Only worthwhile to update stakedPeriodStart for an account staking to a node
        assertThat(modifiedPayer.stakePeriodStart()).isEqualTo(stakePeriodStart);
    }
    //
    //    @Test
    //    void rewardsUltimateBeneficiaryInsteadOfDeletedAccount() {
    //        TODO: Need to add this test once implemented
    //    }
    //
    //    @Test
    //    void doesntTrackAnythingIfRedirectBeneficiaryDeclinedReward() {
    //        TODO: Need to add this test once implemented
    //    }
    //
    //    @Test
    //    void failsHardIfMoreRedirectsThanDeletedEntitiesAreNeeded() {
    //        TODO: Need to add this test once implemented
    //    }
    //
    @Test
    void updatesStakedToMeSideEffects() {
        final var accountBalance = 55L * HBARS_TO_TINYBARS;
        final var ownerBalance = 11L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart - 2)
                .withDeclineReward(false)
                .withDeleted(false)
                .withStakedToMe(0L)
                .withStakedAccountId(ownerId)
                .build();
        final var ownerAccountBefore = new AccountCustomizer()
                .withAccount(ownerAccount)
                .withBalance(ownerBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart - 2)
                .withDeclineReward(false)
                .withDeleted(false)
                .withStakedNodeId(node0Id)
                .withStakedToMe(accountBalance)
                .build();
        addToState(Map.of(payerId, payerAccountBefore, ownerId, ownerAccountBefore));

        final var node0InfoBefore = writableStakingInfoState.get(node0Id);

        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(accountBalance - HBARS_TO_TINYBARS)
                .stakedAccountId(ownerId)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        final var originalPayer = writableAccountStore.get(payerId);
        final var rewards = subject.applyStakingRewards(handleContext);

        // even though only payer account has changed, since staked to me of owner changes,
        // it will trigger reward for owner
        assertThat(rewards).hasSize(1).containsEntry(ownerId, 6600L);

        final var modifiedPayer = writableAccountStore.get(payerId);
        final var modifiedOwner = writableAccountStore.get(ownerId);

        assertThat(modifiedOwner.stakedToMe()).isEqualTo(ownerAccountBefore.stakedToMe() - HBARS_TO_TINYBARS);
        // stakePeriodStart is updated everytime when reward is applied
        assertThat(modifiedOwner.stakePeriodStart()).isEqualTo(stakePeriodStart - 1);

        assertThat(modifiedPayer.stakedToMe()).isEqualTo(originalPayer.stakedToMe());
        assertThat(modifiedPayer.stakePeriodStart()).isEqualTo(stakePeriodStart);

        final var node0InfoAfter = writableStakingInfoStore.get(node0Id);
        assertThat(node0InfoAfter.stakeToReward()).isEqualTo(node0InfoBefore.stakeToReward() - HBARS_TO_TINYBARS);
        assertThat(node0InfoAfter.unclaimedStakeRewardStart()).isZero();
    }

    @Test
    void doesntUpdateStakedToMeIfStakerBalanceIsExactlyTheSame() {
        final var accountBalance = 55L * HBARS_TO_TINYBARS;
        final var ownerBalance = 11L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart - 2)
                .withDeclineReward(false)
                .withDeleted(false)
                .withStakedToMe(0L)
                .withStakedAccountId(ownerId)
                .build();
        final var ownerAccountBefore = new AccountCustomizer()
                .withAccount(ownerAccount)
                .withBalance(ownerBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart - 2)
                .withDeclineReward(false)
                .withDeleted(false)
                .withStakedNodeId(node0Id)
                .withStakedToMe(accountBalance)
                .build();
        addToState(Map.of(payerId, payerAccountBefore, ownerId, ownerAccountBefore));

        final var node0InfoBefore = writableStakingInfoState.get(node0Id);

        // Just change 800 balance
        writableAccountStore.put(stakingRewardAccount
                .copyBuilder()
                .tinybarBalance(stakingRewardAccount.tinybarBalance() + HBARS_TO_TINYBARS)
                .build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        final var originalPayer = writableAccountStore.get(payerId);

        // This should not change anything
        final var rewards = subject.applyStakingRewards(handleContext);

        // No rewards should be paid
        assertThat(rewards).isEmpty();

        // assert nothing changed in account and node
        final var modifiedPayer = writableAccountStore.get(payerId);
        final var modifiedOwner = writableAccountStore.get(ownerId);
        final var node0InfoAfter = writableStakingInfoStore.get(0L);

        assertThat(modifiedOwner.stakedToMe()).isEqualTo(ownerAccountBefore.stakedToMe());
        // stakePeriodStart is updated only when reward is applied
        assertThat(modifiedOwner.stakePeriodStart()).isEqualTo(stakePeriodStart - 2);

        assertThat(modifiedPayer.stakedToMe()).isEqualTo(originalPayer.stakedToMe());
        assertThat(modifiedPayer.stakePeriodStart()).isEqualTo(stakePeriodStart - 2);

        assertThat(node0InfoAfter.stakeToReward()).isEqualTo(node0InfoBefore.stakeToReward());
        assertThat(node0InfoAfter.unclaimedStakeRewardStart()).isZero();
    }

    @Test
    void stakePeriodStartUpdatedWhenStakedToAccount() {
        final var accountBalance = 55L * HBARS_TO_TINYBARS;
        final var ownerBalance = 11L * HBARS_TO_TINYBARS;
        final var payerAccountBefore = new AccountCustomizer()
                .withAccount(account)
                .withBalance(accountBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart - 2)
                .withDeclineReward(false)
                .withDeleted(false)
                .withStakedToMe(0L)
                .withStakedAccountId(ownerId)
                .build();
        final var ownerAccountBefore = new AccountCustomizer()
                .withAccount(ownerAccount)
                .withBalance(ownerBalance)
                .withStakeAtStartOfLastRewardPeriod(-1L)
                .withStakePeriodStart(stakePeriodStart - 2)
                .withDeclineReward(false)
                .withDeleted(false)
                .withStakedNodeId(node0Id)
                .withStakedToMe(accountBalance)
                .build();
        addToState(Map.of(payerId, payerAccountBefore, ownerId, ownerAccountBefore));

        writableAccountStore.put(
                account.copyBuilder().stakedAccountId(stakingRewardId).build());

        given(handleContext.consensusNow())
                .willReturn(LocalDate.ofEpochDay(stakePeriodStart)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        final var originalPayer = writableAccountStore.get(payerId);
        final var rewards = subject.applyStakingRewards(handleContext);

        assertThat(rewards).hasSize(1).containsEntry(ownerId, 6600L);

        final var modifiedPayer = writableAccountStore.get(payerId);
        final var modifiedOwner = writableAccountStore.get(ownerId);
        // Since payer is staked to reward account now, its balance should not add to stakedToMe of owner
        assertThat(modifiedOwner.stakedToMe()).isZero();
        // stakePeriodStart is updated everytime when reward is applied
        assertThat(modifiedOwner.stakePeriodStart()).isEqualTo(stakePeriodStart - 1);
        // stakePeriodStart is not updated here
        assertThat(modifiedPayer.stakedToMe()).isEqualTo(originalPayer.stakedToMe());
        assertThat(modifiedPayer.stakePeriodStart()).isEqualTo(stakePeriodStart);
    }

    private void randomStakeAccountChanges() {
        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(100L)
                .stakedAccountId(treasuryId)
                .declineReward(true)
                .build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void randomStakeNodeChanges() {
        writableAccountStore.put(account.copyBuilder()
                .tinybarBalance(100L)
                .stakedNodeId(0L)
                .declineReward(true)
                .build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void noStakeChanges() {
        writableAccountStore.put(account.copyBuilder().key(kycKey).build());
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    private void addToState(Map<AccountID, Account> idsToAccounts) {
        final var readableBuilder = emptyReadableAccountStateBuilder().value(stakingRewardId, stakingRewardAccount);
        final var writableBuilder = emptyWritableAccountStateBuilder().value(stakingRewardId, stakingRewardAccount);
        for (var entry : idsToAccounts.entrySet()) {
            readableBuilder.value(entry.getKey(), entry.getValue());
            writableBuilder.value(entry.getKey(), entry.getValue());
        }
        readableAccounts = readableBuilder.build();
        writableAccounts = writableBuilder.build();

        given(readableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(readableAccounts);
        readableAccountStore = new ReadableAccountStoreImpl(readableStates);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);

        given(writableStates.<AccountID, Account>get(ACCOUNTS)).willReturn(writableAccounts);
        writableAccountStore = new WritableAccountStore(writableStates);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
    }

    public static AccountCustomizer newBuilder() {
        return new AccountCustomizer();
    }

    /**
     * A builder for {@link Account} instances.
     */
    private static final class AccountCustomizer {
        private Account accountOfInterest;
        private Long amount;
        private Long stakeAtStartOfLastRewardPeriod;
        private Boolean declineReward;
        private Boolean deleted;
        private Long stakePeriodStart;
        private AccountID stakedAccountId;
        private Long stakedNodeId;
        private Long stakedToMe;

        private AccountCustomizer() {}

        public Account build() {
            final var copy = accountOfInterest.copyBuilder();
            if (amount != null) {
                copy.tinybarBalance(amount);
            }
            if (stakeAtStartOfLastRewardPeriod != null) {
                copy.stakeAtStartOfLastRewardedPeriod(stakeAtStartOfLastRewardPeriod);
            }
            if (declineReward != null) {
                copy.declineReward(declineReward);
            }
            if (deleted != null) {
                copy.deleted(deleted);
            }
            if (stakePeriodStart != null) {
                copy.stakePeriodStart(stakePeriodStart);
            }
            if (stakedAccountId != null) {
                copy.stakedAccountId(stakedAccountId);
            } else if (stakedNodeId != null) {
                copy.stakedNodeId(stakedNodeId);
            }
            if (stakedToMe != null) {
                copy.stakedToMe(stakedToMe);
            }
            return copy.build();
        }

        public AccountCustomizer withAccount(final Account accountOfInterest) {
            this.accountOfInterest = accountOfInterest;
            return this;
        }

        public AccountCustomizer withBalance(final Long amount) {
            this.amount = amount;
            return this;
        }

        public AccountCustomizer withStakeAtStartOfLastRewardPeriod(final Long stakeAtStartOfLastRewardPeriod) {
            this.stakeAtStartOfLastRewardPeriod = stakeAtStartOfLastRewardPeriod;
            return this;
        }

        public AccountCustomizer withDeclineReward(final Boolean declineReward) {
            this.declineReward = declineReward;
            return this;
        }

        public AccountCustomizer withDeleted(final Boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public AccountCustomizer withStakePeriodStart(final Long stakePeriodStart) {
            this.stakePeriodStart = stakePeriodStart;
            return this;
        }

        public AccountCustomizer withStakedAccountId(final AccountID id) {
            this.stakedAccountId = id;
            return this;
        }

        public AccountCustomizer withStakedNodeId(final Long id) {
            this.stakedNodeId = id;
            return this;
        }

        public AccountCustomizer withStakedToMe(final long stakedToMe) {
            this.stakedToMe = stakedToMe;
            return this;
        }
    }
}
