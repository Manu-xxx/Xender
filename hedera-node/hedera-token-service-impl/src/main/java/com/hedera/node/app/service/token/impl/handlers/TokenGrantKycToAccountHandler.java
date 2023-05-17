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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_GRANT_KYC_TO_ACCOUNT}.
 */
@Singleton
public class TokenGrantKycToAccountHandler implements TransactionHandler {
    @Inject
    public TokenGrantKycToAccountHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenGrantKycOrThrow();
        pureChecks(op);

        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMeta = tokenStore.getTokenMeta(op.tokenOrElse(TokenID.DEFAULT));
        if (tokenMeta == null) throw new PreCheckException(INVALID_TOKEN_ID);
        if (tokenMeta.hasKycKey()) {
            context.requireKey(tokenMeta.kycKey());
        }
    }

    /**
     * Performs checks independent of state or context
     */
    private void pureChecks(@NonNull final TokenGrantKycTransactionBody op) throws PreCheckException {
        if (!op.hasToken()) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }

        if (!op.hasAccount()) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * @param txnBody the {@link TokenGrantKycTransactionBody} of the active transaction
     * @param tokenRelStore the {@link WritableTokenRelationStore} for the active transaction
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionBody txnBody, @NonNull WritableTokenRelationStore tokenRelStore) {
        requireNonNull(txnBody);
        requireNonNull(tokenRelStore);

        final var op = txnBody.tokenGrantKycOrThrow();

        final var targetTokenId = op.tokenOrThrow();
        final var targetAccountId = op.accountOrThrow();
        final var tokenRelation =
                tokenRelStore.getForModify(targetTokenId.tokenNum(), targetAccountId.accountNumOrThrow());

        final var tokenRelBuilder = tokenRelation.orElseThrow().copyBuilder();
        tokenRelBuilder.kycGranted(true);
        tokenRelStore.put(tokenRelBuilder.build());
    }
}
