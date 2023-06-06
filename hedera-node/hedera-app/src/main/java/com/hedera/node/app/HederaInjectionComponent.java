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

package com.hedera.node.app;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.annotations.MaxSignedTxnSize;
import com.hedera.node.app.annotations.NodeSelfId;
import com.hedera.node.app.authorization.AuthorizerInjectionModule;
import com.hedera.node.app.components.IngestInjectionComponent;
import com.hedera.node.app.components.QueryInjectionComponent;
import com.hedera.node.app.fees.FeesInjectionModule;
import com.hedera.node.app.grpc.GrpcInjectionModule;
import com.hedera.node.app.grpc.GrpcServerManager;
import com.hedera.node.app.info.CurrentPlatformStatus;
import com.hedera.node.app.info.InfoInjectionModule;
import com.hedera.node.app.metrics.MetricsInjectionModule;
import com.hedera.node.app.platform.PlatformModule;
import com.hedera.node.app.service.mono.LegacyMonoInjectionModule;
import com.hedera.node.app.service.mono.context.annotations.BootstrapProps;
import com.hedera.node.app.service.mono.context.annotations.StaticAccountMemo;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.utils.NamedDigestFactory;
import com.hedera.node.app.service.mono.utils.SystemExits;
import com.hedera.node.app.services.ServicesInjectionModule;
import com.hedera.node.app.services.ServicesRegistry;
import com.hedera.node.app.solvency.SolvencyInjectionModule;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.state.HederaStateInjectionModule;
import com.hedera.node.app.state.LedgerValidator;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.throttle.ThrottleInjectionModule;
import com.hedera.node.app.workflows.WorkflowsInjectionModule;
import com.hedera.node.app.workflows.handle.HandleWorkflow;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import dagger.BindsInstance;
import dagger.Component;
import java.nio.charset.Charset;
import java.util.function.Supplier;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * The infrastructure used to implement the platform contract for a Hedera Services node. This is needed for adding
 * dagger subcomponents. Currently, it extends {@link com.hedera.node.app.service.mono.ServicesApp}. But, in the future
 * this class will be cleaned up to not have multiple module dependencies
 */
@Singleton
@Component(
        modules = {
            LegacyMonoInjectionModule.class,
            ServicesInjectionModule.class,
            WorkflowsInjectionModule.class,
            HederaStateInjectionModule.class,
            FeesInjectionModule.class,
            GrpcInjectionModule.class,
            MetricsInjectionModule.class,
            AuthorizerInjectionModule.class,
            InfoInjectionModule.class,
            ThrottleInjectionModule.class,
            SolvencyInjectionModule.class,
            PlatformModule.class
        })
public interface HederaInjectionComponent {
    /* Needed by ServicesState */
    Provider<QueryInjectionComponent.Factory> queryComponentFactory();

    Provider<IngestInjectionComponent.Factory> ingestComponentFactory();

    WorkingStateAccessor workingStateAccessor();

    RecordCache recordCache();

    GrpcServerManager grpcServerManager();

    NodeId nodeId();

    Supplier<Charset> nativeCharset();

    SystemExits systemExits();

    NamedDigestFactory digestFactory();

    NetworkInfo networkInfo();

    LedgerValidator ledgerValidator();

    PreHandleWorkflow preHandleWorkflow();

    HandleWorkflow handleWorkflow();

    @Component.Builder
    interface Builder {
        @BindsInstance
        Builder servicesRegistry(ServicesRegistry registry);

        @BindsInstance
        Builder initTrigger(InitTrigger initTrigger);

        @BindsInstance
        Builder crypto(Cryptography engine);

        @BindsInstance
        Builder initialHash(Hash initialHash);

        @BindsInstance
        Builder platform(Platform platform);

        @BindsInstance
        Builder selfId(@NodeSelfId final AccountID selfId);

        @BindsInstance
        Builder staticAccountMemo(@StaticAccountMemo String accountMemo);

        @BindsInstance
        Builder bootstrapProps(@BootstrapProps PropertySource bootstrapProps);

        @BindsInstance
        Builder configuration(ConfigProvider configProvider);

        @BindsInstance
        Builder maxSignedTxnSize(@MaxSignedTxnSize final int maxSignedTxnSize);

        @BindsInstance
        Builder currentPlatformStatus(CurrentPlatformStatus currentPlatformStatus);

        HederaInjectionComponent build();
    }
}
