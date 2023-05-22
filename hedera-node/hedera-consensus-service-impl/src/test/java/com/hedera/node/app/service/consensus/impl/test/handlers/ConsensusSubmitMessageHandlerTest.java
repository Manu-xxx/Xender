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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler.noThrowSha384HashOf;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_A;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.asBytes;
import static com.hedera.node.app.service.mono.state.merkle.MerkleTopic.RUNNING_HASH_VERSION;
import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusMessageChunkInfo;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStoreImpl;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.utils.TxnUtils;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusSubmitMessageHandlerTest extends ConsensusHandlerTestBase {
    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private HandleContext handleContext;

    private ConsensusServiceConfig config;

    private ConsensusSubmitMessageHandler subject;

    @BeforeEach
    void setUp() {
        commonSetUp();
        subject = new ConsensusSubmitMessageHandler();
        config = new ConsensusServiceConfig(10L, 100);

        writableTopicState = writableTopicStateWithOneKey();
        given(readableStates.<EntityNum, Topic>get(TOPICS_KEY)).willReturn(readableTopicState);
        given(writableStates.<EntityNum, Topic>get(TOPICS_KEY)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStoreImpl(readableStates);
        writableStore = new WritableTopicStore(writableStates);
    }

    @Test
    @DisplayName("Topic submission key sig required")
    void submissionKeySigRequired() throws PreCheckException {
        readableStore = mock(ReadableTopicStore.class);
        // given:
        final var payerKey = mockPayerLookup();
        mockTopicLookup(SIMPLE_KEY_A);
        final var context = new FakePreHandleContext(accountStore, newDefaultSubmitMessageTxn(topicEntityNum));
        context.registerStore(ReadableTopicStore.class, readableStore);

        // when:
        assertDoesNotThrow(() -> subject.preHandle(context));

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(SIMPLE_KEY_A);
    }

    @Test
    @DisplayName("Topic not found returns error")
    void topicIdNotFound() throws PreCheckException {
        mockPayerLookup();
        readableTopicState = emptyReadableTopicState();
        given(readableStates.<EntityNum, Topic>get(TOPICS_KEY)).willReturn(readableTopicState);
        readableStore = new ReadableTopicStoreImpl(readableStates);
        final var context = new FakePreHandleContext(accountStore, newDefaultSubmitMessageTxn(topicEntityNum));
        context.registerStore(ReadableTopicStore.class, readableStore);

        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOPIC_ID);
    }

    @Test
    @DisplayName("Topic without submit key does not error")
    void noTopicSubmitKey() throws PreCheckException {
        readableStore = mock(ReadableTopicStore.class);
        mockPayerLookup();
        mockTopicLookup(null);
        final var context = new FakePreHandleContext(accountStore, newDefaultSubmitMessageTxn(topicEntityNum));
        context.registerStore(ReadableTopicStore.class, readableStore);

        // when:
        assertDoesNotThrow(() -> subject.preHandle(context));
    }

    @Test
    @DisplayName("Correct RecordBuilder type returned")
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(ConsensusSubmitMessageRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    @DisplayName("Handle works as expected")
    void handleWorksAsExpected() {
        givenValidTopic();
        final var txn = newDefaultSubmitMessageTxn(topicEntityNum);

        final var recordBuilder = subject.newRecordBuilder();
        given(handleContext.consensusNow()).willReturn(consensusTimestamp);

        final var initialTopic = writableTopicState.get(topicEntityNum);
        subject.handle(handleContext, txn, config, recordBuilder, writableStore);

        final var expectedTopic = writableTopicState.get(topicEntityNum);
        assertNotEquals(initialTopic, expectedTopic);
        assertEquals(initialTopic.sequenceNumber() + 1, expectedTopic.sequenceNumber());
        assertNotEquals(
                initialTopic.runningHash().toString(),
                expectedTopic.runningHash().toString());
    }

    @Test
    @DisplayName("Handle works as expected if Consensus time is null")
    void handleWorksAsExpectedIfConsensusTimeIsNull() {
        givenValidTopic();
        final var txn = newDefaultSubmitMessageTxn(topicEntityNum);

        final var recordBuilder = subject.newRecordBuilder();
        given(handleContext.consensusNow()).willReturn(null);

        final var initialTopic = writableTopicState.get(topicEntityNum);
        subject.handle(handleContext, txn, config, recordBuilder, writableStore);

        final var expectedTopic = writableTopicState.get(topicEntityNum);
        assertNotEquals(initialTopic, expectedTopic);
        assertEquals(initialTopic.sequenceNumber() + 1, expectedTopic.sequenceNumber());
        assertNotEquals(
                initialTopic.runningHash().toString(),
                expectedTopic.runningHash().toString());
        assertArrayEquals(asBytes(expectedTopic.runningHash()), recordBuilder.getNewTopicRunningHash());
        assertEquals(expectedTopic.sequenceNumber(), recordBuilder.getNewTopicSequenceNumber());
        assertEquals(RUNNING_HASH_VERSION, recordBuilder.getUsedRunningHashVersion());
    }

    @Test
    @DisplayName("Handle fails if submit message is empty")
    void failsIfMessageIsEmpty() {
        givenValidTopic();
        final var txn = newSubmitMessageTxn(topicEntityNum, "");

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_TOPIC_MESSAGE, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if submit message is too large")
    void failsIfMessageIsTooLarge() {
        givenValidTopic();
        final var txn = newSubmitMessageTxn(topicEntityNum, Arrays.toString(TxnUtils.randomUtf8Bytes(2000)));

        final var recordBuilder = subject.newRecordBuilder();
        config = new ConsensusServiceConfig(10, 5);

        final var msg = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if topic for which message is being submitted is not found")
    void failsIfTopicIDInvalid() {
        givenValidTopic();
        final var txn = newDefaultSubmitMessageTxn(MISSING_NUM);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(INVALID_TOPIC_ID, msg.getStatus());
    }

    @Test
    void failsOnUnavailableDigest() {
        final var raw = NONSENSE.toByteArray();
        assertDoesNotThrow(() -> noThrowSha384HashOf(raw));
    }

    @Test
    @DisplayName("Handle fails if submit message chunk number is invalid")
    void failsIfChunkNumberIsInvalid() {
        givenValidTopic();
        final var txn = newSubmitMessageTxnWithChunks(topicEntityNum, 2, 1);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_CHUNK_NUMBER, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if submit message chunk number is less than 1")
    void failsIfChunkNumberInvalid() {
        givenValidTopic();
        final var txn = newSubmitMessageTxnWithChunks(topicEntityNum, 0, 1);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_CHUNK_NUMBER, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if submit message chunk txn payer is not same as initial txn payer")
    void failsIfChunkTxnPayerIsNotInitialPayer() {
        givenValidTopic();
        final var chunkTxnId =
                TransactionID.newBuilder().accountID(anotherPayer).build();
        final var txn = newSubmitMessageTxnWithChunksAndPayer(topicEntityNum, 2, 2, chunkTxnId);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if submit message chunk txn id is not same as initial txn id")
    void failsIfChunkTxnPayerIsNotInitialID() {
        givenValidTopic();
        final var chunkTxnId =
                TransactionID.newBuilder().accountID(anotherPayer).build();
        final var txn = newSubmitMessageTxnWithChunksAndPayer(topicEntityNum, 1, 2, chunkTxnId);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID, msg.getStatus());
    }

    /* ----------------- Helper Methods ------------------- */

    private Key mockPayerLookup() {
        return ConsensusTestUtils.mockPayerLookup(A_COMPLEX_KEY, payerId, accountStore);
    }

    private void mockTopicLookup(Key submitKey) {
        ConsensusTestUtils.mockTopicLookup(null, submitKey, readableStore);
    }

    private TransactionBody newDefaultSubmitMessageTxn(final EntityNum topicEntityNum) {
        return newSubmitMessageTxn(
                topicEntityNum,
                "Message for test-" + Instant.now() + "." + Instant.now().getNano());
    }

    private TransactionBody newSubmitMessageTxn(final EntityNum topicEntityNum, final String message) {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var submitMessageBuilder = ConsensusSubmitMessageTransactionBody.newBuilder()
                .topicID(TopicID.newBuilder()
                        .topicNum(topicEntityNum.longValue())
                        .build())
                .message(Bytes.wrap(message));
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .consensusSubmitMessage(submitMessageBuilder.build())
                .build();
    }

    private TransactionBody newSubmitMessageTxnWithChunks(
            final EntityNum topicEntityNum, final int currentChunk, final int totalChunk) {
        return newSubmitMessageTxnWithChunksAndPayer(topicEntityNum, currentChunk, totalChunk, null);
    }

    private TransactionBody newSubmitMessageTxnWithChunksAndPayer(
            final EntityNum topicEntityNum,
            final int currentChunk,
            final int totalChunk,
            final TransactionID initialTxnId) {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var submitMessageBuilder = ConsensusSubmitMessageTransactionBody.newBuilder()
                .topicID(TopicID.newBuilder()
                        .topicNum(topicEntityNum.longValue())
                        .build())
                .chunkInfo(ConsensusMessageChunkInfo.newBuilder()
                        .initialTransactionID(initialTxnId != null ? initialTxnId : txnId)
                        .number(currentChunk)
                        .total(totalChunk)
                        .build())
                .message(Bytes.wrap("test"));
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .consensusSubmitMessage(submitMessageBuilder.build())
                .build();
    }

    private final ByteString NONSENSE = ByteString.copyFromUtf8("NONSENSE");
}
