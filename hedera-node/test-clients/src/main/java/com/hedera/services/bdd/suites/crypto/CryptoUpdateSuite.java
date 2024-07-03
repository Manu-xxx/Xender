/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.crypto;

import static com.hedera.services.bdd.junit.ContextRequirement.PROPERTY_OVERRIDES;
import static com.hedera.services.bdd.junit.TestTags.CRYPTO;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountDetailsAsserts.accountDetailsWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyLabels.complex;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.ANY;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountDetails;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.triggerAndCloseAtLeastOneFileIfNotInterrupted;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.given;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.nOps;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingTwo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.streamMustIncludeNoFailuresFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.visibleItems;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.EXPECT_STREAMLINED_INGEST_RECORDS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.TRUE_VALUE;
import static com.hedera.services.bdd.suites.HapiSuite.ZERO_BYTE_MEMO;
import static com.hedera.services.bdd.suites.contract.hapi.ContractUpdateSuite.ADMIN_KEY;
import static com.hedera.services.bdd.suites.crypto.CryptoCreateSuite.UNLIMITED_AUTO_ASSOCIATIONS_ENABLED;
import static com.hedera.services.bdd.suites.crypto.CryptoTransferSuite.sdec;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_MAX_AUTO_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.joining;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.LeakyHapiTest;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.keys.KeyLabels;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItemsAssertion;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenType;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@Tag(CRYPTO)
@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class CryptoUpdateSuite {
    private static final long DEFAULT_MAX_LIFETIME =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

    private static final String TEST_ACCOUNT = "testAccount";
    private static final String TARGET_ACCOUNT = "complexKeyAccount";
    private static final String ACCOUNT_ALICE = "alice";
    private static final String ACCOUNT_PETER = "peter";
    private static final String ACCOUNT_PARKER = "parker";
    private static final String ACCOUNT_TONY = "tony";
    private static final String ACCOUNT_STARK = "stark";

    private static final String TOKEN_FUNGIBLE = "fungibleToken";

    private static final String REPEATING_KEY = "repeatingKey";
    private static final String ORIG_KEY = "origKey";
    private static final String UPD_KEY = "updKey";
    private static final String TARGET_KEY = "twoLevelThreshWithOverlap";
    private static final String MULTI_KEY = "multiKey";

    private static final SigControl twoLevelThresh = SigControl.threshSigs(
            2,
            SigControl.threshSigs(1, ANY, ANY, ANY, ANY, ANY, ANY, ANY),
            SigControl.threshSigs(3, ANY, ANY, ANY, ANY, ANY, ANY, ANY));
    private static final KeyLabels overlappingKeys =
            complex(complex("A", "B", "C", "D", "E", "F", "G"), complex("H", "I", "J", "K", "L", "M", "A"));

    private static final SigControl ENOUGH_UNIQUE_SIGS = SigControl.threshSigs(
            2,
            SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
            SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
    private static final SigControl NOT_ENOUGH_UNIQUE_SIGS = SigControl.threshSigs(
            2,
            SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
            SigControl.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));
    private static final SigControl ENOUGH_OVERLAPPING_SIGS = SigControl.threshSigs(
            2,
            SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
            SigControl.threshSigs(3, ON, ON, OFF, OFF, OFF, OFF, ON));

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(cryptoCreate("user").stakedAccountId("0.0.20").declinedReward(true))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> cryptoUpdate("user")
                        .newStakedAccountId("0.0.21")));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> updateForMaxAutoAssociationsForAccountsWorks() {
        return propertyPreservingHapiSpec("updateForMaxAutoAssociationsForAccountsWorks")
                .preserving("entities.unlimitedAutoAssociationsEnabled", "contracts.allowAutoAssociations")
                .given(
                        overridingTwo(
                                "entities.unlimitedAutoAssociationsEnabled",
                                "true",
                                "contracts.allowAutoAssociations",
                                TRUE_VALUE),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT_ALICE).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(0),
                        cryptoCreate(ACCOUNT_PETER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(-1),
                        cryptoCreate(ACCOUNT_TONY).balance(ONE_HUNDRED_HBARS),
                        cryptoCreate(ACCOUNT_STARK).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(-1),
                        cryptoCreate(ACCOUNT_PARKER).balance(ONE_HUNDRED_HBARS).maxAutomaticTokenAssociations(-1),
                        tokenCreate(TOKEN_FUNGIBLE)
                                .initialSupply(1000L)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .treasury(ACCOUNT_ALICE)
                                .via("tokenCreate"),
                        tokenAssociate(ACCOUNT_PETER, TOKEN_FUNGIBLE),
                        tokenAssociate(ACCOUNT_TONY, TOKEN_FUNGIBLE))
                .when(
                        // Update Alice
                        cryptoUpdate(ACCOUNT_ALICE).maxAutomaticAssociations(0),
                        getAccountInfo(ACCOUNT_ALICE).hasMaxAutomaticAssociations(0),
                        cryptoUpdate(ACCOUNT_ALICE).maxAutomaticAssociations(-1),
                        getAccountInfo(ACCOUNT_ALICE).hasMaxAutomaticAssociations(-1),
                        // Update Tony
                        cryptoUpdate(ACCOUNT_TONY).maxAutomaticAssociations(1),
                        getAccountInfo(ACCOUNT_TONY).hasMaxAutomaticAssociations(1),
                        // Update Stark
                        cryptoUpdate(ACCOUNT_STARK).maxAutomaticAssociations(-1),
                        getAccountInfo(ACCOUNT_STARK).hasMaxAutomaticAssociations(-1),
                        // Update Peter
                        cryptoUpdate(ACCOUNT_PETER).maxAutomaticAssociations(-1),
                        getAccountInfo(ACCOUNT_PETER).hasMaxAutomaticAssociations(-1),
                        cryptoUpdate(ACCOUNT_PETER).maxAutomaticAssociations(0),
                        getAccountInfo(ACCOUNT_PETER).hasMaxAutomaticAssociations(0),
                        // Update Parker
                        cryptoUpdate(ACCOUNT_PARKER).maxAutomaticAssociations(1),
                        getAccountInfo(ACCOUNT_PARKER).hasMaxAutomaticAssociations(1))
                .then(getTxnRecord("tokenCreate").hasNewTokenAssociation(TOKEN_FUNGIBLE, ACCOUNT_ALICE));
    }

    @HapiTest
    final Stream<DynamicTest> updateStakingFieldsWorks() {
        return defaultHapiSpec("updateStakingFieldsWorks", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(ADMIN_KEY),
                        cryptoCreate("user")
                                .key(ADMIN_KEY)
                                .stakedAccountId("0.0.20")
                                .declinedReward(true),
                        getAccountInfo("user")
                                .has(AccountInfoAsserts.accountWith()
                                        .stakedAccountId("0.0.20")
                                        .noStakingNodeId()
                                        .isDeclinedReward(true)))
                .when(
                        cryptoUpdate("user").newStakedNodeId(0L).newDeclinedReward(false),
                        getAccountInfo("user")
                                .has(AccountInfoAsserts.accountWith()
                                        .noStakedAccountId()
                                        .stakedNodeId(0L)
                                        .isDeclinedReward(false)),
                        cryptoUpdate("user").newStakedNodeId(-1L),
                        cryptoUpdate("user").newStakedNodeId(-25L).hasKnownStatus(INVALID_STAKING_ID),
                        getAccountInfo("user")
                                .has(AccountInfoAsserts.accountWith()
                                        .noStakedAccountId()
                                        .noStakingNodeId()
                                        .isDeclinedReward(false)))
                .then(
                        cryptoUpdate("user")
                                .key(ADMIN_KEY)
                                .newStakedAccountId("0.0.20")
                                .newDeclinedReward(true),
                        getAccountInfo("user")
                                .has(AccountInfoAsserts.accountWith()
                                        .stakedAccountId("0.0.20")
                                        .noStakingNodeId()
                                        .isDeclinedReward(true))
                                .logged(),
                        cryptoUpdate("user").key(ADMIN_KEY).newStakedAccountId("0.0.0"),
                        getAccountInfo("user")
                                .has(AccountInfoAsserts.accountWith()
                                        .noStakedAccountId()
                                        .noStakingNodeId()
                                        .isDeclinedReward(true))
                                .logged(),
                        // For completeness stake back to a node
                        cryptoUpdate("user").key(ADMIN_KEY).newStakedNodeId(1),
                        getAccountInfo("user")
                                .has(AccountInfoAsserts.accountWith()
                                        .stakedNodeId(1L)
                                        .isDeclinedReward(true)));
    }

    @HapiTest
    final Stream<DynamicTest> usdFeeAsExpectedCryptoUpdate() {
        double autoAssocSlotPrice = 0.0018;
        double baseFee = 0.000214;
        double baseFeeWithExpiry = 0.00022;
        double plusOneSlotFee = baseFee + autoAssocSlotPrice;
        double plusTenSlotsFee = baseFee + 10 * autoAssocSlotPrice;

        final var baseTxn = "baseTxn";
        final var plusOneTxn = "plusOneTxn";
        final var plusTenTxn = "plusTenTxn";
        final var plusFiveKTxn = "plusFiveKTxn";
        final var plusFiveKAndOneTxn = "plusFiveKAndOneTxn";
        final var invalidNegativeTxn = "invalidNegativeTxn";
        final var validNegativeTxn = "validNegativeTxn";
        final var allowedPercentDiff = 1.5;

        AtomicLong expiration = new AtomicLong();
        return propertyPreservingHapiSpec("usdFeeAsExpectedCryptoUpdate", NONDETERMINISTIC_TRANSACTION_FEES)
                .preserving(UNLIMITED_AUTO_ASSOCIATIONS_ENABLED, "ledger.maxAutoAssociations")
                .given(
                        overridingTwo(
                                UNLIMITED_AUTO_ASSOCIATIONS_ENABLED, TRUE_VALUE, "ledger.maxAutoAssociations", "5000"),
                        newKeyNamed("key").shape(SIMPLE),
                        cryptoCreate("payer").key("key").balance(1_000 * ONE_HBAR),
                        cryptoCreate("canonicalAccount")
                                .key("key")
                                .balance(100 * ONE_HBAR)
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .blankMemo()
                                .payingWith("payer"),
                        cryptoCreate("autoAssocTarget")
                                .key("key")
                                .balance(100 * ONE_HBAR)
                                .autoRenewSecs(THREE_MONTHS_IN_SECONDS)
                                .blankMemo()
                                .payingWith("payer"),
                        getAccountInfo("canonicalAccount").exposingExpiry(expiration::set))
                .when(
                        sourcing(() -> cryptoUpdate("canonicalAccount")
                                .payingWith("canonicalAccount")
                                .expiring(expiration.get() + THREE_MONTHS_IN_SECONDS)
                                .blankMemo()
                                .via(baseTxn)),
                        getAccountInfo("canonicalAccount")
                                .hasMaxAutomaticAssociations(0)
                                .logged(),
                        cryptoUpdate("autoAssocTarget")
                                .payingWith("autoAssocTarget")
                                .blankMemo()
                                .maxAutomaticAssociations(1)
                                .via(plusOneTxn),
                        getAccountInfo("autoAssocTarget")
                                .hasMaxAutomaticAssociations(1)
                                .logged(),
                        cryptoUpdate("autoAssocTarget")
                                .payingWith("autoAssocTarget")
                                .blankMemo()
                                .maxAutomaticAssociations(11)
                                .via(plusTenTxn),
                        getAccountInfo("autoAssocTarget")
                                .hasMaxAutomaticAssociations(11)
                                .logged(),
                        cryptoUpdate("autoAssocTarget")
                                .payingWith("autoAssocTarget")
                                .blankMemo()
                                .maxAutomaticAssociations(5000)
                                .via(plusFiveKTxn),
                        getAccountInfo("autoAssocTarget")
                                .hasMaxAutomaticAssociations(5000)
                                .logged(),
                        cryptoUpdate("autoAssocTarget")
                                .payingWith("autoAssocTarget")
                                .blankMemo()
                                .maxAutomaticAssociations(-1000)
                                .via(invalidNegativeTxn)
                                .hasKnownStatus(INVALID_MAX_AUTO_ASSOCIATIONS),
                        cryptoUpdate("autoAssocTarget")
                                .payingWith("autoAssocTarget")
                                .blankMemo()
                                .maxAutomaticAssociations(5001)
                                .via(plusFiveKAndOneTxn)
                                .hasKnownStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                        cryptoUpdate("autoAssocTarget")
                                .payingWith("autoAssocTarget")
                                .blankMemo()
                                .maxAutomaticAssociations(-1)
                                .via(validNegativeTxn),
                        getAccountInfo("autoAssocTarget")
                                .hasMaxAutomaticAssociations(-1)
                                .logged())
                .then(
                        validateChargedUsd(baseTxn, baseFeeWithExpiry, allowedPercentDiff)
                                .skippedIfAutoScheduling(Set.of(CryptoUpdate)),
                        validateChargedUsd(plusOneTxn, baseFee, allowedPercentDiff)
                                .skippedIfAutoScheduling(Set.of(CryptoUpdate)),
                        validateChargedUsd(plusTenTxn, baseFee, allowedPercentDiff)
                                .skippedIfAutoScheduling(Set.of(CryptoUpdate)),
                        validateChargedUsd(plusFiveKTxn, baseFee, allowedPercentDiff)
                                .skippedIfAutoScheduling(Set.of(CryptoUpdate)),
                        validateChargedUsd(validNegativeTxn, baseFee, allowedPercentDiff)
                                .skippedIfAutoScheduling(Set.of(CryptoUpdate)));
    }

    @HapiTest
    final Stream<DynamicTest> updateFailsWithOverlyLongLifetime() {
        final var smallBuffer = 12_345L;
        final var excessiveExpiry = DEFAULT_MAX_LIFETIME + Instant.now().getEpochSecond() + smallBuffer;
        return defaultHapiSpec("UpdateFailsWithOverlyLongLifetime")
                .given(cryptoCreate(TARGET_ACCOUNT))
                .when()
                .then(cryptoUpdate(TARGET_ACCOUNT).expiring(excessiveExpiry).hasKnownStatus(INVALID_EXPIRATION_TIME));
    }

    @HapiTest
    final Stream<DynamicTest> updatePriceScalesWithLifetime() {
        final var accountsToUpdate = 8;
        final var updateTxns =
                IntStream.range(0, accountsToUpdate).mapToObj(i -> "update" + i).toArray(String[]::new);
        final var lifetimesToCompare = Stream.iterate(3 * THREE_MONTHS_IN_SECONDS, l -> l * 2)
                .limit(accountsToUpdate)
                .toArray(Long[]::new);
        final AtomicReference<VisibleItemsAssertion> assertion = new AtomicReference<>();
        final AtomicLong startPoint = new AtomicLong();
        return hapiTest(
                given(() -> startPoint.set(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)),
                streamMustIncludeNoFailuresFrom(visibleItems(assertion, updateTxns)),
                inParallel(
                        nOps(accountsToUpdate, i -> cryptoCreate("account" + i).balance(ONE_MILLION_HBARS))),
                blockingOrder(nOps(
                        accountsToUpdate,
                        i -> sourcing(() -> cryptoUpdate("account" + i)
                                .expiring(startPoint.get() + lifetimesToCompare[i])
                                .payingWith("account" + i)
                                .via(updateTxns[i])))),
                withOpContext((spec, opLog) -> {
                    triggerAndCloseAtLeastOneFileIfNotInterrupted(spec);
                    final var pricesCharged =
                            assertion.get().entriesWithin(Duration.ofSeconds(2)).join();
                    opLog.info(
                            "Charged prices (in USD): \n  {}",
                            IntStream.range(0, accountsToUpdate)
                                    .mapToObj(i -> String.format(
                                                    "%-12s",
                                                    Duration.ofSeconds(lifetimesToCompare[i])
                                                                    .toDays() + " days")
                                            + " :: "
                                            + sdec(
                                                    spec.ratesProvider()
                                                            .toUsdWithActiveRates(pricesCharged
                                                                    .get(updateTxns[i])
                                                                    .getFirst()
                                                                    .transactionRecord()
                                                                    .getTransactionFee()),
                                                    8))
                                    .collect(joining("\n  ")));
                }));
    }

    @HapiTest
    final Stream<DynamicTest> sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign() {
        String sysAccount = "0.0.99";
        String randomAccount = "randomAccount";
        String firstKey = "firstKey";
        String secondKey = "secondKey";

        return defaultHapiSpec("sysAccountKeyUpdateBySpecialWontNeedNewKeyTxnSign")
                .given(
                        newKeyNamed(firstKey).shape(SIMPLE),
                        newKeyNamed(secondKey).shape(SIMPLE))
                .when(cryptoCreate(randomAccount).key(firstKey))
                .then(
                        cryptoUpdate(sysAccount)
                                .key(secondKey)
                                .signedBy(GENESIS)
                                .payingWith(GENESIS)
                                .hasKnownStatus(SUCCESS)
                                .logged(),
                        cryptoUpdate(randomAccount)
                                .key(secondKey)
                                .signedBy(firstKey)
                                .payingWith(GENESIS)
                                .hasPrecheck(INVALID_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> canUpdateMemo() {
        String firstMemo = "First";
        String secondMemo = "Second";
        return defaultHapiSpec("CanUpdateMemo")
                .given(cryptoCreate(TARGET_ACCOUNT).balance(0L).entityMemo(firstMemo))
                .when(
                        cryptoUpdate(TARGET_ACCOUNT)
                                .entityMemo(ZERO_BYTE_MEMO)
                                .hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
                        cryptoUpdate(TARGET_ACCOUNT).entityMemo(secondMemo))
                .then(getAccountDetails(TARGET_ACCOUNT)
                        .payingWith(GENESIS)
                        .has(accountDetailsWith().memo(secondMemo)));
    }

    @HapiTest
    final Stream<DynamicTest> updateWithUniqueSigs() {
        return defaultHapiSpec("UpdateWithUniqueSigs", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(TARGET_KEY).shape(twoLevelThresh).labels(overlappingKeys),
                        cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY))
                .when()
                .then(cryptoUpdate(TARGET_ACCOUNT)
                        .sigControl(forKey(TARGET_KEY, ENOUGH_UNIQUE_SIGS))
                        .receiverSigRequired(true));
    }

    @HapiTest
    final Stream<DynamicTest> updateWithOneEffectiveSig() {
        KeyLabels oneUniqueKey =
                complex(complex("X", "X", "X", "X", "X", "X", "X"), complex("X", "X", "X", "X", "X", "X", "X"));
        SigControl singleSig = SigControl.threshSigs(
                2,
                SigControl.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, OFF),
                SigControl.threshSigs(3, OFF, OFF, OFF, ON, OFF, OFF, OFF));

        return defaultHapiSpec("UpdateWithOneEffectiveSig", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(REPEATING_KEY).shape(twoLevelThresh).labels(oneUniqueKey),
                        cryptoCreate(TARGET_ACCOUNT).key(REPEATING_KEY).balance(1_000_000_000L))
                .when()
                .then(cryptoUpdate(TARGET_ACCOUNT)
                        .sigControl(forKey(REPEATING_KEY, singleSig))
                        .receiverSigRequired(true)
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> updateWithOverlappingSigs() {
        return defaultHapiSpec("UpdateWithOverlappingSigs", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(TARGET_KEY).shape(twoLevelThresh).labels(overlappingKeys),
                        cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY))
                .when()
                .then(cryptoUpdate(TARGET_ACCOUNT)
                        .sigControl(forKey(TARGET_KEY, ENOUGH_OVERLAPPING_SIGS))
                        .receiverSigRequired(true)
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final Stream<DynamicTest> updateFailsWithContractKey() {
        AtomicLong id = new AtomicLong();
        final var CONTRACT = "Multipurpose";
        return defaultHapiSpec(
                        "UpdateFailsWithContractKey",
                        NONDETERMINISTIC_TRANSACTION_FEES,
                        EXPECT_STREAMLINED_INGEST_RECORDS)
                .given(
                        cryptoCreate(TARGET_ACCOUNT),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).exposingNumTo(id::set))
                .when()
                .then(sourcing(() -> cryptoUpdate(TARGET_ACCOUNT)
                        .protoKey(Key.newBuilder()
                                .setContractID(ContractID.newBuilder()
                                        .setContractNum(id.get())
                                        .build())
                                .build())
                        .hasKnownStatus(INVALID_SIGNATURE)));
    }

    @HapiTest
    final Stream<DynamicTest> updateFailsWithInsufficientSigs() {
        return defaultHapiSpec("UpdateFailsWithInsufficientSigs", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(TARGET_KEY).shape(twoLevelThresh).labels(overlappingKeys),
                        cryptoCreate(TARGET_ACCOUNT).key(TARGET_KEY))
                .when()
                .then(cryptoUpdate(TARGET_ACCOUNT)
                        .sigControl(forKey(TARGET_KEY, NOT_ENOUGH_UNIQUE_SIGS))
                        .receiverSigRequired(true)
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> cannotSetThresholdNegative() {
        return defaultHapiSpec("CannotSetThresholdNegative", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(cryptoCreate(TEST_ACCOUNT))
                .when()
                .then(cryptoUpdate(TEST_ACCOUNT).sendThreshold(-1L));
    }

    @HapiTest
    final Stream<DynamicTest> updateFailsIfMissingSigs() {
        SigControl origKeySigs = SigControl.threshSigs(3, ON, ON, SigControl.threshSigs(1, OFF, ON));
        SigControl updKeySigs = SigControl.listSigs(ON, OFF, SigControl.threshSigs(1, ON, OFF, OFF, OFF));

        return defaultHapiSpec("UpdateFailsIfMissingSigs", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(ORIG_KEY).shape(origKeySigs),
                        newKeyNamed(UPD_KEY).shape(updKeySigs))
                .when(cryptoCreate(TEST_ACCOUNT)
                        .receiverSigRequired(true)
                        .key(ORIG_KEY)
                        .sigControl(forKey(ORIG_KEY, origKeySigs)))
                .then(cryptoUpdate(TEST_ACCOUNT)
                        .key(UPD_KEY)
                        .sigControl(forKey(TEST_ACCOUNT, origKeySigs), forKey(UPD_KEY, updKeySigs))
                        .hasKnownStatus(INVALID_SIGNATURE));
    }

    @HapiTest
    final Stream<DynamicTest> updateWithEmptyKeyFails() {
        SigControl updKeySigs = threshOf(0, 0);

        return defaultHapiSpec("updateWithEmptyKeyFails", NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(ORIG_KEY).shape(KeyShape.SIMPLE),
                        newKeyNamed(UPD_KEY).shape(updKeySigs))
                .when(cryptoCreate(TEST_ACCOUNT).key(ORIG_KEY))
                .then(cryptoUpdate(TEST_ACCOUNT).key(UPD_KEY).hasPrecheck(INVALID_ADMIN_KEY));
    }

    @LeakyHapiTest(PROPERTY_OVERRIDES)
    final Stream<DynamicTest> updateMaxAutoAssociationsWorks() {
        final int maxAllowedAssociations = 5000;
        final int originalMax = 2;
        final int newBadMax = originalMax - 1;
        final int newGoodMax = originalMax + 1;
        final String tokenA = "tokenA";
        final String tokenB = "tokenB";

        final String treasury = "treasury";
        final String tokenACreate = "tokenACreate";
        final String tokenBCreate = "tokenBCreate";
        final String transferAToC = "transferAToC";
        final String transferBToC = "transferBToC";
        final String CONTRACT = "Multipurpose";
        final String ADMIN_KEY = "adminKey";

        return propertyPreservingHapiSpec("updateMaxAutoAssociationsWorks", NONDETERMINISTIC_TRANSACTION_FEES)
                .preserving("contracts.allowAutoAssociations", UNLIMITED_AUTO_ASSOCIATIONS_ENABLED)
                .given(
                        cryptoCreate(treasury).balance(ONE_HUNDRED_HBARS),
                        newKeyNamed(ADMIN_KEY),
                        overridingTwo(
                                "contracts.allowAutoAssociations", "true", UNLIMITED_AUTO_ASSOCIATIONS_ENABLED, "true"),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT).adminKey(ADMIN_KEY).maxAutomaticTokenAssociations(originalMax),
                        tokenCreate(tokenA)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(Long.MAX_VALUE)
                                .treasury(treasury)
                                .via(tokenACreate),
                        getTxnRecord(tokenACreate).hasNewTokenAssociation(tokenA, treasury),
                        tokenCreate(tokenB)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(Long.MAX_VALUE)
                                .treasury(treasury)
                                .via(tokenBCreate),
                        getTxnRecord(tokenBCreate).hasNewTokenAssociation(tokenB, treasury),
                        getContractInfo(CONTRACT)
                                .has(ContractInfoAsserts.contractWith().maxAutoAssociations(originalMax))
                                .logged())
                .when(
                        cryptoTransfer(moving(1, tokenA).between(treasury, CONTRACT))
                                .via(transferAToC),
                        getTxnRecord(transferAToC).hasNewTokenAssociation(tokenA, CONTRACT),
                        cryptoTransfer(moving(1, tokenB).between(treasury, CONTRACT))
                                .via(transferBToC),
                        getTxnRecord(transferBToC).hasNewTokenAssociation(tokenB, CONTRACT))
                .then(
                        getContractInfo(CONTRACT)
                                .payingWith(GENESIS)
                                .has(contractWith()
                                        .hasAlreadyUsedAutomaticAssociations(originalMax)
                                        .maxAutoAssociations(originalMax)),
                        contractUpdate(CONTRACT)
                                .newMaxAutomaticAssociations(newBadMax)
                                .hasKnownStatus(EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT),
                        contractUpdate(CONTRACT).newMaxAutomaticAssociations(newGoodMax),
                        getContractInfo(CONTRACT).has(contractWith().maxAutoAssociations(newGoodMax)),
                        contractUpdate(CONTRACT)
                                .newMaxAutomaticAssociations(maxAllowedAssociations + 1)
                                .hasKnownStatus(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),
                        contractUpdate(CONTRACT)
                                .newMaxAutomaticAssociations(-2)
                                .hasKnownStatus(INVALID_MAX_AUTO_ASSOCIATIONS),
                        contractUpdate(CONTRACT).newMaxAutomaticAssociations(-1).hasKnownStatus(SUCCESS),
                        getContractInfo(CONTRACT).has(contractWith().maxAutoAssociations(-1)));
    }
}
