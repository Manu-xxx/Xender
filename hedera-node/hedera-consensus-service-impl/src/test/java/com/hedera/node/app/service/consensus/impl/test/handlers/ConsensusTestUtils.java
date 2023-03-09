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

import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.pbj.runtime.io.Bytes;
import org.assertj.core.api.Assertions;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.EXISTING_TOPIC;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;

public final class ConsensusTestUtils {

    static final com.hedera.hapi.node.base.Key SIMPLE_KEY_A = com.hedera.hapi.node.base.Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
            .build();
    static final com.hedera.hapi.node.base.Key SIMPLE_KEY_B = com.hedera.hapi.node.base.Key.newBuilder()
            .ed25519(Bytes.wrap("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()))
            .build();
    static final HederaKey A_NONNULL_KEY = new HederaKey() {};

    static final com.hedera.hapi.node.base.AccountID ACCOUNT_ID_4 = com.hedera.hapi.node.base.AccountID
            .newBuilder().accountNum(4L).build();

    private ConsensusTestUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    static void assertOkResponse(PreHandleContext context) {
        assertThat(context.getStatus()).isEqualTo(com.hedera.hapi.node.base.ResponseCodeEnum.OK);
        assertThat(context.failed()).isFalse();
    }

    static HederaKey mockPayerLookup(
            com.hedera.hapi.node.base.Key key,
            com.hedera.hapi.node.base.AccountID accountId, AccountAccess keyLookup) {
        final var returnKey = Utils.asHederaKey(key).orElseThrow();
        given(keyLookup.getKey(accountId)).willReturn(KeyOrLookupFailureReason.withKey(returnKey));
        return returnKey;
    }

    static void assertDefaultPayer(PreHandleContext context) {
        assertPayer(DEFAULT_PAYER_KT.asPbjKey(), context);
    }

    static void assertCustomPayer(PreHandleContext context) {
        assertPayer(CUSTOM_PAYER_ACCOUNT_KT.asPbjKey(), context);
    }

    static void assertPayer(com.hedera.hapi.node.base.Key expected, PreHandleContext context) {
        Assertions.assertThat(sanityRestored(context.getPayerKey())).isEqualTo(expected);
    }

    static void mockTopicLookup(
            com.hedera.hapi.node.base.Key adminKey,
            com.hedera.hapi.node.base.Key submitKey,
            ReadableTopicStore topicStore) {
        given(topicStore.getTopicMetadata(notNull()))
                .willReturn(ReadableTopicStore.TopicMetaOrLookupFailureReason.withTopicMeta(newTopicMeta(
                        adminKey != null ? Utils.asHederaKey(adminKey).get() : null,
                        submitKey != null ? Utils.asHederaKey(submitKey).get() : null)));
    }

    static ReadableTopicStore.TopicMetadata newTopicMeta(HederaKey admin, HederaKey submit) {
        return new ReadableTopicStore.TopicMetadata(
                Optional.of(Instant.now() + ""),
                Optional.ofNullable(admin),
                Optional.ofNullable(submit),
                -1L,
                OptionalLong.of(1234567L),
                null,
                -1,
                null,
                EXISTING_TOPIC.getTopicNum(),
                false);
    }
}
