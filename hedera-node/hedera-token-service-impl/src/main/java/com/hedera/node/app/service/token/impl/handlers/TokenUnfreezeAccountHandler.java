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
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_UNFREEZE_ACCOUNT}.
 */
@Singleton
public class TokenUnfreezeAccountHandler implements TransactionHandler {
    @Inject
    public TokenUnfreezeAccountHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenUnfreezeOrThrow();
        pureChecks(op);

        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMeta = tokenStore.getTokenMeta(op.tokenOrElse(TokenID.DEFAULT));
        if (tokenMeta == null) throw new PreCheckException(INVALID_TOKEN_ID);
        if (tokenMeta.hasFreezeKey()) {
            context.requireKey(tokenMeta.freezeKey());
        } else {
            throw new PreCheckException(TOKEN_HAS_NO_FREEZE_KEY);
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle() {
        throw new UnsupportedOperationException("Not implemented");
    }

    private void pureChecks(TokenUnfreezeAccountTransactionBody op) throws PreCheckException {
        if (!op.hasToken()) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }

        if (!op.hasAccount()) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }
    }
}
