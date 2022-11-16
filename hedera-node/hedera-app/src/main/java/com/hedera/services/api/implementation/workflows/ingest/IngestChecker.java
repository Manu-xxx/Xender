/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.api.implementation.workflows.ingest;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.services.store.models.Account;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;

/**
 * Encapsulates the workflow related to transaction ingestion. Given a Transaction, parses,
 * validates, and submits to the platform.
 */
public interface IngestChecker {

    /**
     * Validates a {@link Transaction}
     *
     * @param tx the {@code Transaction}
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if {@code tx} is {@code null}
     */
    void checkTransaction(Transaction tx) throws PreCheckException;

    /**
     * Validates a {@link SignedTransaction}
     *
     * @param tx the {@code SignedTransaction} to check
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if {@code tx} is {@code null}
     */
    void checkSignedTransaction(SignedTransaction tx) throws PreCheckException;

    /**
     * Validates a {@link TransactionBody} with the paying {@link Account}
     *
     * @param txBody the {@code TransactionBody} to check
     * @param account the paying {@code Account}
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    void checkTransactionBody(TransactionBody txBody, Account account) throws PreCheckException;

    /**
     * Validates a signature.
     *
     * @param platformTx the signed bytes to check
     * @param signatureMap the {@link SignatureMap} with all signatures
     * @param reqKeys a list of required {@link HederaKey}s
     * @throws PreCheckException if validation fails
     * @throws NullPointerException if any of the parameters is {@code null}
     */
    void verifySignatures(
            com.swirlds.common.system.transaction.Transaction platformTx,
            SignatureMap signatureMap,
            List<? extends HederaKey> reqKeys)
            throws PreCheckException;
}
