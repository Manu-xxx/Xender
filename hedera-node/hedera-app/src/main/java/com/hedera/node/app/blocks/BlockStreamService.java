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

package com.hedera.node.app.blocks;

import static com.hedera.node.config.types.StreamMode.RECORDS;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.blocks.schemas.V0540BlockStreamSchema;
import com.hedera.node.config.data.BlockStreamConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

/**
 * Service for BlockStreams implementation responsible for tracking state changes
 * and writing them to a block
 */
public class BlockStreamService implements Service {
    public static final Bytes FAKE_RESTART_BLOCK_HASH = Bytes.fromHex("abcd".repeat(24));

    public static final String NAME = "BlockStreamService";

    private final boolean enabled;

    @Nullable
    private Bytes migratedLastBlockHash;

    /**
     * Service constructor.
     */
    public BlockStreamService(final Configuration config) {
        this.enabled = config.getConfigData(BlockStreamConfig.class).streamMode() != RECORDS;
    }

    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        if (enabled) {
            registry.register(new V0540BlockStreamSchema(this::setMigratedLastBlockHash));
        }
    }

    /**
     * Returns the last block hash as migrated from a state that used record streams, or empty
     * if there was no such hash observed during migration.
     * @return the last block hash
     */
    public Optional<Bytes> migratedLastBlockHash() {
        return Optional.ofNullable(migratedLastBlockHash);
    }

    private void setMigratedLastBlockHash(@NonNull final Bytes migratedLastBlockHash) {
        this.migratedLastBlockHash = requireNonNull(migratedLastBlockHash);
    }
}
