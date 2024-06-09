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

package com.hedera.node.app.workflows.handle.flow.dispatch;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.workflows.handle.flow.util.FlowUtils.ALERT_MESSAGE;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.workflows.handle.PlatformStateUpdateFacility;
import com.hedera.node.app.workflows.handle.SystemFileUpdateFacility;
import com.hedera.node.app.workflows.handle.flow.process.WorkDone;
import com.hedera.node.app.workflows.handle.flow.records.RecordFinalizerlogic;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class has the common logic that is executed for a user dispatch and a child dispatch transactions.
 * This will charge the fees to the creator if there is a node due diligence failure. Otherwise, charges the fees to the
 * payer and executes the business logic for the given dispatch, guaranteeing that the changes committed to its stack
 * are exactly reflected in its recordBuilder.
 */
@Singleton
public class DispatchLogic {
    private static final Logger logger = LogManager.getLogger(DispatchLogic.class);
    private final Authorizer authorizer;
    private final ErrorReportingLogic errorReportingLogic;
    private final HandleLogic handleLogic;
    private final RecordFinalizerlogic recordFinalizerlogic;
    private final SystemFileUpdateFacility systemFileUpdateFacility;
    private final PlatformStateUpdateFacility platformStateUpdateFacility;
    private final ExchangeRateManager exchangeRateManager;

    @Inject
    public DispatchLogic(
            final Authorizer authorizer,
            final ErrorReportingLogic errorReportingLogic,
            final HandleLogic handleLogic,
            final RecordFinalizerlogic recordFinalizerlogic,
            final SystemFileUpdateFacility systemFileUpdateFacility,
            final PlatformStateUpdateFacility platformStateUpdateFacility,
            final ExchangeRateManager exchangeRateManager) {
        this.authorizer = authorizer;
        this.errorReportingLogic = errorReportingLogic;
        this.handleLogic = handleLogic;
        this.recordFinalizerlogic = recordFinalizerlogic;
        this.systemFileUpdateFacility = systemFileUpdateFacility;
        this.platformStateUpdateFacility = platformStateUpdateFacility;
        this.exchangeRateManager = exchangeRateManager;
    }

    /**
     * This method is responsible for charging the fees and executing the business logic for the given dispatch,
     * guaranteeing that the changes committed to its stack are exactly reflected in its recordBuilder.
     * @param dispatch the dispatch to be processed
     */
    public WorkDone dispatch(@NonNull Dispatch dispatch, @NonNull final RecordListBuilder recordListBuilder) {
        final var errorReport = errorReportingLogic.errorReportFor(dispatch);
        final WorkDone workDone;
        if (errorReport.isCreatorError()) {
            chargeCreator(dispatch, errorReport);
            workDone = WorkDone.FEES_ONLY;
        } else {
            chargePayer(dispatch, errorReport);
            if (errorReport.isPayerSolvencyError()) {
                dispatch.recordBuilder().status(errorReport.payerSolvencyErrorOrThrow());
                workDone = WorkDone.FEES_ONLY;
            } else {
                workDone = handleTransaction(dispatch, errorReport, recordListBuilder);
            }
        }

        recordFinalizerlogic.finalizeRecord(dispatch);
        dispatch.stack().commitFullStack();
        return workDone;
    }

    /**
     * Handles the transaction logic for the given dispatch. If the logic fails, it will rollback the stack and charge
     * the payer for the fees.
     * @param dispatch the dispatch to be processed
     * @param errorReport the due diligence report for the dispatch
     * @param recordListBuilder the record list builder for the dispatch
     */
    private WorkDone handleTransaction(
            @NonNull final Dispatch dispatch,
            @NonNull final ErrorReport errorReport,
            @NonNull final RecordListBuilder recordListBuilder) {
        try {
            final var workDone = handleLogic.handle(dispatch);
            dispatch.recordBuilder().status(SUCCESS);
            handleSystemUpdates(dispatch);
            return workDone;
        } catch (HandleException e) {
            // In case of a ContractCall when it reverts, the gas charged should not be rolled back
            rollback(
                    e.shouldRollbackStack(),
                    e.getStatus(),
                    dispatch.stack(),
                    recordListBuilder,
                    dispatch.recordBuilder());
            if (e.shouldRollbackStack()) {
                chargePayer(dispatch, errorReport);
            }
            return WorkDone.USER_TRANSACTION;
        } catch (HandleLogic.ThrottleException e) {
            return handleException(dispatch, errorReport, recordListBuilder, e.getStatus());
        } catch (Exception e) {
            logger.error("{} - exception thrown while handling dispatch", ALERT_MESSAGE, e);
            return handleException(dispatch, errorReport, recordListBuilder, ResponseCodeEnum.FAIL_INVALID);
        }
    }

    private void handleSystemUpdates(final Dispatch dispatch) {
        // Notify responsible facility if system-file was uploaded.
        // Returns SUCCESS if no system-file was uploaded
        final var fileUpdateResult = systemFileUpdateFacility.handleTxBody(
                dispatch.stack(), dispatch.txnInfo().txBody());

        dispatch.recordBuilder()
                .exchangeRate(exchangeRateManager.exchangeRates())
                .status(fileUpdateResult);

        // Notify if platform state was updated
        platformStateUpdateFacility.handleTxBody(
                dispatch.stack(), dispatch.platformState(), dispatch.txnInfo().txBody());
    }

    @NonNull
    private WorkDone handleException(
            final @NonNull Dispatch dispatch,
            final @NonNull ErrorReport errorReport,
            final @NonNull RecordListBuilder recordListBuilder,
            final ResponseCodeEnum status) {
        rollback(true, status, dispatch.stack(), recordListBuilder, dispatch.recordBuilder());
        chargePayer(dispatch, errorReport.withoutServiceFee());
        return WorkDone.FEES_ONLY;
    }

    /**
     * Charges the creator for the network fee. This will be called when there is a due diligence failure.
     * @param dispatch the dispatch to be processed
     * @param report the due diligence report for the dispatch
     */
    private void chargeCreator(final Dispatch dispatch, ErrorReport report) {
        dispatch.recordBuilder().status(report.creatorErrorOrThrow());
        dispatch.feeAccumulator()
                .chargeNetworkFee(report.creatorId(), dispatch.fees().networkFee());
    }

    /**
     * Charges the payer for the fees. If the payer is unable to pay the service fee, the service fee will be charged to
     * the creator. If the transaction is a duplicate, the service fee will be waived.
     * @param dispatch the dispatch to be processed
     * @param report the due diligence report for the dispatch
     */
    private void chargePayer(final Dispatch dispatch, ErrorReport report) {
        final var hasWaivedFees = authorizer.hasWaivedFees(
                dispatch.syntheticPayer(),
                dispatch.txnInfo().functionality(),
                dispatch.txnInfo().txBody());
        if (hasWaivedFees) {
            return;
        }
        if (report.unableToPayServiceFee() || report.isDuplicate()) {
            dispatch.feeAccumulator()
                    .chargeFees(
                            report.payerOrThrow().accountIdOrThrow(),
                            report.creatorId(),
                            dispatch.fees().withoutServiceComponent());
        } else {
            dispatch.feeAccumulator()
                    .chargeFees(report.payerOrThrow().accountIdOrThrow(), report.creatorId(), dispatch.fees());
        }
    }

    /**
     * Rolls back the stack and sets the status of the transaction in case of a failure.
     *
     * @param rollbackStack whether to rollback the stack. Will be false when the failure is due to a
     * {@link HandleException} that is due to a contract call revert.
     * @param status the status to set
     * @param stack the save point stack to rollback
     * @param recordListBuilder the record list builder to revert
     */
    private void rollback(
            final boolean rollbackStack,
            @NonNull final ResponseCodeEnum status,
            @NonNull final SavepointStackImpl stack,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final SingleTransactionRecordBuilderImpl recordBuilder) {
        if (rollbackStack) {
            stack.rollbackFullStack();
        }
        recordBuilder.status(status);
        recordListBuilder.revertChildrenOf(recordBuilder);
    }
}
