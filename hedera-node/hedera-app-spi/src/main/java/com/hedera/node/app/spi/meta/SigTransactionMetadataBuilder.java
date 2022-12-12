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
import java.util.Objects;

/**
 * Builds {@link SigTransactionMetadata} by collecting information that is needed when transactions
 * are handled as part of "pre-handle" needed for signature verification. The first key in the
 * required keys list is the payer's key.
 *
 * <p>NOTE : This class is designed to be subclassed For e.g., we need a {@link TransactionMetadata}
 * with an inner {@link TransactionMetadata} for schedule transactions.
 */
public class SigTransactionMetadataBuilder {
    private final List<HederaKey> requiredKeys = new ArrayList<>();
    private ResponseCodeEnum status = OK;

    private TransactionBody txn;
    private final AccountKeyLookup keyLookup;
    private AccountID payer;

    public SigTransactionMetadataBuilder(@NonNull final AccountKeyLookup keyLookup) {
        this.keyLookup = Objects.requireNonNull(keyLookup);
    }

    /**
     * Sets status on {@link TransactionMetadata}. It will be {@link ResponseCodeEnum#OK} if there
     * is no failure.
     *
     * @param status status to be set on {@link TransactionMetadata}
     * @return builder object
     */
    public SigTransactionMetadataBuilder status(@NonNull final ResponseCodeEnum status) {
        this.status = Objects.requireNonNull(status);
        return this;
    }

    /**
     * Add a keys to required keys list
     *
     * @param keys list of keys to add
     * @return builder object
     */
    public SigTransactionMetadataBuilder addAllReqKeys(@NonNull final List<HederaKey> keys) {
        requiredKeys.addAll(Objects.requireNonNull(keys));
        return this;
    }

    /**
     * Fetches the payer key and add to required keys in {@link TransactionMetadata}.
     *
     * @param payer payer for the transaction
     * @return builder object
     */
    public SigTransactionMetadataBuilder payerKeyFor(@NonNull AccountID payer) {
        this.payer = Objects.requireNonNull(payer);
        addPayerKey();
        return this;
    }

    /**
     * Adds given key to required keys in {@link TransactionMetadata}. If the status is already
     * failed, or if the payer's key is not added the given keys will not be added to requiredKeys
     * list. This method is used when the payer's key is already fetched, and we want to add other
     * keys from {@link TransactionBody} to required keys to sign.
     *
     * @param key key to be added
     * @return builder object
     */
    public SigTransactionMetadataBuilder addToReqKeys(@NonNull HederaKey key) {
        if (status != OK || payerKeyNotAdded()) {
            return this;
        }
        requiredKeys.add(Objects.requireNonNull(key));
        return this;
    }

    /**
     * Adds the {@link TransactionBody} of the transaction on {@link TransactionMetadata}.
     *
     * @param txn transaction body of the transaction
     * @return builder object
     */
    public SigTransactionMetadataBuilder txnBody(@NonNull TransactionBody txn) {
        this.txn = Objects.requireNonNull(txn);
        return this;
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the default failureReason given in the result.
     *
     * @param id given accountId
     */
    public SigTransactionMetadataBuilder addNonPayerKey(@NonNull final AccountID id) {
        return addNonPayerKey(id, null);
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account. If the lookup fails, sets the given failure reason on the metadata. If the
     * failureReason is null, sets the default failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatusToUse failure status to be set if there is failure
     */
    public SigTransactionMetadataBuilder addNonPayerKey(
            @NonNull final AccountID id, @Nullable final ResponseCodeEnum failureStatusToUse) {
        if (isNotNeeded(Objects.requireNonNull(id))) {
            return this;
        }
        final var result = keyLookup.getKey(id);
        addToKeysOrFail(result, failureStatusToUse);
        return this;
    }

    /**
     * Checks if the accountId is same as payer or the status of the metadata is already failed. If
     * either of the above is true, doesn't look up the keys for given account. Else, looks up the
     * keys for account if receiverSigRequired is true on the account. If the lookup fails, sets the
     * given failure reason on the metadata. If the failureReason is null, sets the default
     * failureReason given in the result.
     *
     * @param id given accountId
     * @param failureStatusToUse failure status to be set if there is failure
     */
    public SigTransactionMetadataBuilder addNonPayerKeyIfReceiverSigRequired(
            @NonNull final AccountID id, @Nullable final ResponseCodeEnum failureStatusToUse) {
        if (isNotNeeded(Objects.requireNonNull(id))) {
            return this;
        }
        final var result = keyLookup.getKeyIfReceiverSigRequired(id);
        addToKeysOrFail(result, failureStatusToUse);
        return this;
    }

    /**
     * Creates and returns a new {@link SigTransactionMetadata} based on the values configured in
     * this builder.
     *
     * @return a new {@link SigTransactionMetadata}
     */
    @NonNull
    public SigTransactionMetadata build() {
        Objects.requireNonNull(txn, "Transaction body is required to build SigTransactionMetadata");
        Objects.requireNonNull(payer, "Payer is required to build SigTransactionMetadata");
        return new SigTransactionMetadata(txn, payer, status, requiredKeys);
    }

    /* ---------- Helper methods ---------- */

    /**
     * Look up the keys for payer account and add payer key to the required keys list. If the lookup
     * fails adds failure status {@code INVALID_PAYER_ACCOUNT_ID} to the metadata.
     */
    private void addPayerKey() {
        final var result = keyLookup.getKey(payer);
        addToKeysOrFail(result, INVALID_PAYER_ACCOUNT_ID);
    }

    /**
     * Checks if the account given is same as payer or if the metadata is already failed. In either
     * case, no need to look up that account's key. If the metadata did not fail already and no
     * payer key has been added, we don't add other keys, since the first key in the requiredKeys
     * should be payer's key
     *
     * @param id given account
     * @return true if the lookup is not needed, false otherwise
     */
    private boolean isNotNeeded(@NonNull final AccountID id) {
        return id.equals(payer)
                || id.equals(AccountID.getDefaultInstance())
                || designatesAccountRemoval(id)
                || status != OK
                || payerKeyNotAdded();
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
    private void addToKeysOrFail(
            final KeyOrLookupFailureReason result, @Nullable final ResponseCodeEnum failureStatus) {
        if (result.failed()) {
            this.status = failureStatus != null ? failureStatus : result.failureReason();
        } else if (result.key() != null) {
            requiredKeys.add(result.key());
        }
    }

    /**
     * Checks if the payer key has not been added, and we didn't fail looking it up. No other keys
     * can be added at this time. This is to maintain the invariant that the payer key is the first
     * key in requiredKeys list.
     *
     * @return true if payer key is not added, false otherwise
     */
    private boolean payerKeyNotAdded() {
        return status == OK && requiredKeys.isEmpty();
    }
}
