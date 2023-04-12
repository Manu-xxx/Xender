/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.ingest;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
import static com.hedera.node.app.service.mono.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.annotations.NodeSelfId;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.txns.submission.SolvencyPrecheck;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.accessors.SignedTxnAccessor;
import com.hedera.node.app.signature.SignaturePreparer;
import com.hedera.node.app.spi.info.CurrentPlatformStatus;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleAccumulator;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;

/**
 * The {@code IngestChecker} contains checks that are specific to the ingest workflow
 */
public class IngestChecker {

    private final AccountID nodeAccountID;
    private final NodeInfo nodeInfo;
    private final CurrentPlatformStatus currentPlatformStatus;
    private final TransactionChecker transactionChecker;
    private final ThrottleAccumulator throttleAccumulator;
    private final SolvencyPrecheck solvencyPrecheck;
    private final SignaturePreparer signaturePreparer;

    /**
     * Constructor of the {@code IngestChecker}
     *
     * @param nodeAccountID the {@link AccountID} of the <em>node</em>
     * @param nodeInfo the {@link NodeInfo} that contains information about the node
     * @param currentPlatformStatus the {@link CurrentPlatformStatus} that contains the current status of the platform
     * @param transactionChecker the {@link TransactionChecker} that pre-processes the bytes of a transaction
     * @param throttleAccumulator the {@link ThrottleAccumulator} for throttling
     * @param solvencyPrecheck the {@link SolvencyPrecheck} that checks payer balance
     * @param signaturePreparer the {@link SignaturePreparer} that prepares signature data
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @Inject
    public IngestChecker(
            @NonNull @NodeSelfId final AccountID nodeAccountID,
            @NonNull final NodeInfo nodeInfo,
            @NonNull final CurrentPlatformStatus currentPlatformStatus,
            @NonNull final TransactionChecker transactionChecker,
            @NonNull final ThrottleAccumulator throttleAccumulator,
            @NonNull final SolvencyPrecheck solvencyPrecheck,
            @NonNull final SignaturePreparer signaturePreparer) {
        this.nodeAccountID = requireNonNull(nodeAccountID);
        this.nodeInfo = requireNonNull(nodeInfo);
        this.currentPlatformStatus = requireNonNull(currentPlatformStatus);
        this.transactionChecker = requireNonNull(transactionChecker);
        this.throttleAccumulator = requireNonNull(throttleAccumulator);
        this.solvencyPrecheck = solvencyPrecheck;
        this.signaturePreparer = requireNonNull(signaturePreparer);
    }

    /**
     * Checks the general state of the node
     *
     * @throws PreCheckException if the node is unable to process queries
     */
    public void checkNodeState() throws PreCheckException {
        if (nodeInfo.isSelfZeroStake()) {
            // Zero stake nodes are currently not supported
            throw new PreCheckException(INVALID_NODE_ACCOUNT);
        }
        if (currentPlatformStatus.get() != ACTIVE) {
            throw new PreCheckException(PLATFORM_NOT_ACTIVE);
        }
    }

    public TransactionInfo runAllChecks(
            @NonNull final HederaState state,
            @NonNull final Transaction tx)
            throws PreCheckException {
        // 1. Check the syntax
        final var transactionInfo = transactionChecker.check(tx);
        final var txBody = transactionInfo.txBody();
        final var signatureMap = transactionInfo.signatureMap();
        final var functionality = transactionInfo.functionality();

        // This should never happen, because HapiUtils#checkFunctionality() will throw
        // UnknownHederaFunctionality if it cannot map to a proper value, and WorkflowOnset
        // will convert that to INVALID_TRANSACTION_BODY.
        assert functionality != HederaFunctionality.NONE;

        // 2. Check throttles
        if (throttleAccumulator.shouldThrottle(transactionInfo.txBody())) {
            throw new PreCheckException(ResponseCodeEnum.BUSY);
        }

        // 3. Check semantics
        checkTransactionSemantics(txBody, functionality);

        // 4. Get payer account
        final AccountID payerID =
                txBody.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT);

        // 5. Check payer's signature
        checkPayerSignature(state, transactionInfo.transaction(), signatureMap, payerID);

        // 6. Check account balance
        checkSolvency(transactionInfo.transaction());

        return transactionInfo;
    }

    /**
     * Checks a transaction for semantic errors
     *
     * @param txBody        the {@link TransactionBody}
     * @param functionality the {@link HederaFunctionality} of the transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException    if a semantic error was discovered. The contained {@code responseCode} provides the
     *                              error reason.
     */
    private void checkTransactionSemantics(
            @NonNull final TransactionBody txBody, @NonNull final HederaFunctionality functionality)
            throws PreCheckException {
        requireNonNull(txBody);
        requireNonNull(functionality);

        if (!Objects.equals(nodeAccountID, txBody.nodeAccountID())) {
            throw new PreCheckException(INVALID_NODE_ACCOUNT);
        }

        var txnId = txBody.transactionID();
        if (txnId.scheduled() || txnId.nonce() != USER_TRANSACTION_NONCE) {
            throw new PreCheckException(TRANSACTION_ID_FIELD_NOT_ALLOWED);
        }
    }

    /**
     * Checks the signature of the payer. <em>Currently not implemented.</em>
     *
     * @param state         the {@link HederaState} that should be used to read state
     * @param transaction   the relevant {@link Transaction}
     * @param signatureMap  the {@link SignatureMap} contained in the transaction
     * @param payerID       the {@link AccountID} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException    if an error is found while checking the signature. The contained {@code responseCode}
     *                              provides the error reason.
     */
    private void checkPayerSignature(
            @NonNull final HederaState state,
            @NonNull final Transaction transaction,
            @NonNull final SignatureMap signatureMap,
            @NonNull final AccountID payerID)
            throws PreCheckException {
        // TODO - replace with a refactored version of the keys and signatures API
        final var payerSigStatus = signaturePreparer.syncGetPayerSigStatus(transaction);

        if (payerSigStatus != OK) {
            throw new PreCheckException(payerSigStatus);
        }
    }

    /**
     * Checks the solvency of the payer account for the given transaction.
     * <p>
     * TODO - replace with a refactored version of the mono solvency check
     *
     * @param transaction the {@link Transaction} in question
     * @throws NullPointerException         if any argument is {@code null}
     * @throws InsufficientBalanceException if the payer balance is not sufficient
     */
    private void checkSolvency(@NonNull final Transaction transaction) throws PreCheckException {
        final var accessor = SignedTxnAccessor.uncheckedFrom(transaction);
        final var payerNum = EntityNum.fromAccountId(accessor.getPayer());
        final var payerStatus = solvencyPrecheck.payerAccountStatus2(payerNum);
        if (payerStatus != OK) {
            throw new PreCheckException(payerStatus);
        }
        final var solvencySummary = solvencyPrecheck.solvencyOfVerifiedPayer(accessor, false);
        final var validity = PbjConverter.toPbj(solvencySummary.getValidity());
        if (validity != OK) {
            throw new InsufficientBalanceException(validity, solvencySummary.getRequiredFee());
        }
    }
}
