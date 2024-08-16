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

package com.hedera.node.app.workflows.handle.dispatch;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_UPDATE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.util.HapiUtils.functionOf;
import static com.hedera.node.app.workflows.handle.throttle.DispatchUsageManager.CONTRACT_OPERATIONS;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.fees.ResourcePriceCalculatorImpl;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.ids.EntityNumGeneratorImpl;
import com.hedera.node.app.ids.WritableEntityIdStore;
import com.hedera.node.app.service.token.api.FeeStreamBuilder;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.AppKeyVerifier;
import com.hedera.node.app.signature.DelegateKeyVerifier;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.store.ServiceApiFactory;
import com.hedera.node.app.store.StoreFactoryImpl;
import com.hedera.node.app.store.WritableStoreFactory;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.DispatchHandleContext;
import com.hedera.node.app.workflows.handle.DispatchProcessor;
import com.hedera.node.app.workflows.handle.RecordDispatch;
import com.hedera.node.app.workflows.handle.record.RecordStreamBuilder;
import com.hedera.node.app.workflows.handle.record.TokenContextImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A factory for constructing child dispatches.This also gets the pre-handle result for the child transaction,
 * and signature verifications for the child transaction.
 */
@Singleton
public class ChildDispatchFactory {
    public static final NoOpKeyVerifier NO_OP_KEY_VERIFIER = new NoOpKeyVerifier();

    private final TransactionDispatcher dispatcher;
    private final Authorizer authorizer;
    private final NetworkInfo networkInfo;
    private final FeeManager feeManager;
    private final DispatchProcessor dispatchProcessor;
    private final ServiceScopeLookup serviceScopeLookup;
    private final StoreMetricsService storeMetricsService;
    private final ExchangeRateManager exchangeRateManager;

    @Inject
    public ChildDispatchFactory(
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final FeeManager feeManager,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final ExchangeRateManager exchangeRateManager) {
        this.dispatcher = requireNonNull(dispatcher);
        this.authorizer = requireNonNull(authorizer);
        this.networkInfo = requireNonNull(networkInfo);
        this.feeManager = requireNonNull(feeManager);
        this.dispatchProcessor = requireNonNull(dispatchProcessor);
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup);
        this.storeMetricsService = requireNonNull(storeMetricsService);
        this.exchangeRateManager = requireNonNull(exchangeRateManager);
    }

    /**
     * Creates a child dispatch. This method computes the transaction info and initializes record builder for the child
     * transaction. This method also computes a pre-handle result for the child transaction.
     *
     * @param txBody the transaction body
     * @param callback the key verifier for child dispatch
     * @param syntheticPayerId the synthetic payer id
     * @param category the transaction category
     * @param customizer the externalized record customizer
     * @param reversingBehavior the reversing behavior
     * @param config the configuration
     * @param stack the savepoint stack
     * @param readableStoreFactory the readable store factory
     * @param creatorInfo the node info of the creator
     * @param platformState the platform state
     * @param topLevelFunction the top level functionality
     * @param consensusNow the consensus time
     * @param blockRecordInfo the block record info
     * @return the child dispatch
     * @throws HandleException if the child stack base builder cannot be created
     */
    public Dispatch createChildDispatch(
            @NonNull final TransactionBody txBody,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final RecordStreamBuilder.ReversingBehavior reversingBehavior,
            @NonNull final Configuration config,
            @NonNull final SavepointStackImpl stack,
            @NonNull final ReadableStoreFactory readableStoreFactory,
            @NonNull final NodeInfo creatorInfo,
            @NonNull final PlatformState platformState,
            @NonNull final HederaFunctionality topLevelFunction,
            @NonNull final ThrottleAdviser throttleAdviser,
            @NonNull final Instant consensusNow,
            @NonNull final BlockRecordInfo blockRecordInfo) {
        final var preHandleResult = preHandleChild(txBody, syntheticPayerId, config, readableStoreFactory);
        final var childVerifier = getKeyVerifier(callback);
        final var childTxnInfo = getTxnInfoFrom(txBody);
        final var streamMode = config.getConfigData(BlockStreamConfig.class).streamMode();
        final var childStack =
                SavepointStackImpl.newChildStack(stack, reversingBehavior, category, customizer, streamMode);
        final var streamBuilder = initializedForChild(childStack.getBaseBuilder(StreamBuilder.class), childTxnInfo);
        return newChildDispatch(
                streamBuilder,
                childTxnInfo,
                syntheticPayerId,
                category,
                childStack,
                preHandleResult,
                childVerifier,
                consensusNow,
                creatorInfo,
                config,
                platformState,
                topLevelFunction,
                throttleAdviser,
                authorizer,
                networkInfo,
                feeManager,
                dispatchProcessor,
                blockRecordInfo,
                serviceScopeLookup,
                storeMetricsService,
                exchangeRateManager,
                dispatcher);
    }

    private RecordDispatch newChildDispatch(
            // @ChildDispatchScope
            @NonNull final StreamBuilder builder,
            @NonNull final TransactionInfo txnInfo,
            @NonNull final AccountID payerId,
            @NonNull final HandleContext.TransactionCategory category,
            @NonNull final SavepointStackImpl childStack,
            @NonNull final PreHandleResult preHandleResult,
            @NonNull final AppKeyVerifier keyVerifier,
            @NonNull final Instant consensusNow,
            // @UserTxnScope
            @NonNull final NodeInfo creatorInfo,
            @NonNull final Configuration config,
            @NonNull final PlatformState platformState,
            @NonNull final HederaFunctionality topLevelFunction,
            @NonNull final ThrottleAdviser throttleAdviser,
            // @Singleton
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkInfo networkInfo,
            @NonNull final FeeManager feeManager,
            @NonNull final DispatchProcessor dispatchProcessor,
            @NonNull final BlockRecordInfo blockRecordInfo,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final StoreMetricsService storeMetricsService,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final TransactionDispatcher dispatcher) {
        final var readableStoreFactory = new ReadableStoreFactory(childStack);
        final var writableStoreFactory = new WritableStoreFactory(
                childStack, serviceScopeLookup.getServiceName(txnInfo.txBody()), config, storeMetricsService);
        final var serviceApiFactory = new ServiceApiFactory(childStack, config, storeMetricsService);
        final var priceCalculator =
                new ResourcePriceCalculatorImpl(consensusNow, txnInfo, feeManager, readableStoreFactory);
        final var storeFactory = new StoreFactoryImpl(readableStoreFactory, writableStoreFactory, serviceApiFactory);
        final var entityNumGenerator = new EntityNumGeneratorImpl(
                new WritableStoreFactory(childStack, EntityIdService.NAME, config, storeMetricsService)
                        .getStore(WritableEntityIdStore.class));
        final var feeAccumulator =
                new FeeAccumulator(serviceApiFactory.getApi(TokenServiceApi.class), (FeeStreamBuilder) builder);
        final var dispatchHandleContext = new DispatchHandleContext(
                consensusNow,
                creatorInfo,
                txnInfo,
                config,
                authorizer,
                blockRecordInfo,
                priceCalculator,
                feeManager,
                storeFactory,
                payerId,
                keyVerifier,
                platformState,
                topLevelFunction,
                Key.DEFAULT,
                exchangeRateManager,
                childStack,
                entityNumGenerator,
                dispatcher,
                networkInfo,
                this,
                dispatchProcessor,
                throttleAdviser,
                feeAccumulator);
        final var childFees =
                computeChildFees(payerId, dispatchHandleContext, category, dispatcher, topLevelFunction, txnInfo);
        final var childFeeAccumulator =
                new FeeAccumulator(serviceApiFactory.getApi(TokenServiceApi.class), (RecordStreamBuilder) builder);
        final var childTokenContext = new TokenContextImpl(config, storeMetricsService, childStack, consensusNow);
        return new RecordDispatch(
                builder,
                config,
                childFees,
                txnInfo,
                payerId,
                readableStoreFactory,
                childFeeAccumulator,
                keyVerifier,
                creatorInfo,
                consensusNow,
                preHandleResult.getRequiredKeys(),
                preHandleResult.getHollowAccounts(),
                dispatchHandleContext,
                childStack,
                category,
                childTokenContext,
                platformState,
                preHandleResult);
    }

    private static Fees computeChildFees(
            @NonNull final AccountID payerId,
            @NonNull final FeeContext feeContext,
            @NonNull final HandleContext.TransactionCategory childCategory,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final HederaFunctionality topLevelFunction,
            @NonNull final TransactionInfo childTxnInfo) {
        return switch (childCategory) {
            case SCHEDULED -> dispatcher.dispatchComputeFees(feeContext).onlyServiceComponent();
            case PRECEDING -> {
                if (CONTRACT_OPERATIONS.contains(topLevelFunction) || childTxnInfo.functionality() == CRYPTO_UPDATE) {
                    yield Fees.FREE;
                } else {
                    yield feeContext.dispatchComputeFees(childTxnInfo.txBody(), payerId);
                }
            }
            case CHILD -> Fees.FREE;
            case USER -> throw new IllegalStateException("Should not dispatch child with user transaction category");
        };
    }

    /**
     * Dispatches the pre-handle checks for the child transaction. This runs pureChecks and then dispatches pre-handle
     * for child transaction.
     *
     * @param txBody the transaction body
     * @param syntheticPayerId the synthetic payer id
     * @param config the configuration
     * @param readableStoreFactory the readable store factory
     * @return the pre-handle result
     */
    private PreHandleResult preHandleChild(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final Configuration config,
            @NonNull final ReadableStoreFactory readableStoreFactory) {
        try {
            dispatcher.dispatchPureChecks(txBody);
            final var preHandleContext =
                    new PreHandleContextImpl(readableStoreFactory, txBody, syntheticPayerId, config, dispatcher);
            dispatcher.dispatchPreHandle(preHandleContext);
            return new PreHandleResult(
                    null,
                    null,
                    SO_FAR_SO_GOOD,
                    OK,
                    null,
                    preHandleContext.requiredNonPayerKeys(),
                    null,
                    preHandleContext.requiredHollowAccounts(),
                    null,
                    null,
                    0);
        } catch (final PreCheckException e) {
            return new PreHandleResult(
                    null,
                    null,
                    PRE_HANDLE_FAILURE,
                    e.responseCode(),
                    null,
                    Collections.emptySet(),
                    null,
                    Collections.emptySet(),
                    null,
                    null,
                    0);
        }
    }

    /**
     * A {@link AppKeyVerifier} that always returns {@link SignatureVerificationImpl} with a
     * passed verification.
     */
    public static class NoOpKeyVerifier implements AppKeyVerifier {
        private static final SignatureVerification PASSED_VERIFICATION =
                new SignatureVerificationImpl(Key.DEFAULT, Bytes.EMPTY, true);

        @NonNull
        @Override
        public SignatureVerification verificationFor(@NonNull final Key key) {
            return PASSED_VERIFICATION;
        }

        @NonNull
        @Override
        public SignatureVerification verificationFor(
                @NonNull final Key key, @NonNull final VerificationAssistant callback) {
            return PASSED_VERIFICATION;
        }

        @NonNull
        @Override
        public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
            return PASSED_VERIFICATION;
        }

        @Override
        public int numSignaturesVerified() {
            return 0;
        }
    }

    /**
     * Returns a {@link AppKeyVerifier} based on the callback. If the callback is null, then it returns a
     * {@link NoOpKeyVerifier}. Otherwise, it returns a {@link DelegateKeyVerifier} with the callback.
     * The callback is null if the signature verification is not required. This is the case for hollow account
     * completion and auto account creation.
     *
     * @param callback the callback
     * @return the key verifier
     */
    public static AppKeyVerifier getKeyVerifier(@Nullable Predicate<Key> callback) {
        return callback == null
                ? NO_OP_KEY_VERIFIER
                : new AppKeyVerifier() {
                    private final AppKeyVerifier verifier = new DelegateKeyVerifier(callback);

                    @NonNull
                    @Override
                    public SignatureVerification verificationFor(@NonNull final Key key) {
                        return callback.test(key) ? NoOpKeyVerifier.PASSED_VERIFICATION : verifier.verificationFor(key);
                    }

                    @NonNull
                    @Override
                    public SignatureVerification verificationFor(
                            @NonNull final Key key, @NonNull final VerificationAssistant callback) {
                        throw new UnsupportedOperationException("Should never be called!");
                    }

                    @NonNull
                    @Override
                    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
                        throw new UnsupportedOperationException("Should never be called!");
                    }

                    @Override
                    public int numSignaturesVerified() {
                        return 0;
                    }
                };
    }

    /**
     * Provides the transaction information for the given dispatched transaction body.
     *
     * @param txBody the transaction body
     * @return the transaction information
     */
    private TransactionInfo getTxnInfoFrom(TransactionBody txBody) {
        final var bodyBytes = TransactionBody.PROTOBUF.toBytes(txBody);
        final var signedTransaction =
                SignedTransaction.newBuilder().bodyBytes(bodyBytes).build();
        final var signedTransactionBytes = SignedTransaction.PROTOBUF.toBytes(signedTransaction);
        final var transaction = Transaction.newBuilder()
                .signedTransactionBytes(signedTransactionBytes)
                .build();
        // Since in the current systems the synthetic transactions need not have a transaction ID
        // Payer will be injected as synthetic payer in dagger subcomponent, since the payer could be different
        // for schedule dispatches. Also, there will not be signature verifications for synthetic transactions.
        // So these fields are set to default values and will not be used.
        return new TransactionInfo(
                transaction,
                txBody,
                TransactionID.DEFAULT,
                AccountID.DEFAULT,
                SignatureMap.DEFAULT,
                signedTransactionBytes,
                functionOfTxn(txBody));
    }

    /**
     * Provides the functionality of the transaction body.
     *
     * @param txBody the transaction body
     * @return the functionality
     */
    private static HederaFunctionality functionOfTxn(final TransactionBody txBody) {
        try {
            return functionOf(txBody);
        } catch (final UnknownHederaFunctionality e) {
            throw new IllegalArgumentException("Unknown Hedera Functionality", e);
        }
    }

    /**
     * Initializes the user stream item builder with the transaction information.
     * @param builder the stream item builder
     * @param txnInfo the transaction info
     */
    private StreamBuilder initializedForChild(
            @NonNull final StreamBuilder builder, @NonNull final TransactionInfo txnInfo) {
        builder.transaction(txnInfo.transaction())
                .transactionBytes(txnInfo.signedBytes())
                .memo(txnInfo.txBody().memo());
        final var transactionID = txnInfo.txBody().transactionID();
        if (transactionID != null) {
            builder.transactionID(transactionID);
        }
        return builder;
    }
}
