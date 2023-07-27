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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.AccountAmount.*;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.ACCOUNT_AMOUNT_COMPARATOR;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.NFT_TRANSFER_COMPARATOR;
import static com.hedera.node.app.service.token.impl.comparator.TokenComparators.TOKEN_TRANSFER_LIST_COMPARATOR;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHelper.asAccountAmounts;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.node.app.service.token.impl.RecordFinalizer;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.handlers.staking.StakingRewardsHandler;
import com.hedera.node.app.service.token.impl.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.config.data.StakingConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This is a special handler that is used to "finalize" hbar and token transfers for the parent transaction record.
 * Finalization in this context means summing the net changes to make to each account's hbar balance and token
 * balances, and assigning the final owner of an nft after an arbitrary number of ownership changes.
 * Based on issue https://github.com/hashgraph/hedera-services/issues/7084 the modularized
 * transaction record for NFT transfer chain A -> B -> C, will look different from mono-service record.
 * This is because mono-service will record both ownership changes from A -> b and then B-> C.
 * Parent record will record any staking rewards paid out due to transaction changes to state.
 * It will deduct any transfer changes that are listed in child transaction records in the parent record.
 *
 * In this finalizer, we will:
 * 1.If staking is enabled, iterate through all modifications in writableAccountStore and compare with the corresponding entity in readableAccountStore
 * 2. Comparing the changes, we look for balance/declineReward/stakedToMe/stakedId fields have been modified,
 * if an account is staking to a node. Construct a list of possibleRewardReceivers
 * 3. Pay staking rewards to any account who has pending rewards
 * 4. Now again, iterate through all modifications in writableAccountStore, writableTokenRelationStore.
 * 5. For each modification we look at the same entity in the respective readableStore
 * 6. Calculate the difference between the two, and then construct a TransferList and TokenTransferList
 * for the parent record (excluding changes from child transaction records)
 */
@Singleton
public class FinalizeParentRecordHandler extends RecordFinalizer implements ParentRecordFinalizer {
    private final StakingRewardsHandler stakingRewardsHandler;

    @Inject
    public FinalizeParentRecordHandler(@NonNull final StakingRewardsHandler stakingRewardsHandler) {
        this.stakingRewardsHandler = stakingRewardsHandler;
    }

    @Override
    public void finalizeParentRecord (@NonNull final HandleContext context,
                                      @NonNull List<SingleTransactionRecordBuilder> childRecords) {
        final var recordBuilder = context.recordBuilder(CryptoTransferRecordBuilder.class);

        // This handler won't ask the context for its transaction, but instead will determine the net hbar transfers and
        // token transfers based on the original value from writable state, and based on changes made during this
        // transaction via
        // any relevant writable stores
        final var writableAccountStore = context.writableStore(WritableAccountStore.class);
        final var writableTokenRelStore = context.writableStore(WritableTokenRelationStore.class);
        final var writableNftStore = context.writableStore(WritableNftStore.class);
        final var stakingConfig = context.configuration().getConfigData(StakingConfig.class);

        if (stakingConfig.isEnabled()) {
            // staking rewards are triggered for any balance changes to account's that are staked to
            // a node. They are also triggered if staking related fields are modified
            // Calculate staking rewards and add them also to hbarChanges here, before assessing
            // net changes for transaction record
            final var rewardsPaid = stakingRewardsHandler.applyStakingRewards(context);
            if (!rewardsPaid.isEmpty()) {
                recordBuilder.paidStakingRewards(asAccountAmounts(rewardsPaid));
            }
        }

        /* ------------------------- Hbar changes from transaction including staking rewards ------------------------- */
        final var hbarChanges = hbarChangesFrom(writableAccountStore);
        if (!hbarChanges.isEmpty()) {
            // Save the modified hbar amounts so records can be written
            recordBuilder.transferList(
                    TransferList.newBuilder().accountAmounts(hbarChanges).build());
        }

        // Declare the top-level token transfer list, which list will include BOTH fungible and non-fungible token
        // transfers
        final ArrayList<TokenTransferList> tokenTransferLists;

        // ---------- fungible token transfers
        final var fungibleChanges = fungibleChangesFrom(writableTokenRelStore);
        final var fungibleTokenTransferLists = asTokenTransferListFrom(fungibleChanges);
        tokenTransferLists = new ArrayList<>(fungibleTokenTransferLists);

        // ---------- nft transfers
        final var nftTokenTransferLists = nftChangesFrom(writableNftStore);
        tokenTransferLists.addAll(nftTokenTransferLists);

        // Record the modified fungible and non-fungible changes so records can be written
        if (!tokenTransferLists.isEmpty()) {
            tokenTransferLists.sort(TOKEN_TRANSFER_LIST_COMPARATOR);
            recordBuilder.tokenTransferLists(tokenTransferLists);
        }
    }
}
