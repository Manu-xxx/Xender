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

package com.hedera.node.app.service.token.impl.test.util;

import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.notNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.util.TokenHandlerHelper;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenHandlerHelperTest {
    private static final AccountID ACCT_2300 =
            AccountID.newBuilder().accountNum(2300L).build();
    private static final TokenID TOKEN_ID_45 = TokenID.newBuilder().tokenNum(45).build();

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private ReadableTokenStore tokenStore;

    @Mock
    private ReadableTokenRelationStore tokenRelStore;

    @Mock
    private ExpiryValidator expiryValidator;

    @SuppressWarnings("DataFlowIssue")
    @Test
    void account_getIfUsable_nullArg() {
        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(null, accountStore, expiryValidator))
                .isInstanceOf(NullPointerException.class);

        final var acctId = ACCT_2300;
        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(acctId, null, expiryValidator))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(acctId, accountStore, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void account_getIfUsable_nullAccount() {
        BDDMockito.given(accountStore.getAccountById(notNull())).willReturn(null);

        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(ACCT_2300, accountStore, expiryValidator))
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

        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(ACCT_2300, accountStore, expiryValidator))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_DELETED));
    }

    @Test
    void account_getIfUsable_expiredAndPendingRemovalAccount() {
        BDDMockito.given(accountStore.getAccountById(notNull()))
                .willReturn(Account.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tinybarBalance(0L)
                        .deleted(false)
                        .smartContract(false)
                        .expiredAndPendingRemoval(true)
                        .build());

        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(ACCT_2300, accountStore, expiryValidator))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
    }

    @Test
    void contract_getIfUsable_expiredAndPendingRemovalContract() {
        BDDMockito.given(accountStore.getAccountById(notNull()))
                .willReturn(Account.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tinybarBalance(0L)
                        .deleted(false)
                        .smartContract(true)
                        .expiredAndPendingRemoval(true)
                        .build());

        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(ACCT_2300, accountStore, expiryValidator))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL));
    }

    @Test
    void account_getIfUsable_accountTypeIsExpired() {
        BDDMockito.given(accountStore.getAccountById(notNull()))
                .willReturn(Account.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tinybarBalance(0L)
                        .deleted(false)
                        .smartContract(false)
                        .expiredAndPendingRemoval(false)
                        .build());
        BDDMockito.given(expiryValidator.expirationStatus(notNull(), anyBoolean(), anyLong()))
                .willReturn(ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);

        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(ACCT_2300, accountStore, expiryValidator))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
    }

    @Test
    void contract_getIfUsable_contractTypeIsExpired() {
        BDDMockito.given(accountStore.getAccountById(notNull()))
                .willReturn(Account.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tinybarBalance(0L)
                        .deleted(false)
                        .smartContract(true)
                        .expiredAndPendingRemoval(false)
                        .build());
        BDDMockito.given(expiryValidator.expirationStatus(notNull(), anyBoolean(), anyLong()))
                .willReturn(ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL);

        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(ACCT_2300, accountStore, expiryValidator))
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

        BDDMockito.given(expiryValidator.expirationStatus(notNull(), anyBoolean(), anyLong()))
                .willReturn(ResponseCodeEnum.OK);

        final var result = TokenHandlerHelper.getIfUsable(ACCT_2300, accountStore, expiryValidator);
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

        BDDMockito.given(expiryValidator.expirationStatus(notNull(), anyBoolean(), anyLong()))
                .willReturn(ResponseCodeEnum.OK);

        final var result = TokenHandlerHelper.getIfUsable(ACCT_2300, accountStore, expiryValidator);
        Assertions.assertThat(result).isNotNull();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void token_getIfUsable_nullArg() {
        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(null, tokenStore))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> getIfUsable(TOKEN_ID_45, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void token_getIfUsable_nullToken() {
        BDDMockito.given(tokenStore.get(notNull())).willReturn(null);

        Assertions.assertThatThrownBy(() -> getIfUsable(TOKEN_ID_45, tokenStore))
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

        Assertions.assertThatThrownBy(() -> getIfUsable(TOKEN_ID_45, tokenStore))
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

        Assertions.assertThatThrownBy(() -> getIfUsable(TOKEN_ID_45, tokenStore))
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

        final var result = getIfUsable(TOKEN_ID_45, tokenStore);
        Assertions.assertThat(result).isNotNull();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void tokenRel_getIfUsable_nullArg() {
        Assertions.assertThatThrownBy(() -> TokenHandlerHelper.getIfUsable(null, TOKEN_ID_45, tokenRelStore))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> getIfUsable(ACCT_2300, null, tokenRelStore))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> getIfUsable(ACCT_2300, TOKEN_ID_45, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void tokenRel_getIfUsable_notFound() {
        Assertions.assertThatThrownBy(() -> getIfUsable(ACCT_2300, TOKEN_ID_45, tokenRelStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
    }

    @Test
    void tokenRel_getIfUsable_usableTokenRel() {
        BDDMockito.given(tokenRelStore.get(notNull(), notNull()))
                .willReturn(TokenRelation.newBuilder()
                        .accountNumber(ACCT_2300.accountNumOrThrow())
                        .tokenNumber(TOKEN_ID_45.tokenNum())
                        .deleted(false)
                        .balance(0)
                        .build());

        final var result = getIfUsable(ACCT_2300, TOKEN_ID_45, tokenRelStore);
        Assertions.assertThat(result).isNotNull();
    }
}
