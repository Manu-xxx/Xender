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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.mockito.ArgumentMatchers.notNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.mono.context.properties.EntityType;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.ContextualRetriever;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.AutoRenewConfig;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextualRetrieverTest {
    private static final AccountID ACCT_2300 =
            AccountID.newBuilder().accountNum(2300L).build();
    private static final TokenID TOKEN_ID_45 = TokenID.newBuilder().tokenNum(45).build();

    private static final AutoRenewConfig ALL_EXPIRY_DISABLED = new AutoRenewConfig(Set.of());
    private static final AutoRenewConfig CONTRACT_EXPIRY_ENABLED = new AutoRenewConfig(Set.of(EntityType.CONTRACT));

    private static final AutoRenewConfig ACCOUNT_EXPIRY_ENABLED = new AutoRenewConfig(Set.of(EntityType.ACCOUNT));

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private ReadableTokenStore tokenStore;

    @SuppressWarnings("DataFlowIssue")
    @Test
    void account_getIfUsable_nullArg() {
        Assertions.assertThatThrownBy(() -> ContextualRetriever.getIfUsable(null, accountStore, ALL_EXPIRY_DISABLED))
                .isInstanceOf(NullPointerException.class);

        final var acctId = ACCT_2300;
        Assertions.assertThatThrownBy(() -> ContextualRetriever.getIfUsable(acctId, null, ALL_EXPIRY_DISABLED))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> ContextualRetriever.getIfUsable(acctId, accountStore, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void account_getIfUsable_nullAccount() {
        BDDMockito.given(accountStore.getAccountById(notNull())).willReturn(null);

        Assertions.assertThatThrownBy(
                        () -> ContextualRetriever.getIfUsable(ACCT_2300, accountStore, ALL_EXPIRY_DISABLED))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_ACCOUNT_ID));
    }

    @Test
    void account_getIfUsable_deletedAccount() {
        BDDMockito.given(accountStore.getAccountById(notNull()))
                .willReturn(Account.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tinybarBalance(0L)
                        .deleted(true)
                        .build());

        Assertions.assertThatThrownBy(
                        () -> ContextualRetriever.getIfUsable(ACCT_2300, accountStore, ALL_EXPIRY_DISABLED))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_DELETED));
    }

    @Test
    void account_getIfUsable_expiredAccount() {
        BDDMockito.given(accountStore.getAccountById(notNull()))
                .willReturn(Account.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tinybarBalance(0L)
                        .deleted(false)
                        .smartContract(false)
                        .expiredAndPendingRemoval(true)
                        .build());

        Assertions.assertThatThrownBy(
                        () -> ContextualRetriever.getIfUsable(ACCT_2300, accountStore, ACCOUNT_EXPIRY_ENABLED))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
    }

    @Test
    void contract_getIfUsable_expiredContract() {
        BDDMockito.given(accountStore.getAccountById(notNull()))
                .willReturn(Account.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tinybarBalance(0L)
                        .deleted(false)
                        .smartContract(true)
                        .expiredAndPendingRemoval(true)
                        .build());

        Assertions.assertThatThrownBy(
                        () -> ContextualRetriever.getIfUsable(ACCT_2300, accountStore, CONTRACT_EXPIRY_ENABLED))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL));
    }

    @Test
    void account_getIfUsable_usableAccount() {
        BDDMockito.given(accountStore.getAccountById(notNull()))
                .willReturn(Account.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tinybarBalance(0L)
                        .deleted(false)
                        .smartContract(false)
                        .expiredAndPendingRemoval(false)
                        .build());

        final var result = ContextualRetriever.getIfUsable(ACCT_2300, accountStore, CONTRACT_EXPIRY_ENABLED);
        Assertions.assertThat(result).isNotNull();
    }

    @Test
    void contract_getIfUsable_usableContract() {
        BDDMockito.given(accountStore.getAccountById(notNull()))
                .willReturn(Account.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tinybarBalance(0L)
                        .deleted(false)
                        .smartContract(true)
                        .expiredAndPendingRemoval(false)
                        .build());

        final var result = ContextualRetriever.getIfUsable(ACCT_2300, accountStore, CONTRACT_EXPIRY_ENABLED);
        Assertions.assertThat(result).isNotNull();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void token_getIfUsable_nullArg() {
        Assertions.assertThatThrownBy(() -> ContextualRetriever.getIfUsable(null, tokenStore))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> ContextualRetriever.getIfUsable(TOKEN_ID_45, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void token_getIfUsable_nullToken() {
        BDDMockito.given(tokenStore.get(notNull())).willReturn(null);

        Assertions.assertThatThrownBy(() -> ContextualRetriever.getIfUsable(TOKEN_ID_45, tokenStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_ID));
    }

    @Test
    void token_getIfUsable_deletedToken() {
        BDDMockito.given(tokenStore.get(notNull()))
                .willReturn(Token.newBuilder()
                        .tokenNumber(TOKEN_ID_45.tokenNum())
                        .deleted(true)
                        .paused(false)
                        .build());

        Assertions.assertThatThrownBy(() -> ContextualRetriever.getIfUsable(TOKEN_ID_45, tokenStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.TOKEN_WAS_DELETED));
    }

    @Test
    void token_getIfUsable_pausedToken() {
        BDDMockito.given(tokenStore.get(notNull()))
                .willReturn(Token.newBuilder()
                        .tokenNumber(TOKEN_ID_45.tokenNum())
                        .deleted(false)
                        .paused(true)
                        .build());

        Assertions.assertThatThrownBy(() -> ContextualRetriever.getIfUsable(TOKEN_ID_45, tokenStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.TOKEN_IS_PAUSED));
    }

    @Test
    void token_getIfUsable_usableToken() {
        BDDMockito.given(tokenStore.get(notNull()))
                .willReturn(Token.newBuilder()
                        .tokenNumber(TOKEN_ID_45.tokenNum())
                        .deleted(false)
                        .paused(false)
                        .build());

        final var result = ContextualRetriever.getIfUsable(TOKEN_ID_45, tokenStore);
        Assertions.assertThat(result).isNotNull();
    }
}
