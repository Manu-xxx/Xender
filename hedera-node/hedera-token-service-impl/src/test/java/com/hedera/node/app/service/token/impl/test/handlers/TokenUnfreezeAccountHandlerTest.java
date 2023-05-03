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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.AdapterUtils.txnFrom;
import static com.hedera.node.app.service.token.impl.test.util.MetaAssertion.basicContextAssertions;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.UNFREEZE_WITH_INVALID_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.UNFREEZE_WITH_MISSING_FREEZE_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.VALID_UNFREEZE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.node.app.spi.fixtures.Assertions;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TokenUnfreezeAccountHandlerTest {

    private static final AccountID ACCOUNT_13257 =
            AccountID.newBuilder().accountNum(13257L).build();

    private ReadableAccountStore accountStore;
    private ReadableTokenStore tokenStore;

    private TokenUnfreezeAccountHandler subject;

    @BeforeEach
    void setUp() {
        accountStore = SigReqAdapterUtils.wellKnownAccountStoreAt();
        tokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt();
        subject = new TokenUnfreezeAccountHandler();
    }

    @Nested
    class PreHandleTest {
        @Test
        void tokenUnfreezeWithNoToken() throws PreCheckException {
            final var theTxn = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_13257))
                    .tokenUnfreeze(
                            TokenUnfreezeAccountTransactionBody.newBuilder().account(ACCOUNT_13257))
                    .build();

            final var context = new FakePreHandleContext(accountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            Assertions.assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOKEN_ID);
        }

        @Test
        void tokenUnfreezeWithNoAccount() throws PreCheckException {
            final var theTxn = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_13257))
                    .tokenUnfreeze(TokenUnfreezeAccountTransactionBody.newBuilder()
                            .token(TokenID.newBuilder().tokenNum(123L)))
                    .build();

            final var context = new FakePreHandleContext(accountStore, theTxn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            Assertions.assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_ACCOUNT_ID);
        }
    }

    @Nested
    class TokenUnfreezeAccountHandlerParityTest {
        @Test
        void tokenUnfreezeWithExtantFreezable() throws PreCheckException {
            final var txn = txnFrom(VALID_UNFREEZE_WITH_EXTANT_TOKEN);

            final var context = new FakePreHandleContext(accountStore, txn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            subject.preHandle(context);

            assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
            assertThat(context.requiredNonPayerKeys(), contains(TOKEN_FREEZE_KT.asPbjKey()));
            basicContextAssertions(context, 1);
        }

        @Test
        void tokenUnfreezeMissingToken() throws PreCheckException {
            final var txn = txnFrom(UNFREEZE_WITH_MISSING_FREEZE_TOKEN);

            final var context = new FakePreHandleContext(accountStore, txn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            assertThrowsPreCheck(() -> subject.preHandle(context), TOKEN_HAS_NO_FREEZE_KEY);
        }

        @Test
        void tokenUnfreezeWithInvalidToken() throws PreCheckException {
            final var txn = txnFrom(UNFREEZE_WITH_INVALID_TOKEN);

            final var context = new FakePreHandleContext(accountStore, txn);
            context.registerStore(ReadableTokenStore.class, tokenStore);
            assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOKEN_ID);
        }
    }
}
