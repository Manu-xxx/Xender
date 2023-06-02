/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.file.File;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.codec.EntityNumCodec;
import com.hedera.node.app.service.mono.state.codec.CodecFactory;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Set;

/** Standard implementation of the {@link FileService} {@link com.hedera.node.app.spi.Service}. */
public final class FileServiceImpl implements FileService {
    private static final long MAX_BLOBS = 50_000_000L;
    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().minor(34).build();
    public static final String BLOBS_KEY = "FILES";

    @Override
    public void registerMonoAdapterSchemas(@NonNull final SchemaRegistry registry) {
        registerSchemas(registry);
    }

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(fileServiceSchema());
    }

    private Schema fileServiceSchema() {
        return new Schema(CURRENT_VERSION) {
            @NonNull
            @Override
            public Set<StateDefinition> statesToCreate() {
                return Set.of(filesDef());
            }
        };
    }

    @NonNull
    private static StateDefinition<EntityNum, File> filesDef() {
        final var keyCodec = new EntityNumCodec();

        final var valueCodec = CodecFactory.newInMemoryCodec(File.PROTOBUF::parse, File.PROTOBUF::write);

        return StateDefinition.onDisk(BLOBS_KEY, keyCodec, valueCodec, Math.toIntExact(MAX_BLOBS) );
    }
}
