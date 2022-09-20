/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.CRYPTO_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_POSITIVE_BALANCES;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_TREASURY_TITLES;
import static com.hedera.services.ledger.properties.AccountProperty.USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.NftProperty.SPENDER;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
public class TransferLogic {
    public static final List<AccountProperty> TOKEN_TRANSFER_SIDE_EFFECTS =
            List.of(
                    NUM_POSITIVE_BALANCES,
                    NUM_ASSOCIATIONS,
                    NUM_NFTS_OWNED,
                    USED_AUTOMATIC_ASSOCIATIONS,
                    NUM_TREASURY_TITLES);

    private final TokenStore tokenStore;
    private final AutoCreationLogic autoCreationLogic;
    private final SideEffectsTracker sideEffectsTracker;
    private final RecordsHistorian recordsHistorian;
    private final GlobalDynamicProperties dynamicProperties;
    private final MerkleAccountScopedCheck scopedCheck;
    private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    private final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger;
    private final TransactionalLedger<
                    Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
            tokenRelsLedger;
    private final TransactionContext txnCtx;
    private final Supplier<StateView> currentView;

    @Inject
    public TransferLogic(
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger,
            final TransactionalLedger<
                            Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
                    tokenRelsLedger,
            final TokenStore tokenStore,
            final SideEffectsTracker sideEffectsTracker,
            final GlobalDynamicProperties dynamicProperties,
            final OptionValidator validator,
            final @Nullable AutoCreationLogic autoCreationLogic,
            final RecordsHistorian recordsHistorian,
            final TransactionContext txnCtx,
            final Supplier<StateView> currentView) {
        this.tokenStore = tokenStore;
        this.nftsLedger = nftsLedger;
        this.accountsLedger = accountsLedger;
        this.tokenRelsLedger = tokenRelsLedger;
        this.recordsHistorian = recordsHistorian;
        this.autoCreationLogic = autoCreationLogic;
        this.dynamicProperties = dynamicProperties;
        this.sideEffectsTracker = sideEffectsTracker;
        this.txnCtx = txnCtx;
        this.currentView = currentView;

        scopedCheck = new MerkleAccountScopedCheck(validator, nftsLedger);
    }

    public void doZeroSum(final List<BalanceChange> changes) {
        var validity = OK;
        var autoCreationFee = 0L;
        final var tokenAliasMap = countTokenAutoCreations(changes);
        for (var change : changes) {
            // If the change consists of any known alias, replace the alias with the account number
            checkIfExistingAlias(change);
            // create a new account for alias when the no account is already created using the alias
            if (change.hasNonEmptyAlias()
                    || (change.isForNft() && change.hasNonEmptyCounterPartyAlias())) {
                if (autoCreationLogic == null) {
                    throw new IllegalStateException(
                            "Cannot auto-create account from "
                                    + change
                                    + " with null autoCreationLogic");
                }
                final var result = autoCreationLogic.create(change, accountsLedger, tokenAliasMap);
                validity = result.getKey();
                autoCreationFee += result.getValue();
                if (validity == OK && (change.isForToken())) {
                    validity = tokenStore.tryTokenChange(change);
                }
            } else if (change.isForHbar()) {
                validity =
                        accountsLedger.validate(
                                change.accountId(), scopedCheck.setBalanceChange(change));
            } else {
                validity =
                        accountsLedger.validate(
                                change.accountId(), scopedCheck.setBalanceChange(change));

                if (validity == OK) {
                    validity = tokenStore.tryTokenChange(change);
                }
            }
            if (validity != OK) {
                break;
            }
        }

        if (validity == OK) {
            adjustBalancesAndAllowances(changes);
            if (autoCreationFee > 0) {
                payAutoCreationFee(autoCreationFee);
                autoCreationLogic.submitRecordsTo(recordsHistorian);
            }
        } else {
            dropTokenChanges(sideEffectsTracker, nftsLedger, accountsLedger, tokenRelsLedger);
            if (autoCreationLogic != null && autoCreationLogic.reclaimPendingAliases()) {
                accountsLedger.undoCreations();
            }
            throw new InvalidTransactionException(validity);
        }
    }

    private HashMap<ByteString, HashSet<Id>> countTokenAutoCreations(
            final List<BalanceChange> changes) {
        final var map = new HashMap<ByteString, HashSet<Id>>();
        for (final var change : changes) {
            if ((change.isForNft() && change.hasNonEmptyCounterPartyAlias())) {
                final var alias = change.counterPartyAlias();
                if (map.containsKey(alias)) {
                    map.get(alias).add(change.getToken());
                } else {
                    map.put(alias, new HashSet<>(Arrays.asList(change.getToken())));
                }
                //                map.merge(change.counterPartyAccountId().getAlias(),
                // change.tokenId(), (k, v) -> map.get(k).add(v));
            } else if (change.isForFungibleToken() && change.hasNonEmptyAlias()) {
                final var alias = change.alias();
                if (map.containsKey(alias)) {
                    map.get(alias).add(change.getToken());
                } else {
                    map.put(alias, new HashSet<>(Arrays.asList(change.getToken())));
                }
                //                map.merge(change.alias(), change.tokenId(), (a, b) -> map.put(a,
                // b));
            }
        }
        return map;
    }

    private void payAutoCreationFee(final long autoCreationFee) {
        final var funding = dynamicProperties.fundingAccount();
        final var fundingBalance = (long) accountsLedger.get(funding, BALANCE);
        final var newFundingBalance = fundingBalance + autoCreationFee;
        accountsLedger.set(funding, BALANCE, newFundingBalance);

        // deduct the auto creation fee from payer of the transaction
        final var payerBalance = (long) accountsLedger.get(txnCtx.activePayer(), BALANCE);
        accountsLedger.set(txnCtx.activePayer(), BALANCE, payerBalance - autoCreationFee);
        txnCtx.addFeeChargedToPayer(autoCreationFee);
    }

    private void adjustBalancesAndAllowances(final List<BalanceChange> changes) {
        for (var change : changes) {
            final var accountId = change.accountId();
            if (change.isForHbar()) {
                final var newBalance = change.getNewBalance();
                accountsLedger.set(accountId, BALANCE, newBalance);
                if (change.isApprovedAllowance()) {
                    adjustCryptoAllowance(change, accountId);
                }
            } else if (change.isApprovedAllowance() && change.isForFungibleToken()) {
                adjustFungibleTokenAllowance(change, accountId);
            } else if (change.isForNft()) {
                // wipe the allowance on this uniqueToken
                nftsLedger.set(change.nftId(), SPENDER, MISSING_ENTITY_ID);
            }
        }
    }

    public static void dropTokenChanges(
            final SideEffectsTracker sideEffectsTracker,
            final TransactionalLedger<NftId, NftProperty, UniqueTokenAdapter> nftsLedger,
            final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger,
            final TransactionalLedger<
                            Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus>
                    tokenRelsLedger) {
        if (tokenRelsLedger.isInTransaction()) {
            tokenRelsLedger.rollback();
        }
        if (nftsLedger.isInTransaction()) {
            nftsLedger.rollback();
        }
        accountsLedger.undoChangesOfType(TOKEN_TRANSFER_SIDE_EFFECTS);
        sideEffectsTracker.resetTrackedTokenChanges();
    }

    @SuppressWarnings("unchecked")
    private void adjustCryptoAllowance(BalanceChange change, AccountID ownerID) {
        final var payerNum = EntityNum.fromAccountId(change.getPayerID());
        final var hbarAllowances =
                new TreeMap<>(
                        (Map<EntityNum, Long>) accountsLedger.get(ownerID, CRYPTO_ALLOWANCES));
        final var currentAllowance = hbarAllowances.get(payerNum);
        final var newAllowance = currentAllowance + change.getAllowanceUnits();
        if (newAllowance != 0) {
            hbarAllowances.put(payerNum, newAllowance);
        } else {
            hbarAllowances.remove(payerNum);
        }
        accountsLedger.set(ownerID, CRYPTO_ALLOWANCES, hbarAllowances);
    }

    @SuppressWarnings("unchecked")
    private void adjustFungibleTokenAllowance(final BalanceChange change, final AccountID ownerID) {
        final var allowanceId =
                FcTokenAllowanceId.from(
                        change.getToken().asEntityNum(),
                        EntityNum.fromAccountId(change.getPayerID()));
        final var fungibleAllowances =
                new TreeMap<>(
                        (Map<FcTokenAllowanceId, Long>)
                                accountsLedger.get(ownerID, FUNGIBLE_TOKEN_ALLOWANCES));
        final var currentAllowance = fungibleAllowances.get(allowanceId);
        final var newAllowance = currentAllowance + change.getAllowanceUnits();
        if (newAllowance == 0) {
            fungibleAllowances.remove(allowanceId);
        } else {
            fungibleAllowances.put(allowanceId, newAllowance);
        }
        accountsLedger.set(ownerID, FUNGIBLE_TOKEN_ALLOWANCES, fungibleAllowances);
    }

    public void checkIfExistingAlias(final BalanceChange change) {
        if (change.hasNonEmptyAlias() && isKnownAlias(change.accountId(), currentView)) {
            final var aliasNum =
                    currentView
                            .get()
                            .aliases()
                            .get(change.accountId().getAlias())
                            .toGrpcAccountId();
            change.replaceAliasWith(aliasNum);
        }
        if (change.hasNonEmptyCounterPartyAlias()
                && isKnownAlias(change.counterPartyAccountId(), currentView)) {
            final var aliasNum =
                    currentView
                            .get()
                            .aliases()
                            .get(change.counterPartyAccountId().getAlias())
                            .toGrpcAccountId();
            change.replaceCounterPartyAliasWith(aliasNum);
        }
    }

    private boolean isKnownAlias(final AccountID accountId, final Supplier<StateView> workingView) {
        final var aliases = workingView.get().aliases();
        return aliases.containsKey(accountId.getAlias());
    }
}
