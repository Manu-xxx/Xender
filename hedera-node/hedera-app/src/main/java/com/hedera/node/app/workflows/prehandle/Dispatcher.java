/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows.prehandle;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.token.impl.AccountStore;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.Handlers;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code PreHandleDispatcher} takes a validated transaction and dispatches it to the correct
 * handler
 */
public class Dispatcher {

    private final Handlers handlers;

    private final AccountStore accountStore;

    /**
     * Constructor of {@code PreHandleDispatcherImpl}
     *
     * @param hederaState the {@link HederaState} this dispatcher is bound to
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    public Dispatcher(
            @NonNull final Handlers handlers,
            @NonNull final HederaState hederaState) {
        this.handlers = requireNonNull(handlers);
        requireNonNull(hederaState);

        final var cryptoStates = hederaState.createReadableStates(HederaState.CRYPTO_SERVICE);
        accountStore = new AccountStore(cryptoStates);
        // TODO: Create other store(s)
    }

    /**
     * Dispatch a pre-check request. It is forwarded to the correct handler, which takes care of
     * the specific functionality.
     *
     * @param transactionBody the {@link TransactionBody} of the request
     * @throws NullPointerException if {@code transactionBody} is {@code null}
     * @throws PreCheckException if validation fails
     */
    public void preCheck(@NonNull final TransactionBody transactionBody) throws PreCheckException {
        requireNonNull(transactionBody);

        final var handler = getHandler(transactionBody);
        handler.preCheck(transactionBody);
    }

    /**
     * Dispatch a pre-handle request. It is forwarded to the correct handler, which takes care of
     * the specific functionality. The payer is taken from the transaction.
     *
     * @param transactionBody the {@link TransactionBody} of the request
     * @throws NullPointerException if {@code transactionBody} is {@code null}
     */
    @NonNull
    public TransactionMetadata preHandle(@NonNull final TransactionBody transactionBody) {
        requireNonNull(transactionBody);

        final AccountID payer = transactionBody.getTransactionID().getAccountID();
        return preHandle(transactionBody, payer);
    }

    /**
     * Dispatch a request. It is forwarded to the correct handler, which takes care of the specific
     * functionality
     *
     * @param transactionBody the {@link TransactionBody} of the request
     * @param payer the {@link AccountID} of the payer
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    @NonNull
    public TransactionMetadata preHandle(@NonNull final TransactionBody transactionBody, @NonNull AccountID payer) {
        requireNonNull(transactionBody);
        requireNonNull(payer);

        return switch (transactionBody.getDataCase()) {
            case CONSENSUSCREATETOPIC -> handlers.consensusCreateTopicHandler().preHandle(transactionBody, payer);
            case CONSENSUSUPDATETOPIC -> handlers.consensusUpdateTopicHandler().preHandle(transactionBody, payer);
            case CONSENSUSDELETETOPIC -> handlers.consensusDeleteTopicHandler().preHandle(transactionBody, payer);
            case CONSENSUSSUBMITMESSAGE -> handlers.consensusSubmitMessageHandler().preHandle(transactionBody, payer);

            case CONTRACTCREATEINSTANCE -> handlers.contractCreateHandler().preHandle(transactionBody, payer);
            case CONTRACTUPDATEINSTANCE -> handlers.contractUpdateHandler().preHandle(transactionBody, payer);
            case CONTRACTCALL -> handlers.contractCallHandler().preHandle(transactionBody, payer);
            case CONTRACTDELETEINSTANCE -> handlers.contractDeleteHandler().preHandle(transactionBody, payer);
            case ETHEREUMTRANSACTION -> handlers.etherumTransactionHandler().preHandle(transactionBody, payer);

            case CRYPTOCREATEACCOUNT -> handlers.cryptoCreateHandler().preHandle(transactionBody, payer);
            case CRYPTOUPDATEACCOUNT -> handlers.cryptoUpdateHandler().preHandle(transactionBody, payer, accountStore);
            case CRYPTOTRANSFER -> handlers.cryptoTransferHandler().preHandle(transactionBody, payer);
            case CRYPTODELETE -> handlers.cryptoDeleteHandler().preHandle(transactionBody, payer, accountStore);
            case CRYPTOAPPROVEALLOWANCE -> handlers.cryptoApproveAllowanceHandler().preHandle(transactionBody, payer, accountStore);
            case CRYPTODELETEALLOWANCE -> handlers.cryptoDeleteAllowanceHandler().preHandle(transactionBody, payer, accountStore);
            case CRYPTOADDLIVEHASH -> handlers.cryptoAddLiveHashHandler().preHandle(transactionBody, payer);
            case CRYPTODELETELIVEHASH -> handlers.cryptoDeleteLiveHashHandler().preHandle(transactionBody, payer);

            case FILECREATE -> handlers.fileCreateHandler().preHandle(transactionBody, payer);
            case FILEUPDATE -> handlers.fileUpdateHandler().preHandle(transactionBody, payer);
            case FILEDELETE -> handlers.fileDeleteHandler().preHandle(transactionBody, payer);
            case FILEAPPEND -> handlers.fileAppendHandler().preHandle(transactionBody, payer);

            case FREEZE -> handlers.freezeHandler().preHandle(transactionBody, payer);

            case UNCHECKEDSUBMIT -> handlers.uncheckedSubmitHandler().preHandle(transactionBody, payer);

            case SCHEDULECREATE -> handlers.scheduleCreateHandler().preHandle(transactionBody, payer);
            case SCHEDULESIGN -> handlers.scheduleSignHandler().preHandle(transactionBody, payer);
            case SCHEDULEDELETE -> handlers.scheduleDeleteHandler().preHandle(transactionBody, payer);

            case TOKENCREATION -> handlers.tokenCreateHandler().preHandle(transactionBody, payer);
            case TOKENUPDATE -> handlers.tokenUpdateHandler().preHandle(transactionBody, payer);
            case TOKENMINT -> handlers.tokenMintHandler().preHandle(transactionBody, payer);
            case TOKENBURN -> handlers.tokenBurnHandler().preHandle(transactionBody, payer);
            case TOKENDELETION -> handlers.tokenDeleteHandler().preHandle(transactionBody, payer);
            case TOKENWIPE -> handlers.tokenAccountWipeHandler().preHandle(transactionBody, payer);
            case TOKENFREEZE -> handlers.tokenFreezeAccountHandler().preHandle(transactionBody, payer);
            case TOKENUNFREEZE -> handlers.tokenUnfreezeAccountHandler().preHandle(transactionBody, payer);
            case TOKENGRANTKYC -> handlers.tokenGrantKycToAccountHandler().preHandle(transactionBody, payer);
            case TOKENREVOKEKYC -> handlers.tokenRevokeKycFromAccountHandler().preHandle(transactionBody, payer);
            case TOKENASSOCIATE -> handlers.tokenAssociateToAccountHandler().preHandle(transactionBody, payer);
            case TOKENDISSOCIATE -> handlers.tokenDissociateFromAccountHandler().preHandle(transactionBody, payer);
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler().preHandle(transactionBody, payer);
            case TOKEN_PAUSE -> handlers.tokenPauseHandler().preHandle(transactionBody, payer);
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler().preHandle(transactionBody, payer);

            case UTIL_PRNG -> handlers.utilPrngHandler().preHandle(transactionBody, payer);

            case SYSTEMDELETE -> switch (transactionBody.getSystemDelete().getIdCase()) {
                case CONTRACTID -> handlers.contractSystemDeleteHandler().preHandle(transactionBody, payer);
                case FILEID -> handlers.fileSystemDeleteHandler().preHandle(transactionBody, payer);
                case ID_NOT_SET -> throw new IllegalArgumentException(
                        "SystemDelete without IdCase");
            };
            case SYSTEMUNDELETE -> switch (transactionBody.getSystemUndelete().getIdCase()) {
                case CONTRACTID -> handlers.contractSystemUndeleteHandler().preHandle(transactionBody, payer);
                case FILEID -> handlers.fileSystemUndeleteHandler().preHandle(transactionBody, payer);
                case ID_NOT_SET -> throw new IllegalArgumentException(
                        "SystemUndelete without IdCase");
            };

            case NODE_STAKE_UPDATE, DATA_NOT_SET -> throw new UnsupportedOperationException(
                    "Not implemented");
        };
    }

    private TransactionHandler getHandler(final TransactionBody transactionBody) {
        return switch (transactionBody.getDataCase()) {
            case CONSENSUSCREATETOPIC -> handlers.consensusCreateTopicHandler();
            case CONSENSUSUPDATETOPIC -> handlers.consensusUpdateTopicHandler();
            case CONSENSUSDELETETOPIC -> handlers.consensusDeleteTopicHandler();
            case CONSENSUSSUBMITMESSAGE -> handlers.consensusSubmitMessageHandler();

            case CONTRACTCREATEINSTANCE -> handlers.contractCreateHandler();
            case CONTRACTUPDATEINSTANCE -> handlers.contractUpdateHandler();
            case CONTRACTCALL -> handlers.contractCallHandler();
            case CONTRACTDELETEINSTANCE -> handlers.contractDeleteHandler();
            case ETHEREUMTRANSACTION -> handlers.etherumTransactionHandler();

            case CRYPTOCREATEACCOUNT -> handlers.cryptoCreateHandler();
            case CRYPTOUPDATEACCOUNT -> handlers.cryptoUpdateHandler();
            case CRYPTOTRANSFER -> handlers.cryptoTransferHandler();
            case CRYPTODELETE -> handlers.cryptoDeleteHandler();
            case CRYPTOAPPROVEALLOWANCE -> handlers.cryptoApproveAllowanceHandler();
            case CRYPTODELETEALLOWANCE -> handlers.cryptoDeleteAllowanceHandler();
            case CRYPTOADDLIVEHASH -> handlers.cryptoAddLiveHashHandler();
            case CRYPTODELETELIVEHASH -> handlers.cryptoDeleteLiveHashHandler();

            case FILECREATE -> handlers.fileCreateHandler();
            case FILEUPDATE -> handlers.fileUpdateHandler();
            case FILEDELETE -> handlers.fileDeleteHandler();
            case FILEAPPEND -> handlers.fileAppendHandler();

            case FREEZE -> handlers.freezeHandler();

            case UNCHECKEDSUBMIT -> handlers.uncheckedSubmitHandler();

            case SCHEDULECREATE -> handlers.scheduleCreateHandler();
            case SCHEDULESIGN -> handlers.scheduleSignHandler();
            case SCHEDULEDELETE -> handlers.scheduleDeleteHandler();

            case TOKENCREATION -> handlers.tokenCreateHandler();
            case TOKENUPDATE -> handlers.tokenUpdateHandler();
            case TOKENMINT -> handlers.tokenMintHandler();
            case TOKENBURN -> handlers.tokenBurnHandler();
            case TOKENDELETION -> handlers.tokenDeleteHandler();
            case TOKENWIPE -> handlers.tokenAccountWipeHandler();
            case TOKENFREEZE -> handlers.tokenFreezeAccountHandler();
            case TOKENUNFREEZE -> handlers.tokenUnfreezeAccountHandler();
            case TOKENGRANTKYC -> handlers.tokenGrantKycToAccountHandler();
            case TOKENREVOKEKYC -> handlers.tokenRevokeKycFromAccountHandler();
            case TOKENASSOCIATE -> handlers.tokenAssociateToAccountHandler();
            case TOKENDISSOCIATE -> handlers.tokenDissociateFromAccountHandler();
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler();
            case TOKEN_PAUSE -> handlers.tokenPauseHandler();
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler();

            case UTIL_PRNG -> handlers.utilPrngHandler();

            case SYSTEMDELETE -> switch (transactionBody.getSystemDelete().getIdCase()) {
                case CONTRACTID -> handlers.contractSystemDeleteHandler();
                case FILEID -> handlers.fileSystemDeleteHandler();
                case ID_NOT_SET -> throw new IllegalArgumentException(
                        "SystemDelete without IdCase");
            };
            case SYSTEMUNDELETE -> switch (transactionBody.getSystemUndelete().getIdCase()) {
                case CONTRACTID -> handlers.contractSystemUndeleteHandler();
                case FILEID -> handlers.fileSystemUndeleteHandler();
                case ID_NOT_SET -> throw new IllegalArgumentException(
                        "SystemUndelete without IdCase");
            };

            case NODE_STAKE_UPDATE, DATA_NOT_SET -> throw new UnsupportedOperationException(
                    "Not implemented");
        };
    }
}
