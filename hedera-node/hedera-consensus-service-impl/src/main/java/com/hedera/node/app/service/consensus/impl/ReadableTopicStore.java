/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.consensus.impl;

import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TopicID;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Topics.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableTopicStore {
    /** The underlying data storage class that holds the topic data. */
    private final ReadableKVState<Long, MerkleTopic> topicState;

    /**
     * Create a new {@link ReadableTopicStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableTopicStore(@NonNull final ReadableStates states) {
        this.topicState = states.get("TOPICS");
    }

    /**
     * Returns the topic metadata needed. If the topic doesn't exist
     * returns failureReason. If the topic exists , the failure reason will be null.
     *
     * @param id topic id being looked up
     * @return topic's metadata
     */
    public TopicMetaOrLookupFailureReason getTopicMetadata(final TopicID id) {
        final var topic = getTopicLeaf(id);

        if (topic.isEmpty()) {
            return new TopicMetaOrLookupFailureReason(null, INVALID_TOPIC_ID);
        }
        return new TopicMetaOrLookupFailureReason(topicMetaFrom(topic.get()), null);
    }

    private Optional<MerkleTopic> getTopicLeaf(TopicID id) {
        final var topic = topicState.get(id.getTopicNum());
        return Optional.ofNullable(topic);
    }

    private TopicMetadata topicMetaFrom(final MerkleTopic topic) {
        return new TopicMetadata(
                Optional.ofNullable(topic.getMemo()),
                Optional.ofNullable(topic.getAdminKey()),
                Optional.ofNullable(topic.getSubmitKey()),
                topic.getAutoRenewDurationSeconds(),
                topic.getAutoRenewAccountId().num() == 0 ? Optional.empty() : Optional.of(topic.getAutoRenewAccountId().num()),
                topic.getExpirationTimestamp().toGrpc(),
                topic.getSequenceNumber(),
                topic.getRunningHash(),
                topic.getKey().longValue(),
                topic.isDeleted());
    }

    public record TopicMetadata(Optional<String> memo,
                                Optional<HederaKey> adminKey,
                                Optional<HederaKey> submitKey,
                                long autoRenewDurationSeconds,
                                Optional<Long> autoRenewAccountId,
                                Timestamp expirationTimestamp,
                                long sequenceNumber,
                                byte[] runningHash,
                                long key,
                                boolean isDeleted) {
    }

    public record TopicMetaOrLookupFailureReason(
            TopicMetadata metadata, ResponseCodeEnum failureReason) {
        public boolean failed() {
            return failureReason != null;
        }
    }
}
