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
package com.hedera.node.app.service.mono.token.impl;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.TransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.NotImplementedException;

/**
 * A {@code TokenPreTransactionHandler} implementation that pre-computes the required signing keys
 * (but not the candidate signatures) for each token operation.
 */
public final class TokenPreTransactionHandlerImpl implements TokenPreTransactionHandler {

    private final AccountStore accountStore;
    private final PreHandleContext preHandleContext;

    public TokenPreTransactionHandlerImpl(
            @NonNull final AccountStore accountStore, PreHandleContext preHandleContext) {
        this.accountStore = Objects.requireNonNull(accountStore);

        this.preHandleContext = preHandleContext;
    }

    @Override
    public TransactionMetadata preHandleCreateToken(TransactionBody txn, AccountID payer) {
        return buildSigTransactionMetadata(txn, payer);
    }

    @Override
    public TransactionMetadata preHandleUpdateToken(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleMintToken(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleBurnToken(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleDeleteToken(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleWipeTokenAccount(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleFreezeTokenAccount(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleUnfreezeTokenAccount(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleGrantKycToTokenAccount(
            TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleRevokeKycFromTokenAccount(
            TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleAssociateTokens(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleDissociateTokens(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleUpdateTokenFeeSchedule(
            TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandlePauseToken(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public TransactionMetadata preHandleUnpauseToken(TransactionBody txn, AccountID payer) {
        throw new NotImplementedException();
    }

    /**
     * Returns metadata for {@code TokenCreate} transaction needed to validate signatures needed for
     * signing the transaction
     *
     * @param txn given transaction body
     * @param payer payer for the transaction
     * @return transaction's metadata needed to validate signatures
     */
    private TransactionMetadata buildSigTransactionMetadata(
            final TransactionBody txn, final AccountID payer) {
        final var tokenCreateTxnBody = txn.getTokenCreation();
        final var customFees = tokenCreateTxnBody.getCustomFeesList();
        final var treasuryId = tokenCreateTxnBody.getTreasury();
        final var autoRenewalAccountId = tokenCreateTxnBody.getAutoRenewAccount();
        final var meta =
                new SigTransactionMetadataBuilder(accountStore).payerKeyFor(payer).txnBody(txn);
        meta.addNonPayerKey(treasuryId, ACCOUNT_ID_DOES_NOT_EXIST);
        meta.addNonPayerKey(autoRenewalAccountId, INVALID_AUTORENEW_ACCOUNT);
        if (tokenCreateTxnBody.hasAdminKey()) {
            final var adminKey = asHederaKey(tokenCreateTxnBody.getAdminKey());
            adminKey.ifPresent(meta::addToReqNonPayerKeys);
        }
        addCustomFeeKey(meta, customFees);
        return meta.build();
    }

    private void addCustomFeeKey(
            SigTransactionMetadataBuilder meta, final List<CustomFee> customFeesList) {
        final var failureStatus = INVALID_FEE_COLLECTOR_ACCOUNT_ID;
        for (final var customFee : customFeesList) {
            final var hasCollector = customFee.hasFeeCollectorAccountId();
            if (hasCollector) {
                final var collector = customFee.getFeeCollectorAccountId();
                /* A fractional fee collector and a collector for a fixed fee denominated
                in the units of the newly created token both must always sign a TokenCreate,
                since these are automatically associated to the newly created token. */
                final boolean alwaysAdd;
                if (customFee.hasFixedFee()) {
                    final var fixedFee = customFee.getFixedFee();
                    alwaysAdd =
                            fixedFee.hasDenominatingTokenId()
                                    && fixedFee.getDenominatingTokenId().getTokenNum() == 0L;
                    if (alwaysAdd) {
                        meta.addNonPayerKey(collector, failureStatus);
                    } else {
                        meta.addNonPayerKeyIfReceiverSigRequired(collector, RECEIVER_SIG_REQUIRED);
                    }
                } else if (customFee.hasFractionalFee()) {
                    meta.addNonPayerKey(collector, failureStatus);
                } else if (customFee.hasRoyaltyFee()) {
                    final var royaltyFee = customFee.getRoyaltyFee();
                    if (royaltyFee.hasFallbackFee()) {
                        final var fFee = royaltyFee.getFallbackFee();
                        alwaysAdd =
                                fFee.hasDenominatingTokenId()
                                        && fFee.getDenominatingTokenId().getTokenNum() == 0;
                        if (alwaysAdd) {
                            meta.addNonPayerKey(collector, failureStatus);
                        }
                    } else {
                        meta.addNonPayerKeyIfReceiverSigRequired(collector, RECEIVER_SIG_REQUIRED);
                    }
                } else {
                    meta.addNonPayerKey(collector, INVALID_CUSTOM_FEE_COLLECTOR);
                }
            }
        }
    }
}
