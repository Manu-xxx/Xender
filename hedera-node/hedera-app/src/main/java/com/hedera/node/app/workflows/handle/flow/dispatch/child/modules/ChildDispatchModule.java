/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.dispatch.child.modules;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.FinalizeContext;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.dispatcher.WritableStoreFactory;
import com.hedera.node.app.workflows.handle.TriggeredFinalizeContext;
import com.hedera.node.app.workflows.handle.flow.FlowHandleContext;
import com.hedera.node.app.workflows.handle.flow.dispatch.ChildDispatchLogic;
import com.hedera.node.app.workflows.handle.flow.dispatch.DispatchLogic;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchComponent;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildDispatchScope;
import com.hedera.node.app.workflows.handle.flow.dispatch.child.ChildQualifier;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.info.NetworkInfo;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Set;
import javax.inject.Provider;

@Module
public interface ChildDispatchModule {
    @Binds
    @ChildDispatchScope
    HandleContext bindHandleContext(FlowHandleContext handleContext);

    @Binds
    @ChildDispatchScope
    FeeContext bindFeeContext(FlowHandleContext feeContext);

    @Provides
    @ChildDispatchScope
    static Fees provideFees(
            @NonNull FeeContext feeContext,
            @NonNull HandleContext.TransactionCategory childCategory,
            @NonNull TransactionDispatcher dispatcher) {
        if (childCategory != HandleContext.TransactionCategory.SCHEDULED) {
            return Fees.FREE;
        }
        return dispatcher.dispatchComputeFees(feeContext).onlyServiceComponent();
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static ReadableStoreFactory provideReadableStoreFactory(@ChildQualifier SavepointStackImpl stack) {
        return new ReadableStoreFactory(stack);
    }

    @Provides
    @ChildDispatchScope
    static FeeAccumulator provideFeeAccumulator(
            @NonNull SingleTransactionRecordBuilderImpl recordBuilder, @NonNull ServiceApiFactory serviceApiFactory) {
        final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
        return new FeeAccumulatorImpl(tokenApi, recordBuilder);
    }

    @Provides
    @ChildDispatchScope
    static ServiceApiFactory provideServiceApiFactory(
            @ChildQualifier @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        return new ServiceApiFactory(stack, configuration, storeMetricsService);
    }

    @Provides
    @ChildDispatchScope
    @ChildQualifier
    static Key providePayerKey() {
        return Key.DEFAULT;
    }

    @Provides
    @ChildDispatchScope
    static WritableEntityIdStore provideEntityIdStore(
            @ChildQualifier SavepointStackImpl stack,
            Configuration configuration,
            StoreMetricsService storeMetricsService) {
        final var entityIdsFactory =
                new WritableStoreFactory(stack, EntityIdService.NAME, configuration, storeMetricsService);
        return entityIdsFactory.getStore(WritableEntityIdStore.class);
    }

    @Provides
    @ChildDispatchScope
    static WritableStoreFactory provideWritableStoreFactory(
            @ChildQualifier SavepointStackImpl stack,
            @ChildQualifier TransactionInfo txnInfo,
            Configuration configuration,
            ServiceScopeLookup serviceScopeLookup,
            StoreMetricsService storeMetricsService) {
        return new WritableStoreFactory(
                stack, serviceScopeLookup.getServiceName(txnInfo.txBody()), configuration, storeMetricsService);
    }

    @Provides
    @ChildDispatchScope
    static FinalizeContext provideTriggeredFinalizeContext(
            @ChildQualifier @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            @ChildQualifier @NonNull final SavepointStackImpl stack,
            @NonNull final Instant consensusNow,
            @NonNull final Configuration configuration,
            @NonNull final StoreMetricsService storeMetricsService) {
        final var writableStoreFactory =
                new WritableStoreFactory(stack, TokenService.NAME, configuration, storeMetricsService);
        return new TriggeredFinalizeContext(
                readableStoreFactory, writableStoreFactory, recordBuilder, consensusNow, configuration);
    }

    @Provides
    @ChildDispatchScope
    static Set<Key> provideRequiredKeys(@ChildQualifier PreHandleResult preHandleResult) {
        return preHandleResult.requiredKeys();
    }

    @Provides
    @ChildDispatchScope
    static Set<Account> provideHollowAccounts(@ChildQualifier PreHandleResult preHandleResult) {
        return preHandleResult.hollowAccounts();
    }

    @Provides
    @ChildDispatchScope
    static FlowHandleContext provideFlowHandleContext(
            final Instant consensusNow,
            @NonNull @ChildQualifier final TransactionInfo transactionInfo,
            final Configuration configuration,
            final Authorizer authorizer,
            final BlockRecordManager blockRecordManager,
            final FeeManager feeManager,
            @NonNull @ChildQualifier final ReadableStoreFactory storeFactory,
            @NonNull final AccountID syntheticPayer,
            @NonNull final KeyVerifier verifier,
            @NonNull @ChildQualifier final Key payerkey,
            @NonNull final FeeAccumulator feeAccumulator,
            final ExchangeRateManager exchangeRateManager,
            @NonNull @ChildQualifier final SavepointStackImpl stack,
            @NonNull final WritableEntityIdStore entityIdStore,
            final TransactionDispatcher dispatcher,
            final RecordCache recordCache,
            @NonNull final WritableStoreFactory writableStoreFactory,
            @NonNull final ServiceApiFactory serviceApiFactory,
            final NetworkInfo networkInfo,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder,
            final Provider<ChildDispatchComponent.Factory> childDispatchFactory,
            final ChildDispatchLogic childDispatchLogic,
            @NonNull final ChildDispatchComponent dispatch,
            @NonNull final DispatchLogic dispatchLogic,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        return new FlowHandleContext(
                consensusNow,
                transactionInfo,
                configuration,
                authorizer,
                blockRecordManager,
                feeManager,
                storeFactory,
                syntheticPayer,
                verifier,
                payerkey,
                feeAccumulator,
                exchangeRateManager,
                stack,
                entityIdStore,
                dispatcher,
                recordCache,
                writableStoreFactory,
                serviceApiFactory,
                networkInfo,
                recordBuilder,
                childDispatchFactory,
                childDispatchLogic,
                dispatch,
                dispatchLogic,
                networkUtilizationManager);
    }
}
