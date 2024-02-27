/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.bbm;

import static com.hedera.node.app.bbm.associations.TokenAssociationsDumpUtils.dumpMonoTokenRelations;
import static com.hedera.node.app.bbm.files.FilesDumpUtils.dumpMonoFiles;
import static com.hedera.node.app.bbm.nfts.UniqueTokenDumpUtils.dumpMonoUniqueTokens;
import static com.hedera.node.app.bbm.singleton.BlockInfoDumpUtils.dumpModBlockInfo;
import static com.hedera.node.app.bbm.singleton.BlockInfoDumpUtils.dumpMonoBlockInfo;
import static com.hedera.node.app.bbm.singleton.CongestionDumpUtils.dumpMonoCongestion;
import static com.hedera.node.app.bbm.singleton.StakingInfoDumpUtils.dumpMonoStakingInfo;
import static com.hedera.node.app.bbm.singleton.StakingRewardsDumpUtils.dumpMonoStakingRewards;
import static com.hedera.node.app.bbm.singleton.TxnRecordQueueDumpUtils.dumpMonoPayerRecords;
import static com.hedera.node.app.ids.EntityIdService.ENTITY_ID_STATE_KEY;
import static com.hedera.node.app.records.BlockRecordService.BLOCK_INFO_STATE_KEY;
import static com.hedera.node.app.records.BlockRecordService.RUNNING_HASHES_STATE_KEY;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.NETWORK_CTX;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.PAYER_RECORDS_OR_CONSOLIDATED_FCQ;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.RECORD_STREAM_RUNNING_HASH;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.STAKING_INFO;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.STORAGE;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.TOKEN_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.UNIQUE_TOKENS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.singleton.SingletonNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.File;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Optional;

/**
 * A utility class for dumping the state of the {@link MerkleHederaState} to a directory.
 */
public class StateDumper {
    private static final String SEMANTIC_UNIQUE_TOKENS = "uniqueTokens.txt";
    private static final String SEMANTIC_TOKEN_RELATIONS = "tokenRelations.txt";
    private static final String SEMANTIC_FILES = "files.txt";
    private static final String SEMANTIC_BLOCK = "blockInfo.txt";
    private static final String SEMANTIC_STAKING_INFO = "stakingInfo.txt";
    private static final String SEMANTIC_STAKING_REWARDS = "stakingRewards.txt";
    private static final String SEMANTIC_TXN_RECORD_QUEUE = "transactionRecords.txt";
    private static final String SEMANTIC_CONGESTION = "congestion.txt";

    public static void dumpMonoChildrenFrom(
            @NonNull final MerkleHederaState state, @NonNull final DumpCheckpoint checkpoint) {
        final MerkleNetworkContext networkContext = state.getChild(NETWORK_CTX);
        final var dumpLoc = getExtantDumpLoc("mono", networkContext.consensusTimeOfLastHandledTxn());
        dumpMonoUniqueTokens(Paths.get(dumpLoc, SEMANTIC_UNIQUE_TOKENS), state.getChild(UNIQUE_TOKENS), checkpoint);
        dumpMonoTokenRelations(
                Paths.get(dumpLoc, SEMANTIC_TOKEN_RELATIONS), state.getChild(TOKEN_ASSOCIATIONS), checkpoint);
        dumpMonoFiles(Paths.get(dumpLoc, SEMANTIC_FILES), state.getChild(STORAGE), checkpoint);
        dumpMonoBlockInfo(
                Paths.get(dumpLoc, SEMANTIC_BLOCK),
                networkContext,
                state.getChild(RECORD_STREAM_RUNNING_HASH),
                checkpoint);
        dumpMonoStakingInfo(Paths.get(dumpLoc, SEMANTIC_STAKING_INFO), state.getChild(STAKING_INFO), checkpoint);
        dumpMonoStakingRewards(Paths.get(dumpLoc, SEMANTIC_STAKING_REWARDS), networkContext, checkpoint);

        dumpMonoPayerRecords(
                Paths.get(dumpLoc, SEMANTIC_TXN_RECORD_QUEUE),
                state.getChild(PAYER_RECORDS_OR_CONSOLIDATED_FCQ),
                checkpoint);

        dumpMonoCongestion(Paths.get(dumpLoc, SEMANTIC_CONGESTION), networkContext, checkpoint);
    }

    public static void dumpModChildrenFrom(
            @NonNull final MerkleHederaState state, @NonNull final DumpCheckpoint checkpoint) {
        final SingletonNode<BlockInfo> blockInfoNode =
                requireNonNull(state.getChild(state.findNodeIndex(BlockRecordService.NAME, BLOCK_INFO_STATE_KEY)));
        final var blockInfo = blockInfoNode.getValue();
        final var dumpLoc = getExtantDumpLoc(
                "mod",
                Optional.ofNullable(blockInfo.consTimeOfLastHandledTxn())
                        .map(then -> Instant.ofEpochSecond(then.seconds(), then.nanos()))
                        .orElse(null));

        //        final VirtualMap<OnDiskKey<NftID>, OnDiskValue<Nft>> uniqueTokens =
        //                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, NFTS_KEY)));
        //        dumpModUniqueTokens(Paths.get(dumpLoc, SEMANTIC_UNIQUE_TOKENS), uniqueTokens, checkpoint);
        //
        //        final VirtualMap<OnDiskKey<TokenAssociation>, OnDiskValue<TokenRelation>> tokenRelations =
        //                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, TOKEN_RELS_KEY)));
        //        dumpModTokenRelations(Paths.get(dumpLoc, SEMANTIC_TOKEN_RELATIONS), tokenRelations, checkpoint);
        //
        //        final VirtualMap<OnDiskKey<FileID>, OnDiskValue<com.hedera.hapi.node.state.file.File>> files =
        //                requireNonNull(state.getChild(state.findNodeIndex(FileService.NAME,
        // FileServiceImpl.BLOBS_KEY)));
        //        dumpModFiles(Paths.get(dumpLoc, SEMANTIC_FILES), files, checkpoint);
        // Dump block info and running hashes
        final SingletonNode<RunningHashes> runningHashesSingleton =
                requireNonNull(state.getChild(state.findNodeIndex(BlockRecordService.NAME, RUNNING_HASHES_STATE_KEY)));
        final SingletonNode<BlockInfo> blocksStateSingleton =
                requireNonNull(state.getChild(state.findNodeIndex(BlockRecordService.NAME, BLOCK_INFO_STATE_KEY)));
        final SingletonNode<EntityNumber> entityIdSingleton =
                requireNonNull(state.getChild(state.findNodeIndex(EntityIdService.NAME, ENTITY_ID_STATE_KEY)));
        dumpModBlockInfo(
                Paths.get(dumpLoc, SEMANTIC_BLOCK),
                runningHashesSingleton.getValue(),
                blocksStateSingleton.getValue(),
                entityIdSingleton.getValue(),
                checkpoint);
        //        //Dump staking info
        //        final MerkleMap<InMemoryKey<EntityNumber>, InMemoryValue<EntityNumber, StakingNodeInfo>>
        // stakingInfoMap =
        //                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY)));
        //        dumpModStakingInfo(Paths.get(dumpLoc, SEMANTIC_STAKING_INFO), stakingInfoMap, checkpoint);
        //        //Dump staking rewards
        //        final SingletonNode<NetworkStakingRewards> stakingRewards =
        //                requireNonNull(state.getChild(state.findNodeIndex(TokenService.NAME,
        // STAKING_NETWORK_REWARDS_KEY)));
        //        dumpModStakingRewards(Paths.get(dumpLoc, SEMANTIC_STAKING_REWARDS), stakingRewards.getValue(),
        // checkpoint);
        //        // Dump txn record queue
        //        final QueueNode<TransactionRecordEntry> queue =
        //                requireNonNull(state.getChild(state.findNodeIndex(RecordCacheService.NAME,
        // TXN_RECORD_QUEUE)));
        //        dumpModTxnRecordQueue(Paths.get(dumpLoc, SEMANTIC_TXN_RECORD_QUEUE), queue, checkpoint);

        //        final SingletonNode<CongestionLevelStarts> congestionLevelStartsSingletonNode =
        //                requireNonNull(state.getChild(state.findNodeIndex(CongestionThrottleService.NAME,
        // CONGESTION_LEVEL_STARTS_STATE_KEY)));
        //        final SingletonNode<ThrottleUsageSnapshots> throttleUsageSnapshotsSingletonNode =
        //                requireNonNull(state.getChild(state.findNodeIndex(CongestionThrottleService.NAME,
        // THROTTLE_USAGE_SNAPSHOTS_STATE_KEY)));
        //        dumpModCongestion(Paths.get(dumpLoc, SEMANTIC_CONGESTION),
        // congestionLevelStartsSingletonNode.getValue(), throttleUsageSnapshotsSingletonNode.getValue(), checkpoint);
    }

    private static String getExtantDumpLoc(
            @NonNull final String stateType, @Nullable final Instant lastHandledConsensusTime) {
        final var dumpLoc = dirFor(stateType, lastHandledConsensusTime);
        new File(dumpLoc).mkdirs();
        return dumpLoc;
    }

    private static String dirFor(@NonNull final String stateType, @Nullable final Instant lastHandledConsensusTime) {
        final var effectiveTime = lastHandledConsensusTime == null ? Instant.EPOCH : lastHandledConsensusTime;
        return String.format("%s-%s", stateType, effectiveTime.toString().replace(":", "_"));
    }
}
