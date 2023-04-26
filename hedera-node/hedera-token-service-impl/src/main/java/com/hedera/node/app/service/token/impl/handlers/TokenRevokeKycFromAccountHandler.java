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

import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenRevokeKycTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_REVOKE_KYC_FROM_ACCOUNT}.
 */
@Singleton
public class TokenRevokeKycFromAccountHandler implements TransactionHandler {
    @Inject
    public TokenRevokeKycFromAccountHandler() {
        // Exists for injection
    }

    /**
     * This method is called during the pre-handle workflow.
     *
     * @param context the {@link PreHandleContext} which collects all information
     * @throws PreCheckException    for invalid tokens or if the token has no KYC key
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenRevokeKycOrThrow();
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMeta = tokenStore.getTokenMeta(op.tokenOrElse(TokenID.DEFAULT));
        if (tokenMeta == null) throw new PreCheckException(ResponseCodeEnum.INVALID_TOKEN_ID);
        if (tokenMeta.hasKycKey()) {
            context.requireKey(tokenMeta.kycKey());
        } else {
            throw new PreCheckException(TOKEN_HAS_NO_KYC_KEY);
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @param txn           the {@link TransactionBody} of the active transaction
     * @param tokenRelStore the {@link WritableTokenRelationStore} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull TransactionBody txn, @NonNull WritableTokenRelationStore tokenRelStore) {
        requireNonNull(txn);
        requireNonNull(tokenRelStore);

        final var op = txn.tokenRevokeKycOrThrow();
        verifyOp(op);

        final var tokenId = op.tokenOrThrow().tokenNum();
        final var accountId = op.accountOrElse(AccountID.DEFAULT).accountNumOrThrow();
        final var tokenRel = tokenRelStore.getForModify(tokenId, accountId);

        final var tokenRelBuilder = tokenRel.orElseThrow().copyBuilder();
        tokenRelBuilder.kycGranted(false);
        tokenRelStore.put(tokenRelBuilder.build());
    }

    private void verifyOp(TokenRevokeKycTransactionBody op) {
        if (!op.hasToken()) {
            throw new HandleException(ResponseCodeEnum.INVALID_TOKEN_ID);
        }

        if (!op.hasAccount()) {
            throw new HandleException(ResponseCodeEnum.INVALID_ACCOUNT_ID);
        }
    }
}
