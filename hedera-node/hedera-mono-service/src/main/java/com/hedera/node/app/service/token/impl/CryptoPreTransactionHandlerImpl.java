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
package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.Utils.asHederaKey;

import com.hedera.node.app.SigTransactionMetadata;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;

/**
 * A {@code CryptoPreTransactionHandler} implementation that pre-computes the required signing keys
 * (but not the candidate signatures) for each crypto operation.
 */
public final class CryptoPreTransactionHandlerImpl implements CryptoPreTransactionHandler {
    private final AccountStore accountStore;

    public CryptoPreTransactionHandlerImpl(@NotNull final AccountStore accountStore) {
        this.accountStore = Objects.requireNonNull(accountStore);
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCryptoCreate(final TransactionBody tx) {
        final var op = tx.getCryptoCreateAccount();
        final var key = asHederaKey(op.getKey());
        final var receiverSigReq = op.getReceiverSigRequired();
        final var payer = tx.getTransactionID().getAccountID();
        return createAccountSigningMetadata(tx, key, receiverSigReq, payer);
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleUpdateAccount(final TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCryptoTransfer(final TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleCryptoDelete(final TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleApproveAllowances(final TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteAllowances(final TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleAddLiveHash(final TransactionBody txn) {
        throw new NotImplementedException();
    }

    @Override
    /** {@inheritDoc} */
    public TransactionMetadata preHandleDeleteLiveHash(final TransactionBody txn) {
        throw new NotImplementedException();
    }

    private TransactionMetadata createAccountSigningMetadata(
            final TransactionBody tx,
            final Optional<HederaKey> key,
            final boolean receiverSigReq,
            final AccountID payer) {
        if (receiverSigReq && key.isPresent()) {
            return new SigTransactionMetadata(accountStore, tx, payer, List.of(key.get()));
        }
        return new SigTransactionMetadata(accountStore, tx, payer);
    }
}
