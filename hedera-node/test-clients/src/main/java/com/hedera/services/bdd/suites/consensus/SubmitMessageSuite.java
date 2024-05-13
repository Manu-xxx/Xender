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
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTopicId;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.submitModified;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.mod.ModificationUtils.withSuccessivelyVariedBodyIds;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_MESSAGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;

@HapiTestSuite
public class SubmitMessageSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(SubmitMessageSuite.class);

    public static void main(String... args) {
        new SubmitMessageSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<DynamicTest> getSpecsInSuite() {
        return List.of(
                pureCheckFails(),
                topicIdIsValidated(),
                messageIsValidated(),
                messageSubmissionSimple(),
                messageSubmissionIncreasesSeqNo(),
                messageSubmissionWithSubmitKey(),
                messageSubmissionMultiple(),
                messageSubmissionOverSize(),
                messageSubmissionCorrectlyUpdatesRunningHash(),
                feeAsExpected());
    }

    @HapiTest
    final DynamicTest pureCheckFails() {
        return defaultHapiSpec("testTopic")
                .given(cryptoCreate("nonTopicId"))
                .when()
                .then(
                        submitMessageTo(spec -> asTopicId(spec.registry().getAccountID("nonTopicId")))
                                .hasPrecheck(INVALID_TOPIC_ID),
                        submitMessageTo((String) null).hasPrecheck(INVALID_TOPIC_ID));
    }

    @HapiTest
    final DynamicTest idVariantsTreatedAsExpected() {
        return defaultHapiSpec("idVariantsTreatedAsExpected")
                .given(createTopic("testTopic"))
                .when()
                .then(submitModified(withSuccessivelyVariedBodyIds(), () -> submitMessageTo("testTopic")
                        .message("HI")));
    }

    @HapiTest
    final DynamicTest topicIdIsValidated() {
        return defaultHapiSpec("topicIdIsValidated")
                .given(cryptoCreate("nonTopicId"))
                .when()
                .then(
                        submitMessageTo((String) null)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(INVALID_TOPIC_ID),
                        submitMessageTo("1.2.3").hasRetryPrecheckFrom(BUSY).hasKnownStatus(INVALID_TOPIC_ID),
                        submitMessageTo(spec -> asTopicId(spec.registry().getAccountID("nonTopicId")))
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(INVALID_TOPIC_ID));
    }

    @HapiTest
    final DynamicTest messageIsValidated() {
        return defaultHapiSpec("messageIsValidated")
                .given(createTopic("testTopic"))
                .when()
                .then(
                        submitMessageTo("testTopic")
                                .clearMessage()
                                .hasRetryPrecheckFrom(BUSY)
                                .hasPrecheck(INVALID_TOPIC_MESSAGE),
                        submitMessageTo("testTopic")
                                .message("")
                                .hasRetryPrecheckFrom(BUSY)
                                .hasPrecheck(INVALID_TOPIC_MESSAGE));
    }

    @HapiTest
    final DynamicTest messageSubmissionSimple() {
        return defaultHapiSpec("messageSubmissionSimple")
                .given(
                        newKeyNamed("submitKey"),
                        createTopic("testTopic").submitKeyName("submitKey").hasRetryPrecheckFrom(BUSY))
                .when(cryptoCreate("civilian"))
                .then(submitMessageTo("testTopic")
                        .message("testmessage")
                        .payingWith("civilian")
                        .hasRetryPrecheckFrom(BUSY)
                        .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final DynamicTest messageSubmissionIncreasesSeqNo() {
        KeyShape submitKeyShape = threshOf(2, SIMPLE, SIMPLE, listOf(2));

        return defaultHapiSpec("messageSubmissionIncreasesSeqNo")
                .given(createTopic("testTopic").submitKeyShape(submitKeyShape))
                .when(
                        getTopicInfo("testTopic").hasSeqNo(0),
                        submitMessageTo("testTopic").message("Hello world!").hasRetryPrecheckFrom(BUSY))
                .then(getTopicInfo("testTopic").hasSeqNo(1));
    }

    @HapiTest
    final DynamicTest messageSubmissionWithSubmitKey() {
        KeyShape submitKeyShape = threshOf(2, SIMPLE, SIMPLE, listOf(2));

        SigControl validSig = submitKeyShape.signedWith(sigs(ON, OFF, sigs(ON, ON)));
        SigControl invalidSig = submitKeyShape.signedWith(sigs(ON, OFF, sigs(ON, OFF)));

        return defaultHapiSpec("messageSubmissionWithSubmitKey")
                .given(
                        newKeyNamed("submitKey").shape(submitKeyShape),
                        createTopic("testTopic").submitKeyName("submitKey"))
                .when()
                .then(
                        submitMessageTo("testTopic")
                                .sigControl(forKey("testTopicSubmit", invalidSig))
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(INVALID_SIGNATURE),
                        submitMessageTo("testTopic")
                                .sigControl(forKey("testTopicSubmit", validSig))
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(SUCCESS));
    }

    @HapiTest
    final DynamicTest messageSubmissionMultiple() {
        final int numMessages = 10;

        return defaultHapiSpec("messageSubmissionMultiple")
                .given(createTopic("testTopic").hasRetryPrecheckFrom(BUSY))
                .when(inParallel(asOpArray(
                        numMessages,
                        i -> submitMessageTo("testTopic").message("message").hasRetryPrecheckFrom(BUSY))))
                .then(sleepFor(1000), getTopicInfo("testTopic").hasSeqNo(numMessages));
    }

    @HapiTest
    final DynamicTest messageSubmissionOverSize() {
        final byte[] messageBytes = new byte[4096]; // 4k
        Arrays.fill(messageBytes, (byte) 0b1);

        return defaultHapiSpec("messageSubmissionOverSize")
                .given(
                        newKeyNamed("submitKey"),
                        createTopic("testTopic").submitKeyName("submitKey").hasRetryPrecheckFrom(BUSY))
                .when()
                .then(submitMessageTo("testTopic")
                        .message(new String(messageBytes))
                        // In hedera-app we don't enforce such prechecks
                        .hasPrecheckFrom(TRANSACTION_OVERSIZE, BUSY, OK)
                        .hasKnownStatus(MESSAGE_SIZE_TOO_LARGE));
    }

    @HapiTest
    final DynamicTest feeAsExpected() {
        final byte[] messageBytes = new byte[100]; // 4k
        Arrays.fill(messageBytes, (byte) 0b1);
        return defaultHapiSpec("feeAsExpected")
                .given(
                        cryptoCreate("payer").hasRetryPrecheckFrom(BUSY),
                        createTopic("testTopic").submitKeyName("payer").hasRetryPrecheckFrom(BUSY))
                .when(submitMessageTo("testTopic")
                        .blankMemo()
                        .payingWith("payer")
                        .message(new String(messageBytes))
                        .hasRetryPrecheckFrom(BUSY)
                        .via("submitMessage"))
                .then(sleepFor(1000), validateChargedUsd("submitMessage", 0.0001));
    }

    @HapiTest
    final DynamicTest messageSubmissionCorrectlyUpdatesRunningHash() {
        String topic = "testTopic";
        String message1 = "Hello world!";
        String message2 = "Hello world again!";
        String nonsense = "Nonsense";
        String message3 = "Goodbye!";

        return defaultHapiSpec("messageSubmissionCorrectlyUpdatesRunningHash")
                .given(
                        createTopic(topic),
                        getTopicInfo(topic)
                                .hasSeqNo(0)
                                .hasRunningHash(new byte[48])
                                .saveRunningHash())
                .when(submitMessageTo(topic)
                        .message(message1)
                        .hasRetryPrecheckFrom(BUSY)
                        .via("submitMessage1"))
                .then(
                        getTxnRecord("submitMessage1").hasCorrectRunningHash(topic, message1),
                        submitMessageTo(topic)
                                .message(message2)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("submitMessage2"),
                        getTxnRecord("submitMessage2").hasCorrectRunningHash(topic, message2),
                        submitMessageTo(topic)
                                .message(nonsense)
                                .via("nonsense")
                                .chunkInfo(3, 4)
                                .hasRetryPrecheckFrom(BUSY)
                                .hasKnownStatus(INVALID_CHUNK_NUMBER),
                        getTxnRecord("nonsense")
                                .hasCorrectRunningHash(topic, message2)
                                .logged(),
                        submitMessageTo(topic)
                                .message(message3)
                                .hasRetryPrecheckFrom(BUSY)
                                .via("submitMessage3"),
                        getTxnRecord("submitMessage3").hasCorrectRunningHash(topic, message3));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
