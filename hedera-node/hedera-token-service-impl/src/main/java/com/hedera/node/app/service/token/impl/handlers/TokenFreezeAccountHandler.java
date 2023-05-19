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
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_FREEZE_ACCOUNT}.
 */
@Singleton
public class TokenFreezeAccountHandler implements TransactionHandler {
    @Inject
    public TokenFreezeAccountHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenFreezeOrThrow();
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

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);

        final var op = context.body().tokenFreezeOrThrow();
        final var accountStore = context.readableStore(ReadableAccountStore.class);
        final var tokenStore = context.readableStore(ReadableTokenStore.class);
        final var tokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var tokenRel = validateSemantics(op, accountStore, tokenStore, tokenRelStore);

        final var copyBuilder = tokenRel.copyBuilder();
        copyBuilder.frozen(true);
        tokenRelStore.put(copyBuilder.build());
    }

    /**
     * Performs checks independent of state or context
     */
    private void pureChecks(@NonNull final TokenFreezeAccountTransactionBody op) throws PreCheckException {
        if (!op.hasToken()) {
            throw new PreCheckException(INVALID_TOKEN_ID);
        }

        if (!op.hasAccount()) {
            throw new PreCheckException(INVALID_ACCOUNT_ID);
        }
    }

    /**
     * Performs checks that the given token and accounts from the state are valid and that the
     * token is associated to the account
     *
     * @return the token relation for the given token and account
     */
    private TokenRelation validateSemantics(
            @NonNull final TokenFreezeAccountTransactionBody op,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final WritableTokenRelationStore tokenRelStore)
            throws HandleException {
        // Check that the token exists
        final var tokenId = op.tokenOrElse(TokenID.DEFAULT);
        final var tokenMeta = tokenStore.getTokenMeta(tokenId);
        validateTrue(tokenMeta != null, INVALID_TOKEN_ID);

        // Check that the token has a freeze key
        validateTrue(tokenMeta.hasFreezeKey(), TOKEN_HAS_NO_FREEZE_KEY);

        // Check that the account exists
        final var account = accountStore.getAccountById(op.accountOrElse(AccountID.DEFAULT));
        validateTrue(account != null, INVALID_ACCOUNT_ID);

        // Check that the token is associated to the account
        final var tokenRel = tokenRelStore.getForModify(account.accountNumber(), tokenId.tokenNum());
        validateTrue(tokenRel.isPresent(), TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

        // Return the token relation
        return tokenRel.get();
    }
}
