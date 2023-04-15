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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.handlers.CryptoUpdateHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class CryptoUpdateHandlerTest extends CryptoHandlerTestBase {
    private final AccountID updateAccountId =
            AccountID.newBuilder().accountNum(32132).build();
    private final Key updateAccountKey = A_COMPLEX_KEY;

    @Mock
    private Account updateAccount;

    private CryptoUpdateHandler subject = new CryptoUpdateHandler();

    @Test
    void cryptoUpdateVanilla() throws PreCheckException {
        final var txn = cryptoUpdateTransaction(id, updateAccountId);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(updateAccountId.accountNum())))
                .willReturn(updateAccount);
        given(updateAccount.key()).willReturn(updateAccountKey);
        given(waivers.isNewKeySignatureWaived(txn, id)).willReturn(false);
        given(waivers.isTargetAccountSignatureWaived(txn, id)).willReturn(false);

        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context, waivers);
        basicMetaAssertions(context, 2);
        assertEquals(key, context.payerKey());
        assertTrue(context.requiredNonPayerKeys().contains(updateAccountKey));
    }

    @Test
    void cryptoUpdateNewSignatureKeyWaivedVanilla() throws PreCheckException {
        final var txn = cryptoUpdateTransaction(id, updateAccountId);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(updateAccountId.accountNum())))
                .willReturn(updateAccount);
        given(updateAccount.key()).willReturn(updateAccountKey);
        given(waivers.isNewKeySignatureWaived(txn, id)).willReturn(true);
        given(waivers.isTargetAccountSignatureWaived(txn, id)).willReturn(false);

        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context, waivers);
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
        assertIterableEquals(List.of(updateAccountKey), context.requiredNonPayerKeys());
    }

    @Test
    void cryptoUpdateTargetSignatureKeyWaivedVanilla() throws PreCheckException {
        final var txn = cryptoUpdateTransaction(id, updateAccountId);
        given(waivers.isNewKeySignatureWaived(txn, id)).willReturn(false);
        given(waivers.isTargetAccountSignatureWaived(txn, id)).willReturn(true);

        final var context = new PreHandleContext(readableStore, txn);
        subject.preHandle(context, waivers);
        basicMetaAssertions(context, 1);
        assertEquals(key, context.payerKey());
        assertFalse(context.requiredNonPayerKeys().contains(updateAccountKey));
    }

    @Test
    void cryptoUpdateUpdateAccountMissingFails() throws PreCheckException {
        final var txn = cryptoUpdateTransaction(id, updateAccountId);
        given(readableAccounts.get(EntityNumVirtualKey.fromLong(updateAccountId.accountNum())))
                .willReturn(null);

        given(waivers.isNewKeySignatureWaived(txn, id)).willReturn(true);
        given(waivers.isTargetAccountSignatureWaived(txn, id)).willReturn(false);

        final var context = new PreHandleContext(readableStore, txn);
        assertThrowsPreCheck(() -> subject.preHandle(context, waivers), INVALID_ACCOUNT_ID);
    }

    @Test
    void handleNotImplemented() {
        assertThrows(UnsupportedOperationException.class, () -> subject.handle());
    }

    private TransactionBody cryptoUpdateTransaction(final AccountID payerId, final AccountID accountToUpdate) {
        final var transactionID =
                TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp);
        final var updateTxnBody = CryptoUpdateTransactionBody.newBuilder()
                .accountIDToUpdate(accountToUpdate)
                .key(key)
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoUpdateAccount(updateTxnBody)
                .build();
    }
}
