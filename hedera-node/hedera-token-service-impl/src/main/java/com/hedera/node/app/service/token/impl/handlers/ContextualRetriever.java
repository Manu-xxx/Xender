/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Class for retrieving objects in a certain context, e.g. during a {@code handler.handle(...)} call.
 * This allows compartmentalizing common validation logic without requiring store implementations to
 * throw inappropriately-contextual exceptions, and also abstracts duplicated business logic out of
 * multiple handlers.
 */
public class ContextualRetriever {

    private ContextualRetriever() {
        throw new UnsupportedOperationException("Utility class only");
    }

    /**
     * Returns the account if it exists and is usable. A {@link HandleException} is thrown if the account is invalid. Note that this method should also work with account ID's that represent smart contracts
     *
     * @param accountId the ID of the account to get
     * @param accountStore the {@link ReadableTokenStore} to use for account retrieval
     * @param expiryValidator the {@link ExpiryValidator} to determine if the account is expired
     * @throws HandleException if any of the account conditions are not met
     */
    public static Account getIfUsable(
            @NonNull final AccountID accountId,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final ExpiryValidator expiryValidator) {
        requireNonNull(accountId);
        requireNonNull(accountStore);
        requireNonNull(expiryValidator);

        final var acct = accountStore.getAccountById(accountId);
        validateTrue(acct != null, INVALID_ACCOUNT_ID);
        validateFalse(acct.deleted(), ACCOUNT_DELETED);
        final var isSmartContract = acct.smartContract();
        validateFalse(
                acct.expiredAndPendingRemoval(),
                isSmartContract ? CONTRACT_EXPIRED_AND_PENDING_REMOVAL : ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        final var expiryStatus = expiryValidator.expirationStatus(
                isSmartContract ? EntityType.CONTRACT : EntityType.ACCOUNT, false, acct.tinybarBalance());
        validateTrue(expiryStatus == OK, expiryStatus);

        return acct;
    }

    /**
     * Returns the token if it exists and is usable. A {@link HandleException} is thrown if the token is invalid
     *
     * @param tokenId the ID of the token to get
     * @param tokenStore the {@link ReadableTokenStore} to use for token retrieval
     * @throws HandleException if any of the token conditions are not met
     */
    public static Token getIfUsable(@NonNull final TokenID tokenId, @NonNull final ReadableTokenStore tokenStore) {
        requireNonNull(tokenId);
        requireNonNull(tokenStore);

        final var token = tokenStore.get(tokenId);
        validateTrue(token != null, INVALID_TOKEN_ID);
        validateFalse(token.deleted(), TOKEN_WAS_DELETED);
        validateFalse(token.paused(), TOKEN_IS_PAUSED);
        return token;
    }
}
