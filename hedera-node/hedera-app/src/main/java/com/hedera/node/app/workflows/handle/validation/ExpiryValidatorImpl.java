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

package com.hedera.node.app.workflows.handle.validation;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.AutoRenewConfig;
import com.hedera.node.config.data.HederaConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of {@link ExpiryValidator}.
 *
 * <p>The implementation is incomplete, and is a placeholder for future work.
 * GitHub Issue <a href="https://github.com/hashgraph/hedera-services/issues/6701">(#6701)</a>
 */
public class ExpiryValidatorImpl implements ExpiryValidator {

    private final HandleContext context;

    public ExpiryValidatorImpl(@NonNull final HandleContext context) {
        this.context = requireNonNull(context, "context must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExpiryMeta resolveCreationAttempt(
            final boolean entityCanSelfFundRenewal,
            @NonNull final ExpiryMeta creationMeta) {
        if (creationMeta.hasAutoRenewNum()) {
            validateAutoRenewAccount(
                    creationMeta.autoRenewShard(), creationMeta.autoRenewRealm(), creationMeta.autoRenewNum());
        }

        long effectiveExpiry = creationMeta.expiry();
        // We prioritize the expiry implied by auto-renew configuration, if it is present
        // and complete (meaning either both auto-renew period and auto-renew account are
        // present; or auto-renew period is present, and the entity can self-fund)
        if (hasCompleteAutoRenewSpec(entityCanSelfFundRenewal, creationMeta)) {
            effectiveExpiry = context.consensusNow().getEpochSecond() + creationMeta.autoRenewPeriod();
        }
        context.attributeValidator().validateExpiry(effectiveExpiry);

        // Even if the effective expiry is valid, we still also require any explicit auto-renew period to be valid
        if (creationMeta.hasAutoRenewPeriod()) {
            context.attributeValidator().validateAutoRenewPeriod(creationMeta.autoRenewPeriod());
        }
        return new ExpiryMeta(effectiveExpiry, creationMeta.autoRenewPeriod(), creationMeta.autoRenewNum());
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ExpiryMeta resolveUpdateAttempt(
            @NonNull final ExpiryMeta currentMeta,
            @NonNull final ExpiryMeta updateMeta) {
        if (updateMeta.hasAutoRenewNum()) {
            validateAutoRenewAccount(
                    updateMeta.autoRenewShard(), updateMeta.autoRenewRealm(), updateMeta.autoRenewNum());
        }

        var resolvedExpiry = currentMeta.expiry();
        if (updateMeta.hasExplicitExpiry()) {
            validateFalse(updateMeta.expiry() < currentMeta.expiry(), EXPIRATION_REDUCTION_NOT_ALLOWED);
            context.attributeValidator().validateExpiry(updateMeta.expiry());
            resolvedExpiry = updateMeta.expiry();
        }

        var resolvedAutoRenewPeriod = currentMeta.autoRenewPeriod();
        if (updateMeta.hasAutoRenewPeriod()) {
            context.attributeValidator().validateAutoRenewPeriod(updateMeta.autoRenewPeriod());
            resolvedAutoRenewPeriod = updateMeta.autoRenewPeriod();
        }

        var resolvedAutoRenewNum = currentMeta.autoRenewNum();
        if (updateMeta.hasAutoRenewNum()) {
            // If just now adding an auto-renew account, confirm the resolved auto-renew period is valid
            if (!currentMeta.hasAutoRenewNum()) {
                context.attributeValidator().validateAutoRenewPeriod(resolvedAutoRenewPeriod);
            }
            resolvedAutoRenewNum = updateMeta.autoRenewNum();
        }
        return new ExpiryMeta(resolvedExpiry, resolvedAutoRenewPeriod, resolvedAutoRenewNum);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public ResponseCodeEnum expirationStatus(
            @NonNull final EntityType entityType,
            final boolean isMarkedExpired,
            final long balanceAvailableForSelfRenewal) {
        final var isSmartContract = entityType.equals(EntityType.CONTRACT);
        final var autoRenewConfig = context.configuration().getConfigData(AutoRenewConfig.class);
        if (!autoRenewConfig.isAutoRenewEnabled()
                || balanceAvailableForSelfRenewal > 0
                || !isMarkedExpired
                || isExpiryDisabled(
                isSmartContract, autoRenewConfig.expireAccounts(), autoRenewConfig.expireContracts())) {
            return OK;
        }

        return isSmartContract ? CONTRACT_EXPIRED_AND_PENDING_REMOVAL : ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
    }

    /**
     * Helper to check if an entity with the given metadata has a completely specified
     * auto-renew configuration. This is true if either the {@link ExpiryMeta} includes
     * both an auto-renew period and an auto-renew account; or if the {@link ExpiryMeta}
     * includes only an auto-renew period, and the entity can self-fund its auto-renewal.
     *
     * @param entityCanSelfFundRenewal whether the entity can self-fund its auto-renewal
     * @param creationMetadata the entity's proposed {@link ExpiryMeta}
     * @return whether the entity has a complete auto-renew configuration
     */
    private boolean hasCompleteAutoRenewSpec(
            final boolean entityCanSelfFundRenewal, final ExpiryMeta creationMetadata) {
        return creationMetadata.hasFullAutoRenewSpec()
                || (!creationMetadata.hasExplicitExpiry() && entityCanSelfFundRenewal);
    }

    /**
     * Helper to validate that the given account number is a valid auto-renew account.
     *
     * @param shard the account shard to validate
     * @param realm the account realm to validate
     * @param num the account number to validate
     * @throws HandleException if the account number is invalid
     */
    private void validateAutoRenewAccount(final long shard, final long realm, final long num) {
        final var hederaConfig = context.configuration().getConfigData(HederaConfig.class);
        validateTrue(shard == hederaConfig.shard() && realm == hederaConfig.realm(), INVALID_AUTORENEW_ACCOUNT);
        if (num == 0L) {
            // 0L is a sentinel number that says to remove the current auto-renew account
            return;
        }
        final var autoRenewId = AccountID.newBuilder()
                .shardNum(shard)
                .realmNum(realm)
                .accountNum(num).build();
        final var accountStore = context.readableStore(ReadableAccountStore.class);
        try {
            final var account = accountStore.getAccountById(autoRenewId);
            if (account == null) {
                throw new HandleException(INVALID_AUTORENEW_ACCOUNT);
            }
        } catch (final InvalidTransactionException e) {
            throw new HandleException(PbjConverter.toPbj(e.getResponseCode()));
        }
    }

    private boolean isExpiryDisabled(boolean smartContract, boolean expireAccounts, boolean expireContracts) {
        return (smartContract && !expireContracts) || (!smartContract && !expireAccounts);
    }
}
