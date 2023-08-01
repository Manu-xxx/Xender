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

package com.hedera.node.app.service.contract.impl.test.infra;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.infra.LegibleStorageManager;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegibleStorageManagerTest {
    @Mock
    private HederaOperations extWorldScope;

    @Mock
    private ContractStateStore store;

    private final LegibleStorageManager subject = new LegibleStorageManager();

    @Test
    void rewriteIsNoopForNow() {
        assertDoesNotThrow(() -> subject.rewrite(extWorldScope, List.of(), List.of(), store));
    }
}
