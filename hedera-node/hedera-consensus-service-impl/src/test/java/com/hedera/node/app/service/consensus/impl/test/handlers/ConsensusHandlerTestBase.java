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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.consensus.entity.Topic;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.entity.TopicBuilderImpl;
import com.hedera.node.app.service.consensus.impl.entity.TopicImpl;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.QueryContext;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ConsensusHandlerTestBase {
    protected static final String TOPICS = "TOPICS";
    protected final Key key = A_COMPLEX_KEY;
    protected final String payerId = "0.0.3";
    protected final AccountID payer = asAccount(payerId);
    protected final AccountID autoRenewId = asAccount("0.0.4");
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().setSeconds(1_234_567L).build();

    protected final HederaKey hederaKey = asHederaKey(Key.newBuilder()
                    .setEd25519(ByteString.copyFrom("01234567890123456789012345678901".getBytes()))
                    .build())
            .get();
    protected final HederaKey adminKey = asHederaKey(key).get();
    protected final Long payerNum = payer.getAccountNum();
    protected final EntityNum topicEntityNum = EntityNum.fromLong(1L);
    protected final TopicID topicId =
            TopicID.newBuilder().setTopicNum(topicEntityNum.longValue()).build();
    protected final String beneficiaryIdStr = "0.0.3";
    protected final long paymentAmount = 1_234L;
    protected final ByteString ledgerId = ByteString.copyFromUtf8("0x03");
    protected final String memo = "test memo";
    protected final long expirationTime = 1_234_567L;
    protected final long sequenceNumber = 1L;
    protected final long autoRenewSecs = 100L;

    @Mock
    protected MerkleTopic topic;

    @Mock
    protected ReadableStates readableStates;

    @Mock
    protected WritableStates writableStates;

    @Mock
    protected QueryContext queryContext;

    protected MapReadableKVState<EntityNum, MerkleTopic> readableTopicState;
    protected MapWritableKVState<EntityNum, MerkleTopic> writableTopicState;

    protected ReadableTopicStore readableStore;
    protected WritableTopicStore writableStore;

    @BeforeEach
    void commonSetUp() {
        readableTopicState = readableTopicState();
        writableTopicState = emptyWritableTopicState();
        given(readableStates.<EntityNum, MerkleTopic>get(TOPICS)).willReturn(readableTopicState);
        given(writableStates.<EntityNum, MerkleTopic>get(TOPICS)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStore(readableStates);
        writableStore = new WritableTopicStore(writableStates);
    }

    @NonNull
    protected MapWritableKVState<EntityNum, MerkleTopic> emptyWritableTopicState() {
        return MapWritableKVState.<EntityNum, MerkleTopic>builder("TOPICS").build();
    }

    @NonNull
    protected MapWritableKVState<EntityNum, MerkleTopic> writableTopicStateWithOneKey() {
        return MapWritableKVState.<EntityNum, MerkleTopic>builder("TOPICS")
                .value(topicEntityNum, topic)
                .build();
    }

    @NonNull
    protected MapReadableKVState<EntityNum, MerkleTopic> readableTopicState() {
        return MapReadableKVState.<EntityNum, MerkleTopic>builder("TOPICS")
                .value(topicEntityNum, topic)
                .build();
    }

    protected void givenValidTopic() {
        given(topic.getMemo()).willReturn(memo);
        given(topic.getAdminKey()).willReturn((JKey) adminKey);
        given(topic.getSubmitKey()).willReturn((JKey) adminKey);
        given(topic.getAutoRenewDurationSeconds()).willReturn(autoRenewSecs);
        given(topic.getAutoRenewAccountId()).willReturn(EntityId.fromGrpcAccountId(autoRenewId));
        given(topic.getExpirationTimestamp()).willReturn(RichInstant.MISSING_INSTANT);
        given(topic.getSequenceNumber()).willReturn(sequenceNumber);
        given(topic.getRunningHash()).willReturn(new byte[48]);
        given(topic.getKey()).willReturn(topicEntityNum);
        given(topic.isDeleted()).willReturn(false);
    }

    protected Topic createTopic() {
        return new TopicBuilderImpl()
                .topicNumber(topicId.getTopicNum())
                .adminKey(asHederaKey(key).get())
                .submitKey(asHederaKey(key).get())
                .autoRenewSecs(autoRenewSecs)
                .autoRenewAccountNumber(autoRenewId.getAccountNum())
                .expiry(expirationTime)
                .sequenceNumber(sequenceNumber)
                .memo(memo)
                .deleted(true)
                .build();
    }

    protected TopicImpl setUpTopicImpl() {
        return new TopicImpl(
                topicId.getTopicNum(),
                hederaKey,
                hederaKey,
                memo,
                autoRenewId.getAccountNum(),
                autoRenewSecs,
                expirationTime,
                true,
                sequenceNumber);
    }
}
