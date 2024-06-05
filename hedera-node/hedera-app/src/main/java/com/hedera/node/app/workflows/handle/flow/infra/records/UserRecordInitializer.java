/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.infra.records;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UserRecordInitializer {
    private final ExchangeRateManager exchangeRateManager;

    @Inject
    public UserRecordInitializer(final ExchangeRateManager exchangeRateManager) {
        this.exchangeRateManager = exchangeRateManager;
    }

    public void initializeUserRecord(SingleTransactionRecordBuilderImpl recordBuilder, TransactionInfo txnInfo) {
        final Bytes transactionBytes;
        final var transaction = txnInfo.transaction();
        if (transaction.signedTransactionBytes().length() > 0) {
            transactionBytes = transaction.signedTransactionBytes();
        } else {
            // in this case, recorder hash the transaction itself, not its bodyBytes.
            transactionBytes = Transaction.PROTOBUF.toBytes(transaction);
        }
        // Initialize record builder list
        recordBuilder
                .transaction(txnInfo.transaction())
                .transactionBytes(transactionBytes)
                .transactionID(txnInfo.transactionID())
                .exchangeRate(exchangeRateManager.exchangeRates())
                .memo(txnInfo.txBody().memo());
    }
}
