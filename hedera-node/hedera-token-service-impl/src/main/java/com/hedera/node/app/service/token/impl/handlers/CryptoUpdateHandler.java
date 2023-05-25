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
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CRYPTO_UPDATE}.
 */
@Singleton
public class CryptoUpdateHandler implements TransactionHandler {

    private final CryptoSignatureWaivers waivers;

    @Inject
    public CryptoUpdateHandler(@NonNull final CryptoSignatureWaivers waivers) {
        this.waivers = requireNonNull(waivers, "The supplied argument 'waivers' must not be null");
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        requireNonNull(waivers);
        final var txn = context.body();
        final var payer = context.payer();
        final var op = txn.cryptoUpdateAccountOrThrow();
        final var updateAccountId = op.accountIDToUpdateOrElse(AccountID.DEFAULT);

        final var newAccountKeyMustSign = !waivers.isNewKeySignatureWaived(txn, payer);
        final var targetAccountKeyMustSign = !waivers.isTargetAccountSignatureWaived(txn, payer);
        if (targetAccountKeyMustSign) {
            context.requireKeyOrThrow(updateAccountId, INVALID_ACCOUNT_ID);
        }
        if (newAccountKeyMustSign && op.hasKey()) {
            context.requireKey(op.keyOrThrow());
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
