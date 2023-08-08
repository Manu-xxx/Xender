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

package com.hedera.node.app.service.contract.impl.exec;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.annotations.InitialTokenServiceApi;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.utils.ActionStack;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HandleContextHevmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.EvmFrameStateFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ScopedEvmFrameStateFactory;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.swirlds.config.api.Configuration;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Supplier;

@Module(includes = {TransactionSystemContractModule.class})
public interface TransactionModule {
    @Provides
    @TransactionScope
    static Configuration provideConfiguration(@NonNull final HandleContext context) {
        return requireNonNull(context).configuration();
    }

    @Provides
    @TransactionScope
    static ContractsConfig provideContractsConfig(@NonNull final Configuration configuration) {
        return requireNonNull(configuration).getConfigData(ContractsConfig.class);
    }

    @Provides
    @TransactionScope
    static LedgerConfig provideLedgerConfig(@NonNull final Configuration configuration) {
        return requireNonNull(configuration).getConfigData(LedgerConfig.class);
    }

    @Provides
    @TransactionScope
    static StakingConfig provideStakingConfig(@NonNull final Configuration configuration) {
        return requireNonNull(configuration).getConfigData(StakingConfig.class);
    }

    @Provides
    @TransactionScope
    static Instant provideConsensusTime(@NonNull final HandleContext context) {
        return requireNonNull(context).consensusNow();
    }

    @Provides
    @TransactionScope
    static ActionSidecarContentTracer provideActionSidecarContentTracer() {
        return new EvmActionTracer(new ActionStack());
    }

    @Provides
    @TransactionScope
    static HederaEvmContext provideHederaEvmContext(
            @NonNull final HederaOperations extWorldScope, @NonNull final HederaEvmBlocks hederaEvmBlocks) {
        return new HederaEvmContext(extWorldScope.gasPriceInTinybars(), false, hederaEvmBlocks);
    }

    @Provides
    @TransactionScope
    static Supplier<HederaWorldUpdater> provideFeesOnlyUpdater(
            @NonNull final HederaOperations extWorldScope, @NonNull final EvmFrameStateFactory factory) {
        return () -> new ProxyWorldUpdater(requireNonNull(extWorldScope), requireNonNull(factory), null);
    }

    @Provides
    @TransactionScope
    static AttributeValidator provideAttributeValidator(@NonNull final HandleContext context) {
        return context.attributeValidator();
    }

    @Provides
    @TransactionScope
    static ExpiryValidator provideExpiryValidator(@NonNull final HandleContext context) {
        return context.expiryValidator();
    }

    @Provides
    @TransactionScope
    static ReadableFileStore provideReadableFileStore(@NonNull final HandleContext context) {
        return context.readableStore(ReadableFileStore.class);
    }

    @Provides
    @TransactionScope
    static ReadableAccountStore provideReadableAccountStore(@NonNull final HandleContext context) {
        return context.readableStore(ReadableAccountStore.class);
    }

    @Provides
    @TransactionScope
    @InitialTokenServiceApi
    static TokenServiceApi provideInitialTokenServiceApi(@NonNull final HandleContext context) {
        return context.serviceApi(TokenServiceApi.class);
    }

    @Provides
    @TransactionScope
    static NetworkInfo provideNetworkInfo(@NonNull final HandleContext context) {
        return context.networkInfo();
    }

    @Binds
    @TransactionScope
    EvmFrameStateFactory bindEvmFrameStateFactory(ScopedEvmFrameStateFactory factory);

    @Binds
    @TransactionScope
    HederaOperations bindExtWorldScope(HandleHederaOperations handleExtWorldScope);

    @Binds
    @TransactionScope
    HederaNativeOperations bindExtFrameScope(HandleHederaNativeOperations handleExtFrameScope);

    @Binds
    @TransactionScope
    HederaEvmBlocks bindHederaEvmBlocks(HandleContextHevmBlocks handleContextHevmBlocks);
}
