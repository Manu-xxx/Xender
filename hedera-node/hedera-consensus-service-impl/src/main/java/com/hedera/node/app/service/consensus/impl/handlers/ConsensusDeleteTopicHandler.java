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

package com.hedera.node.app.service.consensus.impl.handlers;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNAUTHORIZED;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.records.ConsensusDeleteTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.DeleteTopicRecordBuilder;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ConsensusDeleteTopic}.
 */
@Singleton
public class ConsensusDeleteTopicHandler implements TransactionHandler {
    @Inject
    public ConsensusDeleteTopicHandler() {}

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Determines signatures needed for deleting a consensus topic
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@code handle()}
     * @param topicStore the {@link ReadableTopicStore} to use to resolve topic metadata
     * @throws NullPointerException if any of the arguments are {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context, @NonNull ReadableTopicStore topicStore) {
        requireNonNull(context);
        requireNonNull(topicStore);

        final var op = context.getTxn().getConsensusDeleteTopic();
        final var topicMeta = topicStore.getTopicMetadata(op.getTopicID());
        if (topicMeta.failed()) {
            context.status(ResponseCodeEnum.INVALID_TOPIC_ID);
            return;
        }

        final var adminKey = topicMeta.metadata().adminKey();
        if (adminKey.isEmpty()) {
            context.status(ResponseCodeEnum.UNAUTHORIZED);
            return;
        }

        context.addToReqNonPayerKeys(adminKey.get());
    }

    /**
     * Given the appropriate context, deletes a topic.
     *
     * @param op the {@link ConsensusDeleteTopicTransactionBody} of the active transaction
     * @param topicStore the {@link WritableTopicStore} to use to delete the topic
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(
            @NonNull final ConsensusDeleteTopicTransactionBody op, @NonNull final WritableTopicStore topicStore) {
        var topicId = op.getTopicID();

        var optionalTopic = topicStore.get(topicId.getTopicNum());

        /* If the topic doesn't exist, return INVALID_TOPIC_ID */
        if (optionalTopic.isEmpty()) {
            throw new HandleStatusException(INVALID_TOPIC_ID);
        }
        final var topic = optionalTopic.get();

        /* Topics without adminKeys can't be deleted.*/
        if (topic.adminKey() == null) {
            throw new HandleStatusException(UNAUTHORIZED);
        }

        /* Copy all the fields from existing topic and change deleted flag */
        final var topicBuilder = new Topic.Builder()
                .topicNumber(topic.topicNumber())
                .adminKey(topic.adminKey())
                .submitKey(topic.submitKey())
                .autoRenewAccountNumber(topic.autoRenewAccountNumber())
                .autoRenewPeriod(topic.autoRenewPeriod())
                .expiry(topic.expiry())
                .memo(topic.memo())
                .runningHash(topic.runningHash())
                .sequenceNumber(topic.sequenceNumber());
        topicBuilder.deleted(true);

        /* --- Put the modified topic. It will be in underlying state's modifications map.
        It will not be committed to state until commit is called on the state.--- */
        topicStore.put(topicBuilder.build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConsensusDeleteTopicRecordBuilder newRecordBuilder() {
        return new DeleteTopicRecordBuilder();
    }
}
