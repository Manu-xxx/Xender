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

package com.hedera.services.bdd.suites.addressbook;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nodeCreate;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class NodeCreateSuite {
    private static final long defaultMaxLifetime =
            Long.parseLong(HapiSpecSetup.getDefaultNodeProps().get("entities.maxLifetime"));

    //    @HapiTest
    //    final Stream<DynamicTest> exchangeRateControlAccountIsntCharged() {
    //        return defaultHapiSpec("ExchangeRateControlAccountIsntCharged")
    //                .given(
    //                        cryptoTransfer(tinyBarsFromTo(GENESIS, EXCHANGE_RATE_CONTROL, 1_000_000_000_000L)),
    //                        balanceSnapshot("pre", EXCHANGE_RATE_CONTROL),
    //                        getFileContents(EXCHANGE_RATES).saveTo("exchangeRates.bin"))
    //                .when(fileUpdate(EXCHANGE_RATES)
    //                        .payingWith(EXCHANGE_RATE_CONTROL)
    //                        .path(Path.of("./", "exchangeRates.bin").toString()))
    //                .then(getAccountBalance(EXCHANGE_RATE_CONTROL).hasTinyBars(changeFromSnapshot("pre", 0)));
    //    }

    //    @HapiTest
    //    final Stream<DynamicTest> createFailsWithExcessiveLifetime() {
    //        return defaultHapiSpec("CreateFailsWithExcessiveLifetime")
    //                .given()
    //                .when()
    //                .then(fileCreate("test")
    //                        .lifetime(defaultMaxLifetime + 12_345L)
    //                        .hasPrecheck(AUTORENEW_DURATION_NOT_IN_RANGE));
    //    }
    //
    //    @HapiTest
    //    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
    //        return defaultHapiSpec("idVariantsTreatedAsExpected")
    //                .given()
    //                .when()
    //                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> fileCreate("file")
    //                        .contents("ABC")));
    //    }

    @HapiTest
    final Stream<DynamicTest> createNodeWorks() {
        String description = "His vorpal blade went snicker-snack!";

        return defaultHapiSpec("createNodeWorks")
                .given(nodeCreate("ntb").addDesc(description).addGossipCaCertificate("gossipCaCert".getBytes()))
                .when()
                .then(
                        //                        withTargetLedgerId(ledgerId ->
                        // getNodeInfo("ntb").hasEncodedLedgerId(ledgerId))
                        );
    }
    // example from CryptoCreateSuite
    //    @HapiTest
    //    @Tag(EMBEDDED)
    //    final Stream<DynamicTest> verifyCreatedAccountAsExpected() {
    //        return hapiTest(
    //                cryptoCreate("civilianWORewardStakingNode")
    //                        .balance(ONE_HUNDRED_HBARS)
    //                        .declinedReward(true)
    //                        .stakedNodeId(0),
    //                viewAccount("civilianWORewardStakingNode", account -> {
    //                    assertEquals(ONE_HUNDRED_HBARS, account.tinybarBalance());
    //                    assertTrue(account.declineReward());
    //                    assertEquals(0, account.stakedNodeIdOrThrow());
    //                }));
    //    }

    //    @HapiTest
    //    final Stream<DynamicTest> createFailsWithMissingSigs() {
    //        KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
    //        SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
    //        SigControl invalidSig = shape.signedWith(sigs(OFF, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
    //
    //        return defaultHapiSpec("CreateFailsWithMissingSigs")
    //                .given()
    //                .when()
    //                .then(
    //                        fileCreate("test")
    //                                .waclShape(shape)
    //                                .sigControl(forKey("test", invalidSig))
    //                                .hasKnownStatus(INVALID_SIGNATURE),
    //                        fileCreate("test").waclShape(shape).sigControl(forKey("test", validSig)));
    //    }
    //
    //    private static Transaction replaceTxnNodeAccount(Transaction txn) {
    //        AccountID badNodeAccount = AccountID.newBuilder()
    //                .setAccountNum(2000)
    //                .setRealmNum(0)
    //                .setShardNum(0)
    //                .build();
    //        return TxnUtils.replaceTxnNodeAccount(txn, badNodeAccount);
    //    }
    //
    //    @HapiTest
    //    final Stream<DynamicTest> createFailsWithPayerAccountNotFound() {
    //        KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
    //        SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
    //
    //        return defaultHapiSpec("CreateFailsWithPayerAccountNotFound")
    //                .given()
    //                .when()
    //                .then(fileCreate("test")
    //                        .withProtoStructure(HapiSpecSetup.TxnProtoStructure.OLD)
    //                        .waclShape(shape)
    //                        .sigControl(forKey("test", validSig))
    //                        .withTxnTransform(NodeCreateSuite::replaceTxnNodeAccount)
    //                        .hasPrecheckFrom(INVALID_NODE_ACCOUNT));
    //    }
    //
    //    @HapiTest
    //    final Stream<DynamicTest> precheckRejectsBadEffectiveAutoRenewPeriod() {
    //        var now = Instant.now();
    //        System.out.println(now.getEpochSecond());
    //
    //        return defaultHapiSpec("precheckRejectsBadEffectiveAutoRenewPeriod")
    //                .given()
    //                .when()
    //                .then(fileCreate("notHere")
    //                        .lifetime(-60L)
    //                        .hasPrecheck(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE));
    //    }
    //
    //    @HapiTest
    //    final Stream<DynamicTest> targetsAppear() {
    //        var lifetime = 100_000L;
    //        var requestedExpiry = Instant.now().getEpochSecond() + lifetime;
    //        var contents = "SOMETHING".getBytes();
    //        var newWacl = listOf(SIMPLE, listOf(3), threshOf(1, 3));
    //        var newWaclSigs = newWacl.signedWith(sigs(ON, sigs(ON, ON, ON), sigs(OFF, OFF, ON)));
    //
    //        return defaultHapiSpec("targetsAppear")
    //                .given(UtilVerbs.newKeyNamed("newWacl").shape(newWacl))
    //                .when(fileCreate("file")
    //                        .via("createTxn")
    //                        .contents(contents)
    //                        .key("newWacl")
    //                        .expiry(requestedExpiry)
    //                        .signedBy(GENESIS, "newWacl")
    //                        .sigControl(ControlForKey.forKey("newWacl", newWaclSigs)))
    //                .then(
    //                        QueryVerbs.getFileInfo("file")
    //                                .hasDeleted(false)
    //                                .hasWacl("newWacl")
    //                                .hasExpiryPassing(expiry -> expiry == requestedExpiry),
    //                        QueryVerbs.getFileContents("file")
    //                                .hasByteStringContents(ignore -> ByteString.copyFrom(contents)));
    //    }
    //
    //    @HapiTest
    //    final Stream<DynamicTest> getsExpectedRejections() {
    //        return defaultHapiSpec("getsExpectedRejections")
    //                .given(fileCreate("tbd"), fileDelete("tbd"))
    //                .when()
    //                .then(
    //                        getFileInfo("1.2.3").nodePayment(1_234L).hasAnswerOnlyPrecheck(INVALID_FILE_ID),
    //                        getFileContents("1.2.3").nodePayment(1_234L).hasAnswerOnlyPrecheck(INVALID_FILE_ID),
    //                        getFileContents("tbd")
    //                                .nodePayment(1_234L)
    //                                .hasAnswerOnlyPrecheck(OK)
    //                                .logged(),
    //                        getFileInfo("tbd")
    //                                .nodePayment(1_234L)
    //                                .hasAnswerOnlyPrecheck(OK)
    //                                .hasDeleted(true)
    //                                .logged());
    //    }
    //
    //    /**
    //     * Fetches the system files from a running network and validates they can be
    //     * parsed as the expected protobuf messages. */
    //    @HapiTest
    //    final Stream<DynamicTest> fetchFiles() {
    //        return customHapiSpec("FetchFiles")
    //                .withProperties(Map.of(
    //                        "fees.useFixedOffer", "true",
    //                        "fees.fixedOffer", "100000000"))
    //                .given()
    //                .when()
    //                .then(
    //                        getFileContents(NODE_DETAILS).andValidate(unchecked(NodeAddressBook::parseFrom)::apply),
    //                        getFileContents(ADDRESS_BOOK).andValidate(unchecked(NodeAddressBook::parseFrom)::apply),
    //                        getFileContents(EXCHANGE_RATES).andValidate(unchecked(ExchangeRateSet::parseFrom)::apply),
    //                        getFileContents(APP_PROPERTIES)
    //                                .andValidate(unchecked(ServicesConfigurationList::parseFrom)::apply),
    //                        getFileContents(API_PERMISSIONS)
    //                                .andValidate(unchecked(ServicesConfigurationList::parseFrom)::apply),
    //                        getFileContents(FEE_SCHEDULE)
    //                                .fee(300_000L)
    //                                .nodePayment(40L)
    //                                .andValidate(unchecked(CurrentAndNextFeeSchedule::parseFrom)::apply));
    //    }
    //
    //    @FunctionalInterface
    //    public interface CheckedParser {
    //        Object parseFrom(byte[] bytes) throws Exception;
    //    }
    //
    //    static Function<byte[], String> unchecked(CheckedParser parser) {
    //        return bytes -> {
    //            try {
    //                return parser.parseFrom(bytes).toString();
    //            } catch (Exception e) {
    //                e.printStackTrace();
    //                return "<N/A> due to " + e.getMessage() + "!";
    //            }
    //        };
    //    }
}
