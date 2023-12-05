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

package com.hedera.node.app.service.contract.impl.test.hevm;

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.GAS_LIMIT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.WEI_NETWORK_GAS_PRICE;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmTransactionAbortResultTests {
    @Mock
    private RootProxyWorldUpdater rootProxyWorldUpdater;

    @Test
    void translatesResourceExhaustionResultForContractStorageExceeded() {
        final var subject = HederaEvmTransactionResult.resourceExhaustionFrom(
                SENDER_ID,
                GAS_LIMIT / 2,
                WEI_NETWORK_GAS_PRICE.toLong(),
                ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED);
        assertEquals(ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED, subject.finalStatus());
        final var protoResult = subject.asProtoResultOf(rootProxyWorldUpdater);
        final var traditionalMessage = Bytes.wrap(
                        MAX_CONTRACT_STORAGE_EXCEEDED.protoName().getBytes(StandardCharsets.UTF_8))
                .toString();
        assertEquals(traditionalMessage, protoResult.errorMessage());
    }

    @Test
    void translatesResourceExhaustionResultForTotalStorageExceeded() {
        final var subject = HederaEvmTransactionResult.resourceExhaustionFrom(
                SENDER_ID, GAS_LIMIT / 2, WEI_NETWORK_GAS_PRICE.toLong(), MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED);
        assertEquals(MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED, subject.finalStatus());
        final var protoResult = subject.asProtoResultOf(rootProxyWorldUpdater);
        final var traditionalMessage = Bytes.wrap(
                        MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED.protoName().getBytes(StandardCharsets.UTF_8))
                .toString();
        assertEquals(traditionalMessage, protoResult.errorMessage());
    }
}
