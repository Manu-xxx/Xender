/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.fees.usage.token;

import static com.hedera.node.app.hapi.fees.usage.token.entities.NftEntitySizes.NFT_ENTITY_SIZES;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.INT_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenGetAccountNftInfosUsageTests {
    private TokenGetAccountNftInfosUsage subject;
    private AccountID id;
    private List<ByteString> metadata;

    @BeforeEach
    void setup() {
        metadata = List.of(ByteString.copyFromUtf8("some metadata"));
        id = AccountID.newBuilder()
                .setShardNum(0)
                .setRealmNum(0)
                .setAccountNum(1)
                .build();
        subject = TokenGetAccountNftInfosUsage.newEstimate(tokenQuery());
    }

    @Test
    void assessesEverything() {
        // given:
        subject.givenMetadata(metadata);

        // when:
        final var usage = subject.get();
        final int additionalRb = metadata.stream().mapToInt(ByteString::size).sum();
        final var expectedBytes =
                BASIC_QUERY_RES_HEADER + NFT_ENTITY_SIZES.fixedBytesInNftRepr() * metadata.size() + additionalRb;

        // then:
        final var node = usage.getNodedata();
        assertEquals(FeeBuilder.BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE + 2 * INT_SIZE, node.getBpt());
        assertEquals(expectedBytes, node.getBpr());
    }

    private Query tokenQuery() {
        final var op =
                TokenGetAccountNftInfosQuery.newBuilder().setAccountID(id).build();
        return Query.newBuilder().setTokenGetAccountNftInfos(op).build();
    }
}
