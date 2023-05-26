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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_INITIAL_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RECEIVE_RECORD_THRESHOLD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SEND_RECORD_THRESHOLD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_CREATE}.
 */
@Singleton
public class CryptoCreateHandler extends BaseCryptoHandler implements TransactionHandler {
    @Inject
    public CryptoCreateHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().cryptoCreateAccountOrThrow();
        pureChecks(context.body());
        if (op.hasKey()) {
            final var receiverSigReq = op.receiverSigRequired();
            if (receiverSigReq && op.hasKey()) {
                context.requireKey(op.keyOrThrow());
            }
        }
    }

    /**
     * This method is called during the handle workflow. It executes the {@code CryptoCreate}
     * transaction, creating a new account with the given properties.
     * If the transaction is successful, the account is created and the payer account is charged
     * the transaction fee and the initial balance of new account and the balance of the
     * new account is set to the initial balance.
     * If the transaction is not successful, the account is not created and the payer account is
     * charged the transaction fee.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws HandleException      if the transaction is not successful due to payer
     * account being deleted or has insufficient balance or the account is not created due to
     * the usage of a price regime
     */
    @Override
    public void handle(@NonNull final HandleContext handleContext) {
        requireNonNull(handleContext);
        final var txnBody = handleContext.body();
        final var op = txnBody.cryptoCreateAccount();

        // validate fields in the transaction body that involves checking with
        // dynamic properties or state
        final ResponseCodeEnum validationResult = validateSemantics();
        if (validationResult != OK) {
            throw new HandleException(validationResult);
        }

        // FUTURE: Use the config and check if accounts can be created.
        //  Currently, this check is being done in `finishCryptoCreate` before `commit`

        // validate payer account exists and has enough balance
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);
        final var optionalPayer = accountStore.getForModify(
                txnBody.transactionIDOrElse(TransactionID.DEFAULT).accountIDOrElse(AccountID.DEFAULT));
        if (optionalPayer.isEmpty()) {
            throw new HandleException(INVALID_PAYER_ACCOUNT_ID);
        }
        final var payer = optionalPayer.get();
        final long newPayerBalance = payer.tinybarBalance() - op.initialBalance();
        validatePayer(payer, newPayerBalance);

        // Change payer's balance to reflect the deduction of the initial balance for the new
        // account
        final var modifiedPayer =
                payer.copyBuilder().tinybarBalance(newPayerBalance).build();
        accountStore.put(modifiedPayer);

        // Build the new account to be persisted based on the transaction body
        final var accountCreated = buildAccount(op, handleContext);
        accountStore.put(accountCreated);

        // set newly created account number in the record builder
        final var createdAccountNum = accountCreated.accountNumber();
        final var createdAccountID =
                AccountID.newBuilder().accountNum(createdAccountNum).build();
        final var recordBuilder = handleContext.recordBuilder(CryptoCreateRecordBuilder.class);
        recordBuilder.accountID(createdAccountID);

        // put if any new alias is associated with the account into account store
        if (op.alias() != Bytes.EMPTY) {
            accountStore.putAlias(op.alias().toString(), createdAccountNum);
        }
    }

    /* ----------- Helper Methods ----------- */

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        final var op = txn.cryptoCreateAccountOrThrow();
        validateTruePreCheck(op.initialBalance() >= 0L, INVALID_INITIAL_BALANCE);
        validateTruePreCheck(op.hasAutoRenewPeriod(), INVALID_RENEWAL_PERIOD);
        validateTruePreCheck(
                op.sendRecordThreshold() >= 0L, INVALID_SEND_RECORD_THRESHOLD); // FUTURE: should this return
        // SEND_RECORD_THRESHOLD_FIELD_IS_DEPRECATED
        validateTruePreCheck(
                op.receiveRecordThreshold() >= 0L, INVALID_RECEIVE_RECORD_THRESHOLD); // FUTURE: should this return
        // RECEIVE_RECORD_THRESHOLD_FIELD_IS_DEPRECATED
        validateTruePreCheck(
                !op.hasProxyAccountID()
                        || (op.hasProxyAccountID() && op.proxyAccountID().equals(AccountID.DEFAULT)),
                PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED);
    }

    /**
     * Validate the fields in the transaction body that involves checking with dynamic
     * properties or state. This check is done as part of the handle workflow.
     * @return OK if the transaction body is valid, otherwise return the appropriate error code
     */
    private ResponseCodeEnum validateSemantics() {
        // FUTURE : Need to add validations that involve dynamic properties or state
        return OK;
    }

    /**
     * Validates the payer account exists and has enough balance to cover the initial balance of the
     * account to be created.
     *
     * @param payer the payer account
     * @param newPayerBalance the initial balance of the account to be created
     */
    private void validatePayer(@NonNull final Account payer, final long newPayerBalance) {
        // If the payer account is deleted, throw an exception
        if (payer.deleted()) {
            throw new HandleException(ACCOUNT_DELETED);
        }
        if (newPayerBalance < 0) {
            throw new HandleException(INSUFFICIENT_PAYER_BALANCE);
        }
        // FUTURE: check if payer account is detached when we have started expiring accounts ?
    }

    /**
     * Builds an account based on the transaction body and the consensus time.
     *
     * @param op the transaction body
     * @param handleContext the handle context
     * @return the account created
     */
    private Account buildAccount(CryptoCreateTransactionBody op, HandleContext handleContext) {
        long autoRenewPeriod = op.autoRenewPeriodOrThrow().seconds();
        long consensusTime = handleContext.consensusNow().getEpochSecond();
        long expiry = consensusTime + autoRenewPeriod;
        var builder = Account.newBuilder()
                .memo(op.memo())
                .expiry(expiry)
                .autoRenewSecs(autoRenewPeriod)
                .receiverSigRequired(op.receiverSigRequired())
                .maxAutoAssociations(op.maxAutomaticTokenAssociations())
                .tinybarBalance(op.initialBalance())
                .declineReward(op.declineReward());

        if (onlyKeyProvided(op)) {
            builder.key(op.keyOrThrow());
        } else if (keyAndAliasProvided(op)) {
            builder.key(op.keyOrThrow()).alias(op.alias());
        }

        if (op.hasStakedAccountId() || op.hasStakedNodeId()) {
            final var stakeNumber =
                    getStakedId(op.stakedId().kind().toString(), op.stakedNodeId(), op.stakedAccountId());
            builder.stakedNumber(stakeNumber);
        }
        // set the new account number
        builder.accountNumber(handleContext.newEntityNum());
        return builder.build();
    }

    /**
     * Checks if only key is provided.
     *
     * @param op the transaction body
     * @return true if only key is provided, false otherwise
     */
    private boolean onlyKeyProvided(@NonNull final CryptoCreateTransactionBody op) {
        return op.hasKey() && op.alias().equals(Bytes.EMPTY);
    }

    /**
     * Checks if both key and alias are provided.
     *
     * @param op the transaction body
     * @return true if both key and alias are provided, false otherwise
     */
    private boolean keyAndAliasProvided(@NonNull final CryptoCreateTransactionBody op) {
        return op.hasKey() && !op.alias().equals(Bytes.EMPTY);
    }
}
