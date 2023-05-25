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

package com.hedera.node.app.workflows.prehandle;

import static com.hedera.node.app.workflows.prehandle.PreHandleContextListUpdatesTest.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleContextImplTest {
    private static final AccountID PAYER = AccountID.newBuilder().accountNum(3L).build();

    private Key payerKey = A_COMPLEX_KEY;

    private Key otherKey = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(1)
                    .keys(KeyList.newBuilder()
                            .keys(Key.newBuilder()
                                    .contractID(ContractID.newBuilder()
                                            .contractNum(123456L)
                                            .build())
                                    .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                                    .build())))
            .build();

    @Mock
    ReadableStoreFactory storeFactory;

    @Mock
    ReadableAccountStore accountStore;

    @Mock
    Account account;

    @Mock
    Configuration configuration;

    @Test
    void gettersWork() throws PreCheckException {
        given(storeFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountById(PAYER)).willReturn(account);
        given(account.key()).willReturn(payerKey);
        final var txn = createAccountTransaction();
        final var subject = new PreHandleContextImpl(storeFactory, txn, configuration).requireKey(otherKey);

        assertEquals(txn, subject.body());
        assertEquals(payerKey, subject.payerKey());
        assertEquals(Set.of(otherKey), subject.requiredNonPayerKeys());
        assertEquals(configuration, subject.configuration());
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID = TransactionID.newBuilder()
                .accountID(PAYER)
                .transactionValidStart(Timestamp.newBuilder().seconds(123_456L).build());
        final var createTxnBody = CryptoCreateTransactionBody.newBuilder()
                .key(otherKey)
                .receiverSigRequired(true)
                .memo("Create Account")
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoCreateAccount(createTxnBody)
                .build();
    }
}
