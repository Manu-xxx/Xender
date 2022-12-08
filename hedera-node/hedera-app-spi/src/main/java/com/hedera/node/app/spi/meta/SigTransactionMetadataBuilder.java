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
package com.hedera.node.app.spi.meta;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link SigTransactionMetadata} by collecting needed information collected when transactions are
 * handled as part of "pre-handle" needed for signature verification. This class will be subclassed.
 *
 */
public class SigTransactionMetadataBuilder<T extends SigTransactionMetadataBuilder<T>> {
    protected List<HederaKey> requiredKeys = new ArrayList<>();
    protected ResponseCodeEnum status = OK;

    protected TransactionBody txn;
    protected final AccountKeyLookup keyLookup;
    protected AccountID payer;

    public SigTransactionMetadataBuilder(final AccountKeyLookup keyLookup) {
        this.keyLookup = keyLookup;
    }

    /**
     * Sets status on {@link TransactionMetadata}. It will be {@link ResponseCodeEnum#OK} if there
     * is no failure.
     *
     * @param status status to be set on {@link TransactionMetadata}
     * @return builder object
     */
    public T status(final ResponseCodeEnum status) {
        this.status = status;
        return self();
    }

    public T payer(final AccountID status) {
        this.payer = status;
        return self();
    }

    public T addAllReqKeys(final List<HederaKey> keys) {
        this.requiredKeys.addAll(keys);
        return self();
    }

    /**
     * Fetches the payer key and add to required keys in {@link TransactionMetadata}.
     *
     * @param payer payer for the transaction
     * @return builder object
     */
    public T payerKeyFor(AccountID payer) {
        this.payer = payer;
        addPayerKey();
        return self();
    }

    /**
     * Adds given key to required keys in {@link TransactionMetadata}.
     *
     * @param key key to be added
     * @return builder object
     */
    public T addToReqKeys(HederaKey key) {
        requiredKeys.add(key);
        return self();
    }

    /**
     * Adds the {@link TransactionBody} of the transaction on {@link TransactionMetadata}.
     *
     * @param txn transaction body of the transaction
     * @return builder object
     */
    public T txnBody(TransactionBody txn) {
        this.txn = txn;
        return self();
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the default failureReason given in the result.
     *
     * @param id given accountId
     */
    public T addNonPayerKey(final AccountID id) {
        return addNonPayerKey(id, null);
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the given failure reason on the metadata. If the
     * failureReason is null, sets the default failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatus given failure status
     */
    public T addNonPayerKey(
            final AccountID id, final ResponseCodeEnum failureStatus) {
        if (isNotNeeded(id)) {
            return self();
        }
        final var result = keyLookup.getKey(id);
        failOrAddToKeys(result, failureStatus);
        return self();
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account if receiverSigRequired is true on the account. If the lookup fails, sets the
     * given failure reason on the metadata. If the failureReason is null, sets the default
     * failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatus given failure status
     */
    public T addNonPayerKeyIfReceiverSigRequired(
            final AccountID id, final ResponseCodeEnum failureStatus) {
        if (isNotNeeded(id)) {
            return self();
        }
        final var result = keyLookup.getKeyIfReceiverSigRequired(id);
        failOrAddToKeys(result, failureStatus);
        return self();
    }
    public SigTransactionMetadata build() {
        return new SigTransactionMetadata(txn, payer, status, requiredKeys);
    }

    @SuppressWarnings("unchecked")
    final T self() {
        return (T) this;
    }

    /* ---------- Helper methods ---------- */

    /**
     * Look up the keys for payer account and add payer key to the required keys list. If the lookup
     * fails adds failure status {@code INVALID_PAYER_ACCOUNT_ID} to the metadata.
     */
    private void addPayerKey() {
        final var result = keyLookup.getKey(payer);
        failOrAddToKeys(result, INVALID_PAYER_ACCOUNT_ID);
    }

    /**
     * Checks if the account given is same as payer or if the metadata is already failed. In either
     * case, no need to lookup tha account's key.
     *
     * @param id given account
     * @return true if the lookup is not needed, false otherwise
     */
    private boolean isNotNeeded(@NonNull final AccountID id) {
        return id.equals(payer)
                || id.equals(AccountID.getDefaultInstance())
                || designatesAccountRemoval(id)
                || status != OK;
    }

    /**
     * Checks if the accountId is a sentinel id 0.0.0
     *
     * @param id given accountId
     * @return true if the given accountID is
     */
    private boolean designatesAccountRemoval(@NonNull final AccountID id) {
        return id.getShardNum() == 0
                && id.getRealmNum() == 0
                && id.getAccountNum() == 0
                && id.getAlias().isEmpty();
    }

    /**
     * Given a successful key lookup, adds its key to the required signers. Given a failed key
     * lookup, sets this {@link SigTransactionMetadata}'s status to either the failure reason of the
     * lookup; or (if it is non-null), the requested failureStatus parameter.
     *
     * @param result key lookup result
     * @param failureStatus failure reason for the lookup
     */
    private void failOrAddToKeys(
            final KeyOrLookupFailureReason result, @Nullable final ResponseCodeEnum failureStatus) {
        if (result.failed()) {
            this.status = failureStatus != null ? failureStatus : result.failureReason();
        } else if (result.key() != null) {
            requiredKeys.add(result.key());
        }
    }
}
