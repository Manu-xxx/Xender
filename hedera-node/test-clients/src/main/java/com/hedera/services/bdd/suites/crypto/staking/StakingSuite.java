/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.crypto.staking;

import static com.hedera.services.bdd.junit.ContextRequirement.NO_CONCURRENT_STAKE_PERIOD_BOUNDARY_CROSSINGS;
import static com.hedera.services.bdd.junit.TestTags.LONG_RUNNING;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilJustBeforeNextStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.NODE;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.STAKING_REWARD;
import static com.hedera.services.bdd.suites.HapiSuite.TINY_PARTS_PER_WHOLE;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.enableContractAutoRenewWith;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.PAY_RECEIVABLE_CONTRACT;
import static com.hedera.services.bdd.suites.records.ContractRecordsSanityCheckSuite.PAYABLE_CONTRACT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.junit.support.SpecManager;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hederahashgraph.api.proto.java.AccountAmount;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(LONG_RUNNING)
@HapiTestLifecycle
public class StakingSuite {
    public static final String END_OF_STAKING_PERIOD_CALCULATIONS_MEMO = "End of staking period calculation record";
    private static final long SUITE_PER_HBAR_REWARD_RATE = 3_333_333L;
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String CAROL = "carol";
    public static final String STAKING_START_THRESHOLD = "staking.startThreshold";
    public static final String REWARD_BALANCE_THRESHOLD = "staking.rewardBalanceThreshold";
    public static final String PER_HBAR_REWARD_RATE = "staking.perHbarRewardRate";
    public static final String STAKING_REWARD_RATE = "staking.perHbarRewardRate";
    public static final String FIRST_TRANSFER = "firstTransfer";
    private static final long STAKING_PERIOD_MINS = 1L;

    @BeforeAll
    static void beforeAll(@NonNull final SpecManager specManager) throws Throwable {
        specManager.setup(
                overridingThree(
                        STAKING_START_THRESHOLD, "" + 10 * ONE_HBAR,
                        PER_HBAR_REWARD_RATE, "" + SUITE_PER_HBAR_REWARD_RATE,
                        REWARD_BALANCE_THRESHOLD, "" + 0),
                cryptoTransfer(tinyBarsFromTo(GENESIS, STAKING_REWARD, ONE_MILLION_HBARS)));
    }

    /**
     * Tests a scenario in which many zero stake accounts are created, and then after a few staking
     * periods, a series of credits and debits are made to them, and they are confirmed to have
     * received the expected rewards (all zero).
     */
    @HapiTest
    final Stream<DynamicTest> zeroStakeAccountsHaveMetadataResetOnFirstDayTheyReceiveFunds() {
        final var zeroStakeAccount = "zeroStakeAccount";
        final var numZeroStakeAccounts = 10;
        final var stakePeriodMins = 1L;

        return defaultHapiSpec("ZeroStakeAccountsHaveMetadataResetOnFirstDayTheyReceiveFunds")
                .given(
                        inParallel(IntStream.range(0, numZeroStakeAccounts)
                                .mapToObj(i -> cryptoCreate(zeroStakeAccount + i)
                                        .stakedNodeId(0)
                                        .balance(0L))
                                .toArray(HapiSpecOperation[]::new)),
                        cryptoCreate("somebody").stakedNodeId(0).balance(10 * ONE_MILLION_HBARS),
                        // Wait a few periods
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)))
                .when()
                .then(sleepFor(5_000), withOpContext((spec, opLog) -> {
                    for (int i = 0; i < numZeroStakeAccounts; i++) {
                        final var target = zeroStakeAccount + i;
                        final var setupTxn = "setup" + i;
                        final var fundingTxn = "funding" + i;
                        final var withdrawingTxn = "withdrawing" + i;
                        final var first = cryptoTransfer(tinyBarsFromTo(GENESIS, target, 1))
                                .via(setupTxn);
                        final var second = cryptoTransfer(tinyBarsFromTo(GENESIS, target, ONE_MILLION_HBARS))
                                .via(fundingTxn);
                        final var third = cryptoTransfer(tinyBarsFromTo(target, GENESIS, ONE_MILLION_HBARS))
                                .via(withdrawingTxn);
                        allRunFor(
                                spec,
                                first,
                                second,
                                third,
                                getTxnRecord(setupTxn).hasPaidStakingRewardsCount(0),
                                getTxnRecord(fundingTxn).hasPaidStakingRewardsCount(0),
                                getTxnRecord(withdrawingTxn).hasPaidStakingRewardsCount(0));
                    }
                }));
    }

    /**
     * Tests a scenario in which Alice repeatedly transfers her balance to Baldwin right before the
     * end of a staking period, only to receive it back shortly after that period starts.
     */
    @HapiTest
    final Stream<DynamicTest> stakeIsManagedCorrectlyInTxnsAroundPeriodBoundaries() {
        final var alice = "alice";
        final var baldwin = "baldwin";
        final var stakePeriodMins = 1L;
        final AtomicLong currentAliceBalance = new AtomicLong();
        final AtomicLong currentBaldwinBalance = new AtomicLong();
        final List<List<AccountAmount>> rewardsPaid = new ArrayList<>();

        final int numPeriodsToRepeat = 3;
        final long secsBeforePeriodEndToDoTransfer = 5;
        final IntFunction<String> returnToAliceTxns = n -> "returnToAlice" + n;
        final IntFunction<String> sendToBobTxns = n -> "sendToBob" + n;
        final IntFunction<HapiSpecOperation> returnRecordLookup =
                n -> getTxnRecord(returnToAliceTxns.apply(n)).logged().exposingStakingRewardsTo(rewardsPaid::add);
        final IntFunction<HapiSpecOperation> sendRecordLookup =
                n -> getTxnRecord(sendToBobTxns.apply(n)).logged().exposingStakingRewardsTo(rewardsPaid::add);

        return defaultHapiSpec("StakeIsManagedCorrectlyInTxnsAroundPeriodBoundaries")
                .given(
                        cryptoCreate(alice).stakedNodeId(0).balance(ONE_MILLION_HBARS),
                        cryptoCreate(baldwin).stakedNodeId(0).balance(0L),
                        // Reach a period where stakers can collect rewards
                        waitUntilStartOfNextStakingPeriod(stakePeriodMins))
                .when(IntStream.range(0, numPeriodsToRepeat)
                        .mapToObj(i -> blockingOrder(
                                waitUntilJustBeforeNextStakingPeriod(stakePeriodMins, secsBeforePeriodEndToDoTransfer),
                                getAccountBalance(alice).exposingBalanceTo(currentAliceBalance::set),
                                // From Alice to Baldwin
                                sourcing(() -> cryptoTransfer(tinyBarsFromTo(alice, baldwin, currentAliceBalance.get()))
                                        .via(sendToBobTxns.apply(i))),
                                sourcing(() -> sendRecordLookup.apply(i)),
                                // Wait until the next period starts
                                sleepFor(2 * secsBeforePeriodEndToDoTransfer * 1000),
                                // Back to Alice from Baldwin
                                getAccountBalance(baldwin).exposingBalanceTo(currentBaldwinBalance::set),
                                sourcing(() -> cryptoTransfer(
                                                tinyBarsFromTo(baldwin, alice, currentBaldwinBalance.get()))
                                        .via(returnToAliceTxns.apply(i))),
                                sourcing(() -> returnRecordLookup.apply(i))))
                        .toArray(HapiSpecOperation[]::new))
                .then(withOpContext((spec, opLog) -> {
                    final var registry = spec.registry();
                    final var aliceNum = registry.getAccountID(alice).getAccountNum();
                    final var baldwinNum = registry.getAccountID(baldwin).getAccountNum();
                    final var expectedRewardsPaid = List.of(
                            List.of(0L, 0L),
                            // Alice held the balance at the start of the first rewardable period
                            List.of(3333333000000L, 0L),
                            List.of(0L, 0L),
                            // But Baldwin held the balance at the start of the second rewardable period
                            List.of(0L, 3333333000000L),
                            List.of(0L, 0L),
                            // And also the third rewardable period
                            List.of(0L, 3444442988889L));
                    for (int i = 0; i < rewardsPaid.size(); i++) {
                        if (i % 2 == 0) {
                            opLog.info("======= Send-to-Baldwin #{} =======", i / 2 + 1);
                        } else {
                            opLog.info("======= Return-to-Alice #{} =======", i / 2 + 1);
                        }
                        final var paidThisTime = rewardsPaid.get(i);
                        var aliceReward = 0L;
                        var baldwinReward = 0L;
                        for (final var paid : paidThisTime) {
                            if (paid.getAccountID().getAccountNum() == aliceNum) {
                                aliceReward = paid.getAmount();
                            }
                            if (paid.getAccountID().getAccountNum() == baldwinNum) {
                                baldwinReward = paid.getAmount();
                            }
                        }
                        opLog.info("=  Alice   : {}", aliceReward);
                        opLog.info("=  Baldwin : {}", baldwinReward);
                        opLog.info("==============================\n");
                        Assertions.assertEquals(expectedRewardsPaid.get(i), List.of(aliceReward, baldwinReward));
                    }
                }));
    }

    @HapiTest
    final Stream<DynamicTest> secondOrderRewardSituationsWork() {
        final long alicePendingRewardsCase1 =
                SUITE_PER_HBAR_REWARD_RATE * (2 * ONE_HUNDRED_HBARS / TINY_PARTS_PER_WHOLE);
        final long bobPendingRewardsCase1 = SUITE_PER_HBAR_REWARD_RATE * (ONE_HUNDRED_HBARS / TINY_PARTS_PER_WHOLE);

        return defaultHapiSpec("SecondOrderRewardSituationsWork")
                .given()
                .when( // period 1
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(CAROL).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        /* --- period 2 - paid_rewards 0 for first period --- */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER).hasPaidStakingRewards(List.of()),

                        /* --- second period reward eligible --- */
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)).via("endOfStakingPeriodXfer"),
                        getAccountInfo(ALICE)
                                .has(accountWith().stakedNodeId(0L).pendingRewards(666666600L))
                                .logged(),
                        getAccountInfo(BOB)
                                .has(accountWith().stakedNodeId(0L).pendingRewards(333333300L))
                                .logged(),
                        getAccountInfo(ALICE)
                                .has(accountWith().stakedNodeId(0L).pendingRewards(alicePendingRewardsCase1))
                                .logged(),
                        getAccountInfo(BOB)
                                .has(accountWith().stakedNodeId(0L).pendingRewards(bobPendingRewardsCase1))
                                .logged(),
                        cryptoUpdate(CAROL).newStakedAccountId(BOB).via("secondOrderRewardSituation"),
                        getTxnRecord("secondOrderRewardSituation")
                                .hasPaidStakingRewards(List.of(
                                        Pair.of(ALICE, alicePendingRewardsCase1), Pair.of(BOB, bobPendingRewardsCase1)))
                                .logged(),
                        /* Within the same period rewards are not awarded twice */
                        cryptoTransfer(tinyBarsFromTo(BOB, ALICE, ONE_HBAR))
                                .payingWith(BOB)
                                .via("expectNoReward"),
                        getTxnRecord("expectNoReward").hasStakingFeesPaid().logged());
    }

    @HapiTest
    final Stream<DynamicTest> pendingRewardsPaidBeforeStakedToMeUpdates() {
        return defaultHapiSpec("PendingRewardsPaidBeforeStakedToMeUpdates")
                .given()
                .when( // period 1
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(CAROL).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        /* --- period 2 - paid_rewards 0 for first period --- */
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, ONE_HBAR))
                                .via(FIRST_TRANSFER),
                        // alice - 100, carol - 100
                        /* --- third period reward eligible from period 2--- */
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoUpdate(CAROL).newStakedAccountId(ALICE).via("stakedIdUpdate"),
                        getTxnRecord("stakedIdUpdate")
                                .hasPaidStakingRewards(List.of(
                                        Pair.of(ALICE, 100 * SUITE_PER_HBAR_REWARD_RATE),
                                        Pair.of(CAROL, 100 * SUITE_PER_HBAR_REWARD_RATE))),
                        /* fourth period */
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, ONE_HBAR)),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ALICE, ONE_HBAR)).via("aliceFirstXfer"),
                        getTxnRecord("aliceFirstXfer")
                                // The period we are collecting in is the first that Alice will have the
                                // full benefits of the paid staking reward and Carol's stake; for now
                                // her reward is still based on the 100 HBAR she staked herself
                                .hasPaidStakingRewards(List.of(Pair.of(ALICE, 100 * SUITE_PER_HBAR_REWARD_RATE)))
                                .logged(),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),

                        /* fifth period */
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ALICE, ONE_HBAR)).via("aliceSecondXfer"),
                        getTxnRecord("aliceSecondXfer")
                                .hasPaidStakingRewards(List.of(Pair.of(
                                        ALICE,
                                        (200 + 2 * (100 * SUITE_PER_HBAR_REWARD_RATE / TINY_PARTS_PER_WHOLE))
                                                * SUITE_PER_HBAR_REWARD_RATE)))
                                .logged());
    }

    @HapiTest
    final Stream<DynamicTest> evenOneTinybarChangeInIndirectStakingAccountTriggersStakeeRewardSituation() {
        return defaultHapiSpec("EvenOneTinybarChangeInIndirectStakingAccountTriggersStakeeRewardSituation")
                .given()
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(CAROL).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, CAROL, 1)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER).hasPaidStakingRewardsCount(1));
    }

    @HapiTest
    final Stream<DynamicTest> zeroRewardEarnedWithZeroWholeHbarsStillSetsSASOLARP() {
        return defaultHapiSpec("ZeroRewardEarnedWithZeroWholeHbarsStillSetsSASOLARP")
                .given(
                        cryptoCreate("helpfulStaker").stakedNodeId(0).balance(ONE_MILLION_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(0L),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, ALICE, ONE_HUNDRED_HBARS)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(ALICE, FUNDING, ONE_HUNDRED_HBARS)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, ALICE, 1)).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER).hasPaidStakingRewardsCount(1));
    }

    @HapiTest
    final Stream<DynamicTest> losingEvenAZeroBalanceStakerTriggersStakeeRewardSituation() {
        return defaultHapiSpec("LosingEvenAZeroBalanceStakerTriggersStakeeRewardSituation")
                .given()
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(BOB).stakedAccountId(ALICE).balance(0L),
                        cryptoCreate(CAROL).stakedAccountId(ALICE).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoUpdate(BOB).newStakedNodeId(0L).via(FIRST_TRANSFER),
                        getTxnRecord(FIRST_TRANSFER).hasPaidStakingRewardsCount(1));
    }

    @HapiTest
    final Stream<DynamicTest> stakingMetadataUpdateIsRewardOpportunity() {
        return defaultHapiSpec("stakingMetadataUpdateIsRewardOpportunity")
                .given()
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_HUNDRED_HBARS),
                        uploadInitCode(PAYABLE_CONTRACT),
                        contractCreate(PAYABLE_CONTRACT).stakedNodeId(0L).balance(ONE_HUNDRED_HBARS),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),

                        /* Now rewards are eligible */
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        // info queries return rewards
                        getContractInfo(PAYABLE_CONTRACT)
                                .has(contractWith().stakedNodeId(0L).pendingRewards(333333300L)),
                        contractUpdate(PAYABLE_CONTRACT).newDeclinedReward(true).via("acceptsReward"),
                        getTxnRecord("acceptsReward")
                                .logged()
                                .andAllChildRecords()
                                .hasPaidStakingRewards(List.of(Pair.of(PAYABLE_CONTRACT, 333333300L))),
                        contractUpdate(PAYABLE_CONTRACT).newStakedNodeId(111L).hasPrecheck(INVALID_STAKING_ID),
                        // Same period should not trigger reward for the contract again, only for Alice
                        // whose stakedToMe has now changed
                        contractUpdate(PAYABLE_CONTRACT)
                                .newStakedAccountId(ALICE)
                                .via("samePeriodTxn"),
                        getTxnRecord("samePeriodTxn")
                                .logged()
                                .hasPaidStakingRewards(List.of(Pair.of(ALICE, 100 * SUITE_PER_HBAR_REWARD_RATE))));
    }

    @LeakyHapiTest(NO_CONCURRENT_STAKE_PERIOD_BOUNDARY_CROSSINGS)
    final Stream<DynamicTest> endOfStakingPeriodRecTest() {
        return hapiTest(
                waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1)).via("trigger"),
                getTxnRecord("trigger")
                        .countStakingRecords()
                        .hasChildRecordCount(1)
                        .hasChildRecords(recordWith().memo(END_OF_STAKING_PERIOD_CALCULATIONS_MEMO)),
                cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, ONE_HBAR)).via("noEndOfStakingPeriodRecord"),
                getTxnRecord("noEndOfStakingPeriodRecord")
                        .countStakingRecords()
                        .hasChildRecordCount(0)
                        .logged());
    }

    @HapiTest
    final Stream<DynamicTest> rewardsOfDeletedAreRedirectedToBeneficiary() {
        final var bob = "bob";
        final var deletion = "deletion";
        return defaultHapiSpec("RewardsOfDeletedAreRedirectedToBeneficiary")
                .given()
                .when(
                        cryptoCreate(ALICE).stakedNodeId(0).balance(ONE_MILLION_HBARS),
                        cryptoCreate(bob).balance(0L),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoDelete(ALICE).transfer(bob).via(deletion),
                        getTxnRecord(deletion).hasPaidStakingRewards(List.of(Pair.of(bob, 3333333000000L))));
    }

    /**
     * Creates a contract staked to a node with a lifetime just over one staking period; waits long
     * enough for it to be eligible for rewards, and then triggers its auto-renewal.
     *
     * <p>Since system records aren't queryable via HAPI, it's necessary to add logging in e.g.
     * ExpiryRecordsHelper#finalizeAndStream() to inspect the generated record and confirm staking
     * rewards are paid.
     *
     * @return the spec described above
     */
    // (FUTURE) Enable when expiry/auto-renewal is implemented
    final Stream<DynamicTest> autoRenewalsCanTriggerStakingRewards() {
        final var initBalance = ONE_HBAR * 1000;
        final var minimalLifetime = 3;
        final var creation = "creation";

        return defaultHapiSpec("AutoRenewalsCanTriggerStakingRewards")
                .given(
                        cryptoCreate("miscStaker").stakedNodeId(0).balance(ONE_HUNDRED_HBARS * 1000),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .when(
                        enableContractAutoRenewWith(minimalLifetime, 0),
                        contractCreate(PAY_RECEIVABLE_CONTRACT)
                                .gas(2_000_000)
                                .entityMemo("")
                                .stakedNodeId(0L)
                                // Lifetime is in seconds not minutes, add a 10 second buffer
                                .autoRenewSecs(STAKING_PERIOD_MINS * 60 + 10)
                                .balance(initBalance)
                                .via(creation),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS))
                .then(
                        cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)).via("triggerRenewal")
                        // (TODO) Verify that the contract was auto-renewed and that staking rewards were paid
                        );
    }

    // (FUTURE) Delete after confirming min stake will always be zero going forward
    final Stream<DynamicTest> canBeRewardedWithoutMinStakeIfSoConfigured() {
        final var patientlyWaiting = "patientlyWaiting";

        return defaultHapiSpec("CanBeRewardedWithoutMinStakeIfSoConfigured")
                .given(
                        overriding("staking.requireMinStakeToReward", "true"),
                        cryptoCreate(patientlyWaiting).stakedNodeId(0).balance(ONE_HUNDRED_HBARS))
                .when(
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        cryptoTransfer(tinyBarsFromTo(patientlyWaiting, FUNDING, 1))
                                .via("lackingMinStake"),
                        // If node0 was over minStake, we would have been rewarded
                        getTxnRecord("lackingMinStake")
                                .hasPaidStakingRewardsCount(0)
                                .logged(),
                        waitUntilStartOfNextStakingPeriod(STAKING_PERIOD_MINS),
                        cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L)),
                        // Now we should be rewardable even though node0 is far from minStake
                        overriding("staking.requireMinStakeToReward", "false"),
                        cryptoTransfer(tinyBarsFromTo(patientlyWaiting, FUNDING, 1))
                                .via("minStakeIrrelevant"))
                .then(getTxnRecord("lackingMinStake").logged().hasPaidStakingRewardsCount(1));
    }
}
