/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.TransactionConfigModule;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionConfigModuleTest {
    @Mock
    private HandleContext context;

    @Test
    void providesExpectedConfig() {
        final var config = HederaTestConfigBuilder.create().getOrCreateConfig();
        given(context.configuration()).willReturn(config);
        assertSame(config, TransactionConfigModule.provideConfiguration(context));
        assertNotNull(TransactionConfigModule.provideContractsConfig(config));
        assertNotNull(TransactionConfigModule.provideLedgerConfig(config));
        assertNotNull(TransactionConfigModule.provideStakingConfig(config));
        assertNotNull(TransactionConfigModule.provideHederaConfig(config));
        assertNotNull(TransactionConfigModule.provideEntitiesConfig(config));
    }
}
