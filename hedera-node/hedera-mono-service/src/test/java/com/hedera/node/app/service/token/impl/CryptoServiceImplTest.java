/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.spi.state.StateKey.ACCOUNT_STORE;
import static com.hedera.node.app.spi.state.StateKey.ALIASES_STORE;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.spi.state.States;
import com.hedera.node.app.spi.state.impl.InMemoryStateImpl;
import com.hedera.node.app.spi.state.impl.RebuiltStateImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoServiceImplTest {
    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;
    @Mock States states;

    private CryptoServiceImpl subject;

    @Test
    void createsNewInstance() {
        subject = new CryptoServiceImpl();

        given(states.get(ACCOUNT_STORE)).willReturn(accounts);
        given(states.get(ALIASES_STORE)).willReturn(aliases);

        final var serviceImpl = subject.createPreTransactionHandler(states);
        final var serviceImpl1 = subject.createPreTransactionHandler(states);
        assertNotEquals(serviceImpl1, serviceImpl);
    }
}
