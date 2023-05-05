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

package com.hedera.node.app.workflows.dispatcher;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@code TransactionDispatcher} provides functionality to forward pre-check, pre-handle, and handle-transaction
 * requests to the appropriate handler
 *
 * <p>For handle, mostly just supports the limited form of the Consensus Service handlers
 * described in https://github.com/hashgraph/hedera-services/issues/4945, while still trying to make a bit of progress
 * toward the general implementation.
 */
@Singleton
public class TransactionDispatcher {
    private static final String TYPE_NOT_SUPPORTED = "This transaction type is not supported";
    private static final String SYSTEM_DELETE_WITHOUT_ID_CASE = "SystemDelete without IdCase";
    private static final String SYSTEM_UNDELETE_WITHOUT_ID_CASE = "SystemUndelete without IdCase";

    protected final TransactionHandlers handlers;

    /**
     * Creates a {@code TransactionDispatcher}.
     *
     * @param handlers the handlers for all transaction types
     */
    @Inject
    public TransactionDispatcher(@NonNull final TransactionHandlers handlers) {
        this.handlers = requireNonNull(handlers);
    }

    /**
     * Dispatch a handle request. It is forwarded to the correct handler, which takes care of the specific
     * functionality
     *
     * @param context the context of the handle workflow
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void dispatchHandle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context, "The supplied argument 'context' cannot be null!");

        //        final var serviceName = getServiceName(context.body());
        //        context.setServiceScope(serviceName);

        // At this stage, we should always find a handler, otherwise something really weird is going on
        final var handler = getHandler(context.body());
        handler.handle(context);
    }

    /**
     * Dispatch a pre-handle request. It is forwarded to the correct handler, which takes care of the specific
     * functionality
     *
     * @param context the context of the pre-handle workflow
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void dispatchPreHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context, "The supplied argument 'context' cannot be null!");

        try {
            final var handler = getHandler(context.body());
            handler.preHandle(context);
        } catch (UnsupportedOperationException ex) {
            throw new PreCheckException(ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        }
    }

    @NonNull
    private String getServiceName(@NonNull final TransactionBody txBody) {
        return switch (txBody.data().kind()) {
            case CONSENSUS_CREATE_TOPIC,
                    CONSENSUS_UPDATE_TOPIC,
                    CONSENSUS_DELETE_TOPIC,
                    CONSENSUS_SUBMIT_MESSAGE -> ConsensusService.NAME;

            case CONTRACT_CREATE_INSTANCE,
                    CONTRACT_UPDATE_INSTANCE,
                    CONTRACT_CALL,
                    CONTRACT_DELETE_INSTANCE,
                    ETHEREUM_TRANSACTION -> ContractService.NAME;

            case CRYPTO_CREATE_ACCOUNT,
                    CRYPTO_UPDATE_ACCOUNT,
                    CRYPTO_TRANSFER,
                    CRYPTO_DELETE,
                    CRYPTO_APPROVE_ALLOWANCE,
                    CRYPTO_DELETE_ALLOWANCE,
                    CRYPTO_ADD_LIVE_HASH,
                    CRYPTO_DELETE_LIVE_HASH -> TokenService.NAME;

            case FILE_CREATE, FILE_UPDATE, FILE_DELETE, FILE_APPEND -> FileService.NAME;

            case FREEZE -> FreezeService.NAME;

            case UNCHECKED_SUBMIT -> NetworkService.NAME;

            case SCHEDULE_CREATE, SCHEDULE_SIGN, SCHEDULE_DELETE -> ScheduleService.NAME;

            case TOKEN_CREATION,
                    TOKEN_UPDATE,
                    TOKEN_MINT,
                    TOKEN_BURN,
                    TOKEN_DELETION,
                    TOKEN_WIPE,
                    TOKEN_FREEZE,
                    TOKEN_UNFREEZE,
                    TOKEN_GRANT_KYC,
                    TOKEN_REVOKE_KYC,
                    TOKEN_ASSOCIATE,
                    TOKEN_DISSOCIATE,
                    TOKEN_FEE_SCHEDULE_UPDATE,
                    TOKEN_PAUSE,
                    TOKEN_UNPAUSE -> TokenService.NAME;

            case UTIL_PRNG -> UtilService.NAME;

            case SYSTEM_DELETE -> switch (txBody.systemDeleteOrThrow().id().kind()) {
                case CONTRACT_ID -> ContractService.NAME;
                case FILE_ID -> FileService.NAME;
                default -> throw new UnsupportedOperationException(SYSTEM_DELETE_WITHOUT_ID_CASE);
            };
            case SYSTEM_UNDELETE -> switch (txBody.systemUndeleteOrThrow().id().kind()) {
                case CONTRACT_ID -> ContractService.NAME;
                case FILE_ID -> FileService.NAME;
                default -> throw new UnsupportedOperationException(SYSTEM_UNDELETE_WITHOUT_ID_CASE);
            };

            default -> throw new UnsupportedOperationException(TYPE_NOT_SUPPORTED);
        };
    }

    @NonNull
    private TransactionHandler getHandler(@NonNull final TransactionBody txBody) {
        return switch (txBody.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> handlers.consensusCreateTopicHandler();
            case CONSENSUS_UPDATE_TOPIC -> handlers.consensusUpdateTopicHandler();
            case CONSENSUS_DELETE_TOPIC -> handlers.consensusDeleteTopicHandler();
            case CONSENSUS_SUBMIT_MESSAGE -> handlers.consensusSubmitMessageHandler();

            case CONTRACT_CREATE_INSTANCE -> handlers.contractCreateHandler();
            case CONTRACT_UPDATE_INSTANCE -> handlers.contractUpdateHandler();
            case CONTRACT_CALL -> handlers.contractCallHandler();
            case CONTRACT_DELETE_INSTANCE -> handlers.contractDeleteHandler();
            case ETHEREUM_TRANSACTION -> handlers.etherumTransactionHandler();

            case CRYPTO_CREATE_ACCOUNT -> handlers.cryptoCreateHandler();
            case CRYPTO_UPDATE_ACCOUNT -> handlers.cryptoUpdateHandler();
            case CRYPTO_TRANSFER -> handlers.cryptoTransferHandler();
            case CRYPTO_DELETE -> handlers.cryptoDeleteHandler();
            case CRYPTO_APPROVE_ALLOWANCE -> handlers.cryptoApproveAllowanceHandler();
            case CRYPTO_DELETE_ALLOWANCE -> handlers.cryptoDeleteAllowanceHandler();
            case CRYPTO_ADD_LIVE_HASH -> handlers.cryptoAddLiveHashHandler();
            case CRYPTO_DELETE_LIVE_HASH -> handlers.cryptoDeleteLiveHashHandler();

            case FILE_CREATE -> handlers.fileCreateHandler();
            case FILE_UPDATE -> handlers.fileUpdateHandler();
            case FILE_DELETE -> handlers.fileDeleteHandler();
            case FILE_APPEND -> handlers.fileAppendHandler();

            case FREEZE -> handlers.freezeHandler();

            case UNCHECKED_SUBMIT -> handlers.networkUncheckedSubmitHandler();

            case SCHEDULE_CREATE -> handlers.scheduleCreateHandler();
            case SCHEDULE_SIGN -> handlers.scheduleSignHandler();
            case SCHEDULE_DELETE -> handlers.scheduleDeleteHandler();

            case TOKEN_CREATION -> handlers.tokenCreateHandler();
            case TOKEN_UPDATE -> handlers.tokenUpdateHandler();
            case TOKEN_MINT -> handlers.tokenMintHandler();
            case TOKEN_BURN -> handlers.tokenBurnHandler();
            case TOKEN_DELETION -> handlers.tokenDeleteHandler();
            case TOKEN_WIPE -> handlers.tokenAccountWipeHandler();
            case TOKEN_FREEZE -> handlers.tokenFreezeAccountHandler();
            case TOKEN_UNFREEZE -> handlers.tokenUnfreezeAccountHandler();
            case TOKEN_GRANT_KYC -> handlers.tokenGrantKycToAccountHandler();
            case TOKEN_REVOKE_KYC -> handlers.tokenRevokeKycFromAccountHandler();
            case TOKEN_ASSOCIATE -> handlers.tokenAssociateToAccountHandler();
            case TOKEN_DISSOCIATE -> handlers.tokenDissociateFromAccountHandler();
            case TOKEN_FEE_SCHEDULE_UPDATE -> handlers.tokenFeeScheduleUpdateHandler();
            case TOKEN_PAUSE -> handlers.tokenPauseHandler();
            case TOKEN_UNPAUSE -> handlers.tokenUnpauseHandler();

            case UTIL_PRNG -> handlers.utilPrngHandler();

            case SYSTEM_DELETE -> switch (txBody.systemDeleteOrThrow().id().kind()) {
                case CONTRACT_ID -> handlers.contractSystemDeleteHandler();
                case FILE_ID -> handlers.fileSystemDeleteHandler();
                default -> throw new UnsupportedOperationException(SYSTEM_DELETE_WITHOUT_ID_CASE);
            };
            case SYSTEM_UNDELETE -> switch (txBody.systemUndeleteOrThrow().id().kind()) {
                case CONTRACT_ID -> handlers.contractSystemUndeleteHandler();
                case FILE_ID -> handlers.fileSystemUndeleteHandler();
                default -> throw new UnsupportedOperationException(SYSTEM_UNDELETE_WITHOUT_ID_CASE);
            };

            default -> throw new UnsupportedOperationException(TYPE_NOT_SUPPORTED);
        };
    }
}
