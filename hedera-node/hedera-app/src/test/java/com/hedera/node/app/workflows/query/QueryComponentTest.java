/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.query;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.DaggerHederaInjectionComponent;
import com.hedera.node.app.HederaInjectionComponent;
import com.hedera.node.app.components.QueryInjectionComponent;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.gui.SwirldsGui;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryComponentTest {

    @Mock
    private Cryptography cryptography;

    @Mock
    private Platform platform;

    private HederaInjectionComponent app;

    @BeforeEach
    void setUp() {
        final var selfNodeId = new NodeId(666L);
        final Configuration configuration = new HederaTestConfigBuilder().getOrCreateConfig();
        final PlatformContext platformContext = mock(PlatformContext.class);
        when(platformContext.getConfiguration()).thenReturn(configuration);
        when(platform.getContext()).thenReturn(platformContext);
        when(platformContext.getCryptography()).thenReturn(cryptography);
        app = DaggerHederaInjectionComponent.builder()
                .initTrigger(InitTrigger.GENESIS)
                .platform(platform)
                .crypto(CryptographyHolder.get())
                .consoleCreator(SwirldsGui::createConsole)
                .staticAccountMemo("memo")
                .bootstrapProps(new BootstrapProperties())
                .selfId(AccountID.newBuilder()
                        .accountNum(selfNodeId.getIdAsInt())
                        .build())
                .initialHash(new Hash())
                .maxSignedTxnSize(1024)
                .genesisUsage(false)
                .build();
    }

    @Test
    void objectGraphRootsAreAvailable() {
        given(platform.getSelfId()).willReturn(new NodeId(0L));

        final QueryInjectionComponent subject =
                app.queryComponentFactory().get().create();

        assertNotNull(subject.queryWorkflow());
    }
}
