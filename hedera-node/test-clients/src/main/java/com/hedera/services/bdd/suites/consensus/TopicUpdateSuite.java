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

package com.hedera.services.bdd.suites.consensus;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doSeveralWithStartupConfigNow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hedera.services.bdd.suites.HapiSuite.EMPTY_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.NONSENSE_KEY;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.HapiSuite.ZERO_BYTE_MEMO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_ACCOUNT_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;

import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicUpdate;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class TopicUpdateSuite {
    private static final long validAutoRenewPeriod = 7_000_000L;

    @HapiTest
    final Stream<DynamicTest> pureCheckFails() {
        return defaultHapiSpec("testTopic")
                .given()
                .when()
                .then(updateTopic("0.0.1").hasPrecheck(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> updateToMissingTopicFails() {
        return defaultHapiSpec("updateToMissingTopicFails")
                .given()
                .when()
                .then(updateTopic("1.2.3").hasKnownStatus(INVALID_TOPIC_ID));
    }

    @HapiTest
    final Stream<DynamicTest> idVariantsTreatedAsExpected() {
        final var autoRenewAccount = "autoRenewAccount";
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(cryptoCreate(autoRenewAccount), cryptoCreate("replacementAccount"), newKeyNamed("adminKey"))
                .when(createTopic("topic").adminKeyName("adminKey").autoRenewAccountId(autoRenewAccount))
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> updateTopic("topic")
                        .autoRenewAccountId("replacementAccount")));
    }

    @HapiTest
    final Stream<DynamicTest> validateMultipleFields() {
        byte[] longBytes = new byte[1000];
        Arrays.fill(longBytes, (byte) 33);
        String longMemo = new String(longBytes, StandardCharsets.UTF_8);
        return defaultHapiSpec("validateMultipleFields")
                .given(newKeyNamed("adminKey"), createTopic("testTopic").adminKeyName("adminKey"))
                .when()
                .then(
                        updateTopic("testTopic")
                                .adminKey(NONSENSE_KEY)
                                .hasPrecheckFrom(BAD_ENCODING, OK)
                                .hasKnownStatus(BAD_ENCODING),
                        updateTopic("testTopic").submitKey(NONSENSE_KEY).hasKnownStatus(BAD_ENCODING),
                        updateTopic("testTopic").topicMemo(longMemo).hasKnownStatus(MEMO_TOO_LONG),
                        updateTopic("testTopic").topicMemo(ZERO_BYTE_MEMO).hasKnownStatus(INVALID_ZERO_BYTE_IN_STRING),
                        updateTopic("testTopic").autoRenewPeriod(0).hasKnownStatus(AUTORENEW_DURATION_NOT_IN_RANGE),
                        updateTopic("testTopic")
                                .autoRenewPeriod(Long.MAX_VALUE)
                                .hasKnownStatus(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @HapiTest
    final Stream<DynamicTest> topicUpdateSigReqsEnforcedAtConsensus() {
        long PAYER_BALANCE = 199_999_999_999L;
        Function<String[], HapiTopicUpdate> updateTopicSignedBy = (signers) -> updateTopic("testTopic")
                .payingWith("payer")
                .adminKey("newAdminKey")
                .autoRenewAccountId("newAutoRenewAccount")
                .signedBy(signers);

        return defaultHapiSpec("topicUpdateSigReqsEnforcedAtConsensus")
                .given(
                        newKeyNamed("oldAdminKey"),
                        cryptoCreate("oldAutoRenewAccount"),
                        newKeyNamed("newAdminKey"),
                        cryptoCreate("newAutoRenewAccount"),
                        cryptoCreate("payer").balance(PAYER_BALANCE),
                        createTopic("testTopic").adminKeyName("oldAdminKey").autoRenewAccountId("oldAutoRenewAccount"))
                .when(
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "oldAdminKey"})
                                .hasKnownStatus(INVALID_SIGNATURE),
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "oldAdminKey", "newAdminKey"})
                                .hasKnownStatus(INVALID_SIGNATURE),
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "oldAdminKey", "newAutoRenewAccount"})
                                .hasKnownStatus(INVALID_SIGNATURE),
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "newAdminKey", "newAutoRenewAccount"})
                                .hasKnownStatus(INVALID_SIGNATURE),
                        updateTopicSignedBy
                                .apply(new String[] {"payer", "oldAdminKey", "newAdminKey", "newAutoRenewAccount"})
                                .hasKnownStatus(SUCCESS))
                .then(getTopicInfo("testTopic")
                        .logged()
                        .hasAdminKey("newAdminKey")
                        .hasAutoRenewAccount("newAutoRenewAccount"));
    }

    @HapiTest
    final Stream<DynamicTest> updateSubmitKeyToDiffKey() {
        return defaultHapiSpec("updateSubmitKeyToDiffKey")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("submitKey"),
                        createTopic("testTopic").adminKeyName("adminKey"))
                .when(updateTopic("testTopic").submitKey("submitKey"))
                .then(getTopicInfo("testTopic")
                        .hasSubmitKey("submitKey")
                        .hasAdminKey("adminKey")
                        .logged());
    }

    @HapiTest
    final Stream<DynamicTest> canRemoveSubmitKeyDuringUpdate() {
        return defaultHapiSpec("updateSubmitKeyToDiffKey")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("submitKey"),
                        createTopic("testTopic").adminKeyName("adminKey").submitKeyName("submitKey"))
                .when(
                        submitMessageTo("testTopic").message("message"),
                        updateTopic("testTopic").submitKey(EMPTY_KEY))
                .then(
                        getTopicInfo("testTopic").hasNoSubmitKey().hasAdminKey("adminKey"),
                        submitMessageTo("testTopic").message("message").logged());
    }

    @HapiTest
    final Stream<DynamicTest> updateAdminKeyToDiffKey() {
        return defaultHapiSpec("updateAdminKeyToDiffKey")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("updateAdminKey"),
                        createTopic("testTopic").adminKeyName("adminKey"))
                .when(updateTopic("testTopic").adminKey("updateAdminKey"))
                .then(getTopicInfo("testTopic").hasAdminKey("updateAdminKey").logged());
    }

    @HapiTest
    final Stream<DynamicTest> updateAdminKeyToEmpty() {
        return defaultHapiSpec("updateAdminKeyToEmpty")
                .given(newKeyNamed("adminKey"), createTopic("testTopic").adminKeyName("adminKey"))
                /* if adminKey is empty list should clear adminKey */
                .when(updateTopic("testTopic").adminKey(EMPTY_KEY))
                .then(getTopicInfo("testTopic").hasNoAdminKey().logged());
    }

    @HapiTest
    final Stream<DynamicTest> updateMultipleFields() {
        long expirationTimestamp = Instant.now().getEpochSecond() + 7999990; // more than default.autorenew
        // .secs=7000000
        return defaultHapiSpec("updateMultipleFields")
                .given(
                        newKeyNamed("adminKey"),
                        newKeyNamed("adminKey2"),
                        newKeyNamed("submitKey"),
                        cryptoCreate("autoRenewAccount"),
                        cryptoCreate("nextAutoRenewAccount"),
                        createTopic("testTopic")
                                .topicMemo("initialmemo")
                                .adminKeyName("adminKey")
                                .autoRenewPeriod(validAutoRenewPeriod)
                                .autoRenewAccountId("autoRenewAccount"))
                .when(updateTopic("testTopic")
                        .topicMemo("updatedmemo")
                        .submitKey("submitKey")
                        .adminKey("adminKey2")
                        .expiry(expirationTimestamp)
                        .autoRenewPeriod(validAutoRenewPeriod + 5_000L)
                        .autoRenewAccountId("nextAutoRenewAccount")
                        .hasKnownStatus(SUCCESS))
                .then(getTopicInfo("testTopic")
                        .hasMemo("updatedmemo")
                        .hasSubmitKey("submitKey")
                        .hasAdminKey("adminKey2")
                        .hasExpiry(expirationTimestamp)
                        .hasAutoRenewPeriod(validAutoRenewPeriod + 5_000L)
                        .hasAutoRenewAccount("nextAutoRenewAccount")
                        .logged());
    }

    @HapiTest
    final Stream<DynamicTest> expirationTimestampIsValidated() {
        long now = Instant.now().getEpochSecond();
        return defaultHapiSpec("expirationTimestampIsValidated")
                .given(createTopic("testTopic").autoRenewPeriod(validAutoRenewPeriod))
                .when()
                .then(
                        updateTopic("testTopic")
                                .expiry(now - 1) // less than consensus time
                                .hasKnownStatusFrom(INVALID_EXPIRATION_TIME, EXPIRATION_REDUCTION_NOT_ALLOWED),
                        updateTopic("testTopic")
                                .expiry(now + 1000) // 1000 < autoRenewPeriod
                                .hasKnownStatus(EXPIRATION_REDUCTION_NOT_ALLOWED));
    }

    /* If admin key is not set, only expiration timestamp updates are allowed */
    @HapiTest
    final Stream<DynamicTest> updateExpiryOnTopicWithNoAdminKey() {
        return hapiTest(
                createTopic("testTopic"), doSeveralWithStartupConfigNow("entities.maxLifetime", (value, now) -> {
                    final var maxLifetime = Long.parseLong(value);
                    final var newExpiry = now.getEpochSecond() + maxLifetime - 12_345L;
                    final var excessiveExpiry = now.getEpochSecond() + maxLifetime + 12_345L;
                    return new SpecOperation[] {
                        updateTopic("testTopic").expiry(excessiveExpiry).hasKnownStatus(INVALID_EXPIRATION_TIME),
                        updateTopic("testTopic").expiry(newExpiry),
                        getTopicInfo("testTopic").hasExpiry(newExpiry)
                    };
                }));
    }

    @HapiTest
    final Stream<DynamicTest> clearingAdminKeyWhenAutoRenewAccountPresent() {
        return defaultHapiSpec("clearingAdminKeyWhenAutoRenewAccountPresent")
                .given(
                        newKeyNamed("adminKey"),
                        cryptoCreate("autoRenewAccount"),
                        createTopic("testTopic").adminKeyName("adminKey").autoRenewAccountId("autoRenewAccount"))
                .when(
                        updateTopic("testTopic").adminKey(EMPTY_KEY).hasKnownStatus(AUTORENEW_ACCOUNT_NOT_ALLOWED),
                        updateTopic("testTopic").adminKey(EMPTY_KEY).autoRenewAccountId("0.0.0"))
                .then(getTopicInfo("testTopic").hasNoAdminKey());
    }

    @HapiTest
    final Stream<DynamicTest> updateSubmitKeyOnTopicWithNoAdminKeyFails() {
        return defaultHapiSpec("updateSubmitKeyOnTopicWithNoAdminKeyFails")
                .given(newKeyNamed("submitKey"), createTopic("testTopic"))
                .when(updateTopic("testTopic").submitKey("submitKey").hasKnownStatus(UNAUTHORIZED))
                .then();
    }

    @HapiTest
    final Stream<DynamicTest> feeAsExpected() {
        return defaultHapiSpec("feeAsExpected")
                .given(
                        cryptoCreate("autoRenewAccount"),
                        cryptoCreate("payer"),
                        createTopic("testTopic")
                                .autoRenewAccountId("autoRenewAccount")
                                .autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 1)
                                .adminKeyName("payer"))
                .when(updateTopic("testTopic")
                        .payingWith("payer")
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .via("updateTopic"))
                .then(validateChargedUsdWithin("updateTopic", 0.00022, 3.0));
    }
}
