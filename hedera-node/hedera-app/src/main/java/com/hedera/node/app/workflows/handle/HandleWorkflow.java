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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CONSENSUS_GAS_EXHAUSTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.spi.HapiUtils.isHollow;
import static com.hedera.node.app.spi.key.KeyUtils.IMMUTABILITY_SENTINEL_KEY;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.NO_DUPLICATE;
import static com.hedera.node.app.state.HederaRecordCache.DuplicateCheckResult.SAME_NODE;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartEvent;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartRound;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransaction;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP2;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logStartUserTransactionPreHandleResultP3;
import static com.hedera.node.app.throttle.ThrottleAccumulator.isGasThrottled;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.NODE_DUE_DILIGENCE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.PRE_HANDLE_FAILURE;
import static com.hedera.node.app.workflows.prehandle.PreHandleResult.Status.SO_FAR_SO_GOOD;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.service.token.records.CryptoUpdateRecordBuilder;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.DefaultKeyVerifier;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.signature.SignatureExpander;
import com.hedera.node.app.signature.SignatureVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.InsufficientNonFeeDebitsException;
import com.hedera.node.app.spi.workflows.InsufficientServiceFeeException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.record.GenesisRecordsConsensusHook;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.ConsensusConfig;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SwirldDualState;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The handle workflow that is responsible for handling the next {@link Round} of transactions.
 */
public class HandleWorkflow {

    private static final Logger logger = LogManager.getLogger(HandleWorkflow.class);

    private final NetworkInfo networkInfo;
    private final PreHandleWorkflow preHandleWorkflow;
    private final TransactionDispatcher dispatcher;
    private final BlockRecordManager blockRecordManager;
    private final SignatureExpander signatureExpander;
    private final SignatureVerifier signatureVerifier;
    private final TransactionChecker checker;
    private final ServiceScopeLookup serviceScopeLookup;
    private final ConfigProvider configProvider;
    private final HederaRecordCache recordCache;
    private final GenesisRecordsConsensusHook genesisRecordsTimeHook;
    private final StakingPeriodTimeHook stakingPeriodTimeHook;
    private final FeeManager feeManager;
    private final ExchangeRateManager exchangeRateManager;
    private final ChildRecordFinalizer childRecordFinalizer;
    private final ParentRecordFinalizer transactionFinalizer;
    private final SystemFileUpdateFacility systemFileUpdateFacility;
    private final DualStateUpdateFacility dualStateUpdateFacility;
    private final SolvencyPreCheck solvencyPreCheck;
    private final Authorizer authorizer;
    private final NetworkUtilizationManager networkUtilizationManager;

    @Inject
    public HandleWorkflow(
            @NonNull final NetworkInfo networkInfo,
            @NonNull final PreHandleWorkflow preHandleWorkflow,
            @NonNull final TransactionDispatcher dispatcher,
            @NonNull final BlockRecordManager blockRecordManager,
            @NonNull final SignatureExpander signatureExpander,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final TransactionChecker checker,
            @NonNull final ServiceScopeLookup serviceScopeLookup,
            @NonNull final ConfigProvider configProvider,
            @NonNull final HederaRecordCache recordCache,
            @NonNull final GenesisRecordsConsensusHook genesisRecordsTimeHook,
            @NonNull final StakingPeriodTimeHook stakingPeriodTimeHook,
            @NonNull final FeeManager feeManager,
            @NonNull final ExchangeRateManager exchangeRateManager,
            @NonNull final ChildRecordFinalizer childRecordFinalizer,
            @NonNull final ParentRecordFinalizer transactionFinalizer,
            @NonNull final SystemFileUpdateFacility systemFileUpdateFacility,
            @NonNull final DualStateUpdateFacility dualStateUpdateFacility,
            @NonNull final SolvencyPreCheck solvencyPreCheck,
            @NonNull final Authorizer authorizer,
            @NonNull final NetworkUtilizationManager networkUtilizationManager) {
        this.networkInfo = requireNonNull(networkInfo, "networkInfo must not be null");
        this.preHandleWorkflow = requireNonNull(preHandleWorkflow, "preHandleWorkflow must not be null");
        this.dispatcher = requireNonNull(dispatcher, "dispatcher must not be null");
        this.blockRecordManager = requireNonNull(blockRecordManager, "recordManager must not be null");
        this.signatureExpander = requireNonNull(signatureExpander, "signatureExpander must not be null");
        this.signatureVerifier = requireNonNull(signatureVerifier, "signatureVerifier must not be null");
        this.checker = requireNonNull(checker, "checker must not be null");
        this.serviceScopeLookup = requireNonNull(serviceScopeLookup, "serviceScopeLookup must not be null");
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.recordCache = requireNonNull(recordCache, "recordCache must not be null");
        this.genesisRecordsTimeHook = requireNonNull(genesisRecordsTimeHook, "genesisRecordsTimeHook must not be null");
        this.stakingPeriodTimeHook = requireNonNull(stakingPeriodTimeHook, "stakingPeriodTimeHook must not be null");
        this.feeManager = requireNonNull(feeManager, "feeManager must not be null");
        this.exchangeRateManager = requireNonNull(exchangeRateManager, "exchangeRateManager must not be null");
        this.childRecordFinalizer = childRecordFinalizer;
        this.transactionFinalizer = requireNonNull(transactionFinalizer, "transactionFinalizer must not be null");
        this.systemFileUpdateFacility =
                requireNonNull(systemFileUpdateFacility, "systemFileUpdateFacility must not be null");
        this.dualStateUpdateFacility =
                requireNonNull(dualStateUpdateFacility, "dualStateUpdateFacility must not be null");
        this.solvencyPreCheck = requireNonNull(solvencyPreCheck, "solvencyPreCheck must not be null");
        this.authorizer = requireNonNull(authorizer, "authorizer must not be null");
        this.networkUtilizationManager =
                requireNonNull(networkUtilizationManager, "networkUtilizationManager must not be null");
    }

    /**
     * Handles the next {@link Round}
     *
     * @param state the writable {@link HederaState} that this round will work on
     * @param round the next {@link Round} that needs to be processed
     */
    public void handleRound(
            @NonNull final HederaState state, @NonNull final SwirldDualState dualState, @NonNull final Round round) {
        // Keep track of whether any user transactions were handled. If so, then we will need to close the round
        // with the block record manager.
        final var userTransactionsHandled = new AtomicBoolean(false);

        // log start of round to transaction state log
        logStartRound(round);

        // handle each event in the round
        for (final ConsensusEvent event : round) {
            final var creator = networkInfo.nodeInfo(event.getCreatorId().id());
            if (creator == null) {
                // We were given an event for a node that *does not exist in the address book*. This will be logged as
                // a warning, as this should never happen, and we will skip the event. The platform should guarantee
                // that we never receive an event that isn't associated with the address book, and every node in the
                // address book must have an account ID, since you cannot delete an account belonging to a node, and
                // you cannot change the address book non-deterministically.
                logger.warn("Received event from node {} which is not in the address book", event.getCreatorId());
                return;
            }

            // log start of event to transaction state log
            logStartEvent(event, creator);

            // handle each transaction of the event
            for (final var it = event.consensusTransactionIterator(); it.hasNext(); ) {
                final var platformTxn = it.next();
                try {
                    // skip system transactions
                    if (!platformTxn.isSystem()) {
                        userTransactionsHandled.set(true);
                        handlePlatformTransaction(state, dualState, event, creator, platformTxn);
                    }
                } catch (final Exception e) {
                    logger.fatal(
                            "A fatal unhandled exception occurred during transaction handling. "
                                    + "While this node may not die right away, it is in a bad way, most likely fatally.",
                            e);
                }
            }
        }

        // Inform the BlockRecordManager that the round is complete, so it can update running-hashes in state
        // that have been being computed in background threads. The running hash has to be included in
        // state, but we want to synchronize with background threads as infrequently as possible. So once per
        // round is the minimum we can do.
        if (userTransactionsHandled.get()) {
            blockRecordManager.endRound(state);
        }
    }

    private void handlePlatformTransaction(
            @NonNull final HederaState state,
            @NonNull final SwirldDualState dualState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
        // Get the consensus timestamp. FUTURE We want this to exactly match the consensus timestamp from the hashgraph,
        // but for compatibility with the current implementation, we adjust it as follows.
        final Instant consensusNow = platformTxn.getConsensusTimestamp().minusNanos(1000 - 3L);

        // handle user transaction
        handleUserTransaction(consensusNow, state, dualState, platformEvent, creator, platformTxn);
    }

    private void handleUserTransaction(
            @NonNull final Instant consensusNow,
            @NonNull final HederaState state,
            @NonNull final SwirldDualState dualState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
        // Setup record builder list
        blockRecordManager.startUserTransaction(consensusNow, state);
        final var recordListBuilder = new RecordListBuilder(consensusNow);
        final var recordBuilder = recordListBuilder.userTransactionRecordBuilder();

        // Setup helpers
        final var configuration = configProvider.getConfiguration();
        final var stack = new SavepointStackImpl(state);
        final var readableStoreFactory = new ReadableStoreFactory(stack);
        final var feeAccumulator = createFeeAccumulator(stack, configuration, recordBuilder);

        final var tokenServiceContext = new TokenContextImpl(configuration, stack, recordListBuilder);
        // It's awful that we have to check this every time a transaction is handled, especially since this mostly
        // applies to non-production cases. Let's find a way to 💥💥 remove this 💥💥
        genesisRecordsTimeHook.process(tokenServiceContext);
        try {
            // If this is the first user transaction after midnight, then handle staking updates prior to handling the
            // transaction itself.
            stakingPeriodTimeHook.process(stack, tokenServiceContext);
        } catch (final Exception e) {
            // If anything goes wrong, we log the error and continue
            logger.error("Failed to process staking period time hook", e);
        }

        // Consensus hooks have now had a chance to publish any records from migrations; therefore we can begin handling
        // the user transaction
        blockRecordManager.advanceConsensusClock(consensusNow, state);

        TransactionBody txBody = null;
        AccountID payer = null;
        Fees fees = null;
        try {
            final var preHandleResult = getCurrentPreHandleResult(readableStoreFactory, creator, platformTxn);

            final var transactionInfo = preHandleResult.txInfo();

            if (transactionInfo == null) {
                // FUTURE: Charge node generic penalty, set values in record builder, and remove log statement
                logger.error("Bad transaction from creator {}", creator);
                return;
            }

            // Get the parsed data
            final var transaction = transactionInfo.transaction();
            txBody = transactionInfo.txBody();
            payer = transactionInfo.payerID();

            final Bytes transactionBytes;
            if (transaction.signedTransactionBytes().length() > 0) {
                transactionBytes = transaction.signedTransactionBytes();
            } else {
                // in this case, recorder hash the transaction itself, not its bodyBytes.
                transactionBytes = Transaction.PROTOBUF.toBytes(transaction);
            }

            // Log start of user transaction to transaction state log
            logStartUserTransaction(platformTxn, txBody, payer);
            logStartUserTransactionPreHandleResultP2(
                    preHandleResult.payer(),
                    preHandleResult.payerKey(),
                    preHandleResult.status(),
                    preHandleResult.responseCode());
            logStartUserTransactionPreHandleResultP3(
                    preHandleResult.txInfo(), preHandleResult.requiredKeys(), preHandleResult.verificationResults());

            // Initialize record builder list
            recordBuilder
                    .transaction(transactionInfo.transaction())
                    .transactionBytes(transactionBytes)
                    .transactionID(transactionInfo.transactionID())
                    .exchangeRate(exchangeRateManager.exchangeRates())
                    .memo(txBody.memo());

            // Set up the verifier
            final var hederaConfig = configuration.getConfigData(HederaConfig.class);
            final var legacyFeeCalcNetworkVpt =
                    transactionInfo.signatureMap().sigPairOrElse(emptyList()).size();
            final var verifier = new DefaultKeyVerifier(
                    legacyFeeCalcNetworkVpt, hederaConfig, preHandleResult.verificationResults());
            final var signatureMapSize = SignatureMap.PROTOBUF.measureRecord(transactionInfo.signatureMap());

            // Setup context
            final var context = new HandleContextImpl(
                    txBody,
                    transactionInfo.functionality(),
                    signatureMapSize,
                    payer,
                    preHandleResult.getPayerKey(),
                    networkInfo,
                    TransactionCategory.USER,
                    recordBuilder,
                    stack,
                    configuration,
                    verifier,
                    recordListBuilder,
                    checker,
                    dispatcher,
                    serviceScopeLookup,
                    blockRecordManager,
                    recordCache,
                    feeManager,
                    exchangeRateManager,
                    consensusNow,
                    authorizer,
                    solvencyPreCheck,
                    childRecordFinalizer);

            // Calculate the fee
            fees = dispatcher.dispatchComputeFees(context);

            // Run all pre-checks
            final var validationResult = validate(
                    consensusNow,
                    verifier,
                    preHandleResult,
                    readableStoreFactory,
                    fees,
                    platformEvent.getCreatorId().id());

            networkUtilizationManager.resetFrom(stack);
            final var hasWaivedFees = authorizer.hasWaivedFees(payer, transactionInfo.functionality(), txBody);

            if (validationResult.status() != SO_FAR_SO_GOOD) {
                final var sigVerificationFailed = validationResult.responseCodeEnum() == INVALID_SIGNATURE;
                if (sigVerificationFailed) {
                    // If the signature status isn't ok, only work done will be fee charging
                    // Note this is how it's implemented in mono (TopLevelTransition.java#L93), in future we may want to
                    // not trackFeePayments() only for INVALID_SIGNATURE but for any preCheckResult.status() !=
                    // SO_FAR_SO_GOOD
                    networkUtilizationManager.trackFeePayments(payer, consensusNow, stack);
                }
                recordBuilder.status(validationResult.responseCodeEnum());
                try {
                    // If the payer is authorized to waive fees, then we don't charge them
                    if (!hasWaivedFees) {
                        if (validationResult.status() == NODE_DUE_DILIGENCE_FAILURE) {
                            feeAccumulator.chargeNetworkFee(creator.accountId(), fees.networkFee());
                        } else if (validationResult.status() == PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE) {
                            // We do not charge partial service fees; if the payer is unwilling or unable to cover
                            // the entire service fee, then we only charge network and node fees (prioritizing
                            // the network fee in case of a very low payer balance)
                            feeAccumulator.chargeFees(payer, creator.accountId(), fees.withoutServiceComponent());
                        } else {
                            feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                        }
                    }
                } catch (final HandleException ex) {
                    final var identifier = validationResult.status == NODE_DUE_DILIGENCE_FAILURE
                            ? "node " + creator.nodeId()
                            : "account " + payer;
                    logger.error(
                            "Unable to charge {} a penalty after {} happened. Cause of the failed charge:",
                            identifier,
                            validationResult.responseCodeEnum,
                            ex);
                }

            } else {
                try {
                    // Any hollow accounts that must sign to have all needed signatures, need to be finalized
                    // as a result of transaction being handled.
                    finalizeHollowAccounts(context, configuration, preHandleResult.getHollowAccounts(), verifier);

                    networkUtilizationManager.trackTxn(transactionInfo, consensusNow, stack);
                    // If the payer is authorized to waive fees, then we don't charge them
                    if (!hasWaivedFees) {
                        // privileged transactions are not charged fees
                        feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                    }

                    if (networkUtilizationManager.wasLastTxnGasThrottled()) {
                        // Don't charge the payer the service fee component, because the user-submitted transaction
                        // was fully valid but network capacity was unavailable to satisfy it
                        fees = fees.withoutServiceComponent();
                        throw new HandleException(CONSENSUS_GAS_EXHAUSTED);
                    }

                    // Dispatch the transaction to the handler
                    dispatcher.dispatchHandle(context);
                    // Possibly charge assessed fees for preceding child transactions
                    if (!recordListBuilder.precedingRecordBuilders().isEmpty()) {
                        // We intentionally charge fees even if the transaction failed (may need to update
                        // mono-service to this behavior?)
                        final var childFees = recordListBuilder.precedingRecordBuilders().stream()
                                .mapToLong(SingleTransactionRecordBuilderImpl::transactionFee)
                                .sum();
                        // If the payer is authorized to waive fees, then we don't charge them
                        if (!hasWaivedFees && !feeAccumulator.chargeNetworkFee(payer, childFees)) {
                            throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
                        }
                    }
                    recordBuilder.status(SUCCESS);

                    // After transaction is successfully handled update the gas throttle by leaking the unused gas
                    if (isGasThrottled(transactionInfo.functionality()) && recordBuilder.hasContractResult()) {
                        final var contractsConfig = configuration.getConfigData(ContractsConfig.class);
                        if (contractsConfig.throttleThrottleByGas()) {
                            final var gasUsed = recordBuilder.getGasUsedForContractTxn();
                            final var gasLimitForContractTx =
                                    getGasLimitForContractTx(txBody, transactionInfo.functionality());
                            final var excessAmount = gasLimitForContractTx - gasUsed;
                            networkUtilizationManager.leakUnusedGasPreviouslyReserved(transactionInfo, excessAmount);
                        }
                    }

                    // Notify responsible facility if system-file was uploaded.
                    // Returns SUCCESS if no system-file was uploaded
                    final var fileUpdateResult = systemFileUpdateFacility.handleTxBody(stack, txBody);
                    recordBuilder.status(fileUpdateResult);

                    // Notify if dual state was updated
                    dualStateUpdateFacility.handleTxBody(stack, dualState, txBody);

                } catch (final HandleException e) {
                    // In case of a ContractCall when it reverts, the gas charged should not be rolled back
                    rollback(e.shouldRollbackStack(), e.getStatus(), stack, recordListBuilder);
                    if (!hasWaivedFees && e.shouldRollbackStack()) {
                        // Only re-charge fees if we rolled back the stack
                        feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                    }
                }
            }
        } catch (final Exception e) {
            logger.error("An unexpected exception was thrown during handle", e);
            // We should always rollback stack including gas charges when there is an unexpected exception
            rollback(true, ResponseCodeEnum.FAIL_INVALID, stack, recordListBuilder);
            if (payer != null && fees != null) {
                try {
                    feeAccumulator.chargeFees(payer, creator.accountId(), fees);
                } catch (final HandleException chargeException) {
                    logger.error(
                            "Unable to charge account {} a penalty after an unexpected exception {}. Cause of the failed charge:",
                            payer,
                            e,
                            chargeException);
                }
            }
        }

        networkUtilizationManager.saveTo(stack);
        transactionFinalizer.finalizeParentRecord(payer, tokenServiceContext);

        // Commit all state changes
        stack.commitFullStack();

        // store all records at once, build() records end of transaction to log
        final var recordListResult = recordListBuilder.build();
        recordCache.add(creator.nodeId(), payer, recordListResult.records());

        blockRecordManager.endUserTransaction(recordListResult.records().stream(), state);
    }

    /**
     * Updates key on the hollow accounts that need to be finalized. This is done by dispatching a preceding
     * synthetic update transaction. The ksy is derived from the signature expansion, by looking up the ECDSA key
     * for the alias.
     *
     * @param context the handle context
     * @param configuration the configuration
     * @param accounts the set of hollow accounts that need to be finalized
     * @param verifier the key verifier
     */
    private void finalizeHollowAccounts(
            @NonNull final HandleContext context,
            @NonNull final Configuration configuration,
            @NonNull final Set<Account> accounts,
            @NonNull final DefaultKeyVerifier verifier) {
        final var consensusConfig = configuration.getConfigData(ConsensusConfig.class);
        final var precedingHollowAccountRecords = accounts.size();
        final var maxRecords = consensusConfig.handleMaxPrecedingRecords();
        // If the hollow accounts that need to be finalized is greater than the max preceding
        // records allowed throw an exception
        if (precedingHollowAccountRecords >= maxRecords) {
            throw new HandleException(MAX_CHILD_RECORDS_EXCEEDED);
        } else {
            for (final var hollowAccount : accounts) {
                // get the verified key for this hollow account
                final var verification = Objects.requireNonNull(
                        verifier.verificationFor(hollowAccount.alias()),
                        "Required hollow account verified signature did not exist");
                if (verification.key() != null) {
                    if (!IMMUTABILITY_SENTINEL_KEY.equals(hollowAccount.keyOrThrow())) {
                        logger.error("Hollow account {} has a key other than the sentinel key", hollowAccount);
                        return;
                    }
                    // dispatch synthetic update transaction for updating key on this hollow account
                    final var syntheticUpdateTxn = TransactionBody.newBuilder()
                            .cryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
                                    .accountIDToUpdate(hollowAccount.accountId())
                                    .key(verification.key())
                                    .build())
                            .build();
                    // Note the null key verification callback below; we bypass signature
                    // verifications when doing hollow account finalization
                    final var recordBuilder = context.dispatchPrecedingTransaction(
                            syntheticUpdateTxn, CryptoUpdateRecordBuilder.class, null, context.payer());
                    // For some reason update accountId is set only for the hollow account finalizations and not
                    // for top level crypto update transactions. So we set it here.
                    recordBuilder.accountID(hollowAccount.accountId());
                }
            }
        }
    }

    @NonNull
    private FeeAccumulator createFeeAccumulator(
            @NonNull final SavepointStackImpl stack,
            @NonNull final Configuration configuration,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        final var serviceApiFactory = new ServiceApiFactory(stack, configuration);
        final var tokenApi = serviceApiFactory.getApi(TokenServiceApi.class);
        return new FeeAccumulatorImpl(tokenApi, recordBuilder);
    }

    private static long getGasLimitForContractTx(final TransactionBody txnBody, final HederaFunctionality function) {
        return switch (function) {
            case CONTRACT_CREATE -> txnBody.contractCreateInstance().gas();
            case CONTRACT_CALL -> txnBody.contractCall().gas();
            case ETHEREUM_TRANSACTION -> EthTxData.populateEthTxData(
                            txnBody.ethereumTransaction().ethereumData().toByteArray())
                    .gasLimit();
            default -> 0L;
        };
    }

    private ValidationResult validate(
            @NonNull final Instant consensusNow,
            @NonNull final KeyVerifier verifier,
            @NonNull final PreHandleResult preHandleResult,
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final Fees fees,
            final long nodeID) {

        final var txInfo = preHandleResult.txInfo();
        final var payerID = txInfo.payerID();
        final var functionality = txInfo.functionality();
        final var txBody = txInfo.txBody();
        boolean isPayerHollow;

        // Check if pre-handle was successful
        if (preHandleResult.status() != SO_FAR_SO_GOOD) {
            return new ValidationResult(preHandleResult.status(), preHandleResult.responseCode());
        }

        // Check for duplicate transactions. It is perfectly normal for there to be duplicates -- it is valid for
        // a user to intentionally submit duplicates to multiple nodes as a hedge against dishonest nodes, or for
        // other reasons. If we find a duplicate, we *will not* execute the transaction, we will simply charge
        // the payer (whether the payer from the transaction or the node in the event of a due diligence failure)
        // and create an appropriate record to save in state and send to the record stream.
        final var duplicateCheckResult = recordCache.hasDuplicate(txBody.transactionID(), nodeID);
        if (duplicateCheckResult != NO_DUPLICATE) {
            return new ValidationResult(
                    duplicateCheckResult == SAME_NODE ? NODE_DUE_DILIGENCE_FAILURE : PRE_HANDLE_FAILURE,
                    DUPLICATE_TRANSACTION);
        }

        // Check the status and solvency of the payer

        try {
            final var payer = solvencyPreCheck.getPayerAccount(storeFactory, payerID);
            solvencyPreCheck.checkSolvency(txInfo, payer, fees, false);
            isPayerHollow = isHollow(payer);
        } catch (final InsufficientServiceFeeException e) {
            return new ValidationResult(PAYER_UNWILLING_OR_UNABLE_TO_PAY_SERVICE_FEE, e.responseCode());
        } catch (final InsufficientNonFeeDebitsException e) {
            return new ValidationResult(PRE_HANDLE_FAILURE, e.responseCode());
        } catch (final PreCheckException e) {
            // Includes InsufficientNetworkFeeException
            return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, e.responseCode());
        }

        // Check the time box of the transaction
        try {
            checker.checkTimeBox(txBody, consensusNow);
        } catch (final PreCheckException e) {
            return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, e.responseCode());
        }

        // Check if the payer has the required permissions
        if (!authorizer.isAuthorized(payerID, functionality)) {
            if (functionality == HederaFunctionality.SYSTEM_DELETE) {
                return new ValidationResult(PRE_HANDLE_FAILURE, ResponseCodeEnum.NOT_SUPPORTED);
            }
            return new ValidationResult(PRE_HANDLE_FAILURE, ResponseCodeEnum.UNAUTHORIZED);
        }

        // Check if the transaction is privileged and if the payer has the required privileges
        final var privileges = authorizer.hasPrivilegedAuthorization(payerID, functionality, txBody);
        if (privileges == SystemPrivilege.UNAUTHORIZED) {
            return new ValidationResult(PRE_HANDLE_FAILURE, ResponseCodeEnum.AUTHORIZATION_FAILED);
        }
        if (privileges == SystemPrivilege.IMPERMISSIBLE) {
            return new ValidationResult(PRE_HANDLE_FAILURE, ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE);
        }

        // Check all signature verifications. This will also wait, if validation is still ongoing.
        // If the payer is hollow the key will be null, so we skip the payer signature verification.
        if (!isPayerHollow) {
            final var payerKeyVerification = verifier.verificationFor(preHandleResult.getPayerKey());
            if (payerKeyVerification.failed()) {
                return new ValidationResult(NODE_DUE_DILIGENCE_FAILURE, INVALID_PAYER_SIGNATURE);
            }
        }

        // verify all the keys
        for (final var key : preHandleResult.getRequiredKeys()) {
            final var verification = verifier.verificationFor(key);
            if (verification.failed()) {
                return new ValidationResult(PRE_HANDLE_FAILURE, INVALID_SIGNATURE);
            }
        }
        // If there are any hollow accounts whose signatures need to be verified, verify them
        for (final var hollowAccount : preHandleResult.getHollowAccounts()) {
            final var verification = verifier.verificationFor(hollowAccount.alias());
            if (verification.failed()) {
                return new ValidationResult(PRE_HANDLE_FAILURE, INVALID_SIGNATURE);
            }
        }

        return new ValidationResult(SO_FAR_SO_GOOD, OK);
    }

    private record ValidationResult(
            @NonNull PreHandleResult.Status status, @NonNull ResponseCodeEnum responseCodeEnum) {}

    /**
     * Rolls back the stack and sets the status of the transaction in case of a failure.
     * @param rollbackStack whether to rollback the stack. Will be false when the failure is due to a
     *                      {@link HandleException} that is due to a contract call revert.
     * @param status the status to set
     * @param stack the save point stack to rollback
     * @param recordListBuilder the record list builder to revert
     */
    private void rollback(
            final boolean rollbackStack,
            @NonNull final ResponseCodeEnum status,
            @NonNull final SavepointStackImpl stack,
            @NonNull final RecordListBuilder recordListBuilder) {
        if (rollbackStack) {
            stack.rollbackFullStack();
        }
        final var userTransactionRecordBuilder = recordListBuilder.userTransactionRecordBuilder();
        userTransactionRecordBuilder.status(status);
        recordListBuilder.revertChildrenOf(userTransactionRecordBuilder);
    }

    /*
     * This method gets all the verification data for the current transaction. If pre-handle was previously ran
     * successfully, we only add the missing keys. If it did not run or an error occurred, we run it again.
     * If there is a due diligence error, this method will return a CryptoTransfer to charge the node along with
     * its verification data.
     */
    @NonNull
    private PreHandleResult getCurrentPreHandleResult(
            @NonNull final ReadableStoreFactory storeFactory,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
        final var metadata = platformTxn.getMetadata();
        final PreHandleResult originalPreHandleResult;
        if (metadata instanceof PreHandleResult result) {
            originalPreHandleResult = result;
        } else {
            originalPreHandleResult = null;
        }
        // We do not know how long transactions are kept in memory. Clearing metadata to avoid keeping it for too long.
        platformTxn.setMetadata(null);

        return preHandleWorkflow.preHandleTransaction(
                creator.accountId(),
                storeFactory,
                storeFactory.getStore(ReadableAccountStore.class),
                platformTxn,
                originalPreHandleResult);
    }

    /**
     * Checks if any of the keys changed from previous result to current result.
     * Only if keys changed we need to re-expand and re-verify the signatures.
     *
     * @param previousResult previous pre-handle result
     * @param context current context
     * @return true if any of the keys changed
     */
    private boolean haveKeyChanges(final PreHandleResult previousResult, final PreHandleContextImpl context) {
        final var currentRequiredNonPayerKeys = context.requiredNonPayerKeys();
        final var currentOptionalNonPayerKeys = context.optionalNonPayerKeys();
        final var currentPayerKey = context.payerKey();

        // keys from previous pre-handle result
        final var previousResultRequiredKeys = previousResult.getRequiredKeys();
        final var previousResultOptionalKeys = previousResult.getOptionalKeys();
        final var previousResultPayerKey = previousResult.getPayerKey();

        for (final var key : currentRequiredNonPayerKeys) {
            if (!previousResultRequiredKeys.contains(key)) {
                return true;
            }
        }
        for (final var key : currentOptionalNonPayerKeys) {
            if (!previousResultOptionalKeys.contains(key)) {
                return true;
            }
        }
        return !previousResultPayerKey.equals(currentPayerKey);
    }
}
