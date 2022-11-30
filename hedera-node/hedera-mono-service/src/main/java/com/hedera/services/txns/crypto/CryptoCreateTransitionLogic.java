/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto;

import static com.hedera.node.app.hapi.utils.ethereum.EthTxSigs.recoverAddressFromPubKey;
import static com.hedera.services.ledger.accounts.HederaAccountCustomizer.hasStakedId;
import static com.hedera.services.txns.crypto.validators.CryptoCreateChecks.aliasAndEvmAddressProvided;
import static com.hedera.services.txns.crypto.validators.CryptoCreateChecks.keyAndAliasAndEvmAddressProvided;
import static com.hedera.services.txns.crypto.validators.CryptoCreateChecks.keyAndAliasProvided;
import static com.hedera.services.txns.crypto.validators.CryptoCreateChecks.keyAndEvmAddressProvided;
import static com.hedera.services.txns.crypto.validators.CryptoCreateChecks.onlyAliasProvided;
import static com.hedera.services.txns.crypto.validators.CryptoCreateChecks.onlyEvmAddressProvided;
import static com.hedera.services.txns.crypto.validators.CryptoCreateChecks.onlyKeyProvided;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hedera.services.utils.MiscUtils.asPrimitiveKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InsufficientFundsException;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.CryptoCreateChecks;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoCreate transaction, and the conditions
 * under which such logic is syntactically correct. (It is possible that the <i>semantics</i> of the
 * transaction will still be wrong; for example, if the sponsor account can no longer afford to fund
 * the initial balance of the new account.)
 */
@Singleton
public class CryptoCreateTransitionLogic implements TransitionLogic {
    static final int MAX_CHARGEABLE_AUTO_ASSOCIATIONS = 5000;
    private static final Logger log = LogManager.getLogger(CryptoCreateTransitionLogic.class);

    private final UsageLimits usageLimits;
    private final HederaLedger ledger;
    private final SigImpactHistorian sigImpactHistorian;
    private final TransactionContext txnCtx;
    private final GlobalDynamicProperties dynamicProperties;
    private final AliasManager aliasManager;
    private final CryptoCreateChecks cryptoCreateChecks;

    @Inject
    public CryptoCreateTransitionLogic(
            final UsageLimits usageLimits,
            final HederaLedger ledger,
            final SigImpactHistorian sigImpactHistorian,
            final TransactionContext txnCtx,
            final GlobalDynamicProperties dynamicProperties,
            final AliasManager aliasManager,
            final CryptoCreateChecks cryptoCreateChecks) {
        this.ledger = ledger;
        this.txnCtx = txnCtx;
        this.usageLimits = usageLimits;
        this.sigImpactHistorian = sigImpactHistorian;
        this.dynamicProperties = dynamicProperties;
        this.aliasManager = aliasManager;
        this.cryptoCreateChecks = cryptoCreateChecks;
    }

    @Override
    public void doStateTransition() {
        if (!usageLimits.areCreatableAccounts(1)) {
            txnCtx.setStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
            return;
        }
        try {
            TransactionBody cryptoCreateTxn = txnCtx.accessor().getTxn();
            AccountID sponsor = txnCtx.activePayer();

            CryptoCreateTransactionBody op = cryptoCreateTxn.getCryptoCreateAccount();
            long balance = op.getInitialBalance();
            final var customizer = asCustomizer(op);
            final var created = ledger.create(sponsor, balance, customizer);
            sigImpactHistorian.markEntityChanged(created.getAccountNum());

            txnCtx.setCreated(created);
            txnCtx.setStatus(SUCCESS);

            if (!op.getAlias().isEmpty()) {
                aliasManager.link(op.getAlias(), EntityNum.fromAccountId(created));

                final var key = asPrimitiveKeyUnchecked(op.getAlias());
                if (!key.getECDSASecp256K1().isEmpty()) {
                    final var jKey = asFcKeyUnchecked(key);
                    aliasManager.maybeLinkEvmAddress(jKey, EntityNum.fromAccountId(created));
                    txnCtx.setEvmAddress(
                            ByteStringUtils.wrapUnsafely(
                                    Objects.requireNonNull(
                                            recoverAddressFromPubKey(
                                                    key.getECDSASecp256K1().toByteArray()))));
                }
            } else {
                if (op.hasKey()
                        && !op.getKey().getECDSASecp256K1().isEmpty()
                        && dynamicProperties.isCryptoCreateWithAliasEnabled()) {
                    aliasManager.link(
                            (ByteString)
                                    ledger.getAccountsLedger().get(created, AccountProperty.ALIAS),
                            EntityNum.fromAccountId(created));
                    txnCtx.setEvmAddress(
                            (ByteString)
                                    ledger.getAccountsLedger().get(created, AccountProperty.ALIAS));
                }

                if (!op.getEvmAddress().isEmpty()) {
                    aliasManager.link(op.getEvmAddress(), EntityNum.fromAccountId(created));
                }
            }
        } catch (InsufficientFundsException ife) {
            txnCtx.setStatus(INSUFFICIENT_PAYER_BALANCE);
        } catch (Exception e) {
            log.warn("Avoidable exception!", e);
            txnCtx.setStatus(FAIL_INVALID);
        }
    }

    private HederaAccountCustomizer asCustomizer(CryptoCreateTransactionBody op) {
        long autoRenewPeriod = op.getAutoRenewPeriod().getSeconds();
        long consensusTime = txnCtx.consensusTime().getEpochSecond();
        long expiry = consensusTime + autoRenewPeriod;

        var customizer = new HederaAccountCustomizer();
        customizer
                .memo(op.getMemo())
                .expiry(expiry)
                .autoRenewPeriod(autoRenewPeriod)
                .isReceiverSigRequired(op.getReceiverSigRequired())
                .maxAutomaticAssociations(op.getMaxAutomaticTokenAssociations())
                .isDeclinedReward(op.getDeclineReward());

        if (onlyKeyProvided(op)) {
            if (!op.getKey().getECDSASecp256K1().isEmpty()
                    && dynamicProperties.isCryptoCreateWithAliasEnabled()) {

                final var recoveredEvmAddressFromPrimitiveKey =
                        recoverAddressFromPubKey(op.getKey().getECDSASecp256K1().toByteArray());

                if (recoveredEvmAddressFromPrimitiveKey != null) {
                    customizer.alias(ByteString.copyFrom(recoveredEvmAddressFromPrimitiveKey));
                }
            }

            final JKey key = asFcKeyUnchecked(op.getKey());
            customizer.key(key);
        } else if (onlyEvmAddressProvided(op)) {
            customizer.alias(op.getEvmAddress());
        } else if (onlyAliasProvided(op)) {
            final var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());
            final JKey jKeyFromAlias = asFcKeyUnchecked(keyFromAlias);
            customizer.key(jKeyFromAlias).alias(op.getAlias());
        } else if (keyAndAliasProvided(op)) {
            customizer.key(asFcKeyUnchecked(op.getKey())).alias(op.getAlias());
        } else if (keyAndEvmAddressProvided(op)) {
            customizer.key(asFcKeyUnchecked(op.getKey())).alias(op.getEvmAddress());
        } else if (aliasAndEvmAddressProvided(op)) {
            final var keyFromAlias = asPrimitiveKeyUnchecked(op.getAlias());
            final JKey jKeyFromAlias = asFcKeyUnchecked(keyFromAlias);
            customizer.key(jKeyFromAlias).alias(op.getEvmAddress());
        } else if (keyAndAliasAndEvmAddressProvided(op)) {
            customizer.key(asFcKeyUnchecked(op.getKey())).alias(op.getAlias());
        }

        if (hasStakedId(op.getStakedIdCase().name())) {
            customizer.customizeStakedId(
                    op.getStakedIdCase().name(), op.getStakedAccountId(), op.getStakedNodeId());
        }
        return customizer;
    }

    @Override
    public Predicate<TransactionBody> applicability() {
        return TransactionBody::hasCryptoCreateAccount;
    }

    @Override
    public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
        return this::validate;
    }

    public ResponseCodeEnum validate(TransactionBody cryptoCreateTxn) {
        return cryptoCreateChecks.cryptoCreateValidation(cryptoCreateTxn.getCryptoCreateAccount());
    }
}
