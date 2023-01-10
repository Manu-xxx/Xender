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
package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#TokenCreate}.
 */
public class TokenCreateHandler implements TransactionHandler {

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Typically, this method validates the {@link TransactionBody} semantically, gathers all
     * required keys, warms the cache, and creates the {@link TransactionMetadata} that is used in
     * the handle stage.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param txBody the {@link TransactionBody} with the transaction data
     * @param payer the {@link AccountID} of the payer
     * @param accountStore the {@link AccountKeyLookup} to use to resolve keys
     * @return the {@link TransactionMetadata} with all information that needs to be passed to
     *     {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public TransactionMetadata preHandle(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID payer,
            @NonNull final AccountKeyLookup accountStore) {
        final var tokenCreateTxnBody = txBody.getTokenCreation();
        final var customFees = tokenCreateTxnBody.getCustomFeesList();
        final var treasuryId = tokenCreateTxnBody.getTreasury();
        final var autoRenewalAccountId = tokenCreateTxnBody.getAutoRenewAccount();
        final var hasSigRecKey = accountStore.getKeyIfReceiverSigRequired(payer);

        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txBody);

        meta.addNonPayerKey(treasuryId, INVALID_TREASURY_ACCOUNT_FOR_TOKEN);

        if (tokenCreateTxnBody.hasAutoRenewAccount()) {
            meta.addNonPayerKey(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        }
        if (tokenCreateTxnBody.hasAdminKey()) {
            final var adminKey = asHederaKey(tokenCreateTxnBody.getAdminKey());
            adminKey.ifPresent(meta::addToReqNonPayerKeys);
        }

        addCustomFeeKey(meta, customFees, hasSigRecKey);
        return meta.build();
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /* --------------- Helper methods --------------- */

    /**
     * Validates the collector key from the custom fees and signs the metadata.
     *
     * @param meta given transaction metadata
     * @param customFeesList list with the custom fees
     */
    private void addCustomFeeKey(
            SigTransactionMetadataBuilder meta,
            final List<CustomFee> customFeesList,
            final KeyOrLookupFailureReason hasSigRequired) {
        final var failureStatus = INVALID_CUSTOM_FEE_COLLECTOR;
        for (final var customFee : customFeesList) {
            final var collector = customFee.getFeeCollectorAccountId();
            /* A fractional fee collector and a collector for a fixed fee denominated
            in the units of the newly created token both must always sign a TokenCreate,
            since these are automatically associated to the newly created token. */
            if (customFee.hasFixedFee()) {
                final var fixedFee = customFee.getFixedFee();
                final var alwaysAdd =
                        fixedFee.hasDenominatingTokenId()
                                && fixedFee.getDenominatingTokenId().getTokenNum() == 0L;
                if (alwaysAdd || hasSigRequired == KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED) {
                    meta.addNonPayerKey(collector, failureStatus);
                }
            } else if (customFee.hasFractionalFee()) {
                meta.addNonPayerKey(collector, failureStatus);
            } else {
                final var royaltyFee = customFee.getRoyaltyFee();
                var alwaysAdd = false;
                if (royaltyFee.hasFallbackFee()) {
                    final var fFee = royaltyFee.getFallbackFee();
                    alwaysAdd =
                            fFee.hasDenominatingTokenId()
                                    && fFee.getDenominatingTokenId().getTokenNum() == 0;
                }
                if (alwaysAdd || hasSigRequired == KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED) {
                    meta.addNonPayerKey(collector, failureStatus);
                }
            }
        }
    }
}
