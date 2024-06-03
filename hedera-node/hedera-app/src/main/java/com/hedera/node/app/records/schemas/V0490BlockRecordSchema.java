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

package com.hedera.node.app.records.schemas;

import static com.hedera.node.app.records.BlockRecordService.EPOCH;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.records.impl.codec.BlockInfoTranslator;
import com.hedera.node.app.records.impl.codec.RunningHashesTranslator;
import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.spi.WritableSingletonStateBase;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class V0490BlockRecordSchema extends Schema {
    private static final Logger logger = LogManager.getLogger(V0490BlockRecordSchema.class);

    /** The key for the {@link RunningHashes} object in state */
    public static final String RUNNING_HASHES_STATE_KEY = "RUNNING_HASHES";
    /** The key for the {@link BlockInfo} object in state */
    public static final String BLOCK_INFO_STATE_KEY = "BLOCKS";
    /** The original hash, only used at genesis */
    private static final Bytes GENESIS_HASH = Bytes.wrap(new byte[48]);
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    private static RecordsRunningHashLeaf fs;
    private static MerkleNetworkContext mnc;

    public V0490BlockRecordSchema() {
        super(VERSION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull Set<StateDefinition> statesToCreate() {
        return Set.of(
                StateDefinition.singleton(RUNNING_HASHES_STATE_KEY, RunningHashes.PROTOBUF),
                StateDefinition.singleton(BLOCK_INFO_STATE_KEY, BlockInfo.PROTOBUF));
    }

    /**
     * {@inheritDoc}
     * */
    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        final var runningHashState = ctx.newStates().getSingleton(RUNNING_HASHES_STATE_KEY);
        final var blocksState = ctx.newStates().getSingleton(BLOCK_INFO_STATE_KEY);
        final var isGenesis = ctx.previousVersion() == null;
        if (isGenesis) {
            final var blocks = new BlockInfo(-1, EPOCH, Bytes.EMPTY, EPOCH, false, EPOCH);
            blocksState.put(blocks);
            final var runningHashes =
                    RunningHashes.newBuilder().runningHash(GENESIS_HASH).build();
            runningHashState.put(runningHashes);
        }

        if (mnc != null) {
            logger.info("BBM: doing block record migration");

            // first migrate the hashes
            final var toHashState = ctx.newStates().getSingleton(RUNNING_HASHES_STATE_KEY);
            final var toRunningHashes = RunningHashesTranslator.runningHashesFromRecordsRunningHashLeaf(fs);
            toHashState.put(toRunningHashes);
            if (toHashState.isModified()) ((WritableSingletonStateBase) toHashState).commit();

            // then migrate the latest block info
            final var toBlockState = ctx.newStates().getSingleton(BLOCK_INFO_STATE_KEY);

            try {
                final BlockInfo toBlockInfo = BlockInfoTranslator.blockInfoFromMerkleNetworkContext(mnc);
                toBlockState.put(toBlockInfo);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (toBlockState.isModified()) ((WritableSingletonStateBase) toBlockState).commit();

            logger.info("BBM: finished block record migration");
        } else {
            logger.warn("BBM: no block 'from' state found");
        }

        fs = null;
        mnc = null;
    }

    public static void setFs(RecordsRunningHashLeaf fs) {
        V0490BlockRecordSchema.fs = fs;
    }

    public static void setMnc(MerkleNetworkContext mnc) {
        V0490BlockRecordSchema.mnc = mnc;
    }
}
