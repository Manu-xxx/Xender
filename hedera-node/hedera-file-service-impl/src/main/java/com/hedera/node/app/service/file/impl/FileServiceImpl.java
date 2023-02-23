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

import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetContentsHandler;
import com.hedera.node.app.service.file.impl.handlers.FileGetInfoHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKey;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobKeySerializer;
import com.hedera.node.app.service.mono.state.virtual.VirtualBlobValue;
import com.hedera.node.app.spi.service.Service;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.state.serdes.MonoMapSerdesAdapter;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/** Standard implementation of the {@link FileService} {@link Service}. */
public final class FileServiceImpl implements FileService {

    private static final int MAX_BLOBS = 4096;
    private static final SemanticVersion CURRENT_VERSION =
            SemanticVersion.newBuilder().setMinor(34).build();
    public static final String BLOBS_KEY = "BLOBS";

    private final FileAppendHandler fileAppendHandler;

    private final FileCreateHandler fileCreateHandler;

    private final FileDeleteHandler fileDeleteHandler;

    private final FileUpdateHandler fileUpdateHandler;

    private final FileGetContentsHandler fileGetContentsHandler;

    private final FileGetInfoHandler fileGetInfoHandler;

    private final FileSystemDeleteHandler fileSystemDeleteHandler;

    private final FileSystemUndeleteHandler fileSystemUndeleteHandler;

    /**
     * Creates a new {@link FileServiceImpl} instance.
     */
    public FileServiceImpl() {
        this.fileAppendHandler = new FileAppendHandler();
        this.fileCreateHandler = new FileCreateHandler();
        this.fileDeleteHandler = new FileDeleteHandler();
        this.fileUpdateHandler = new FileUpdateHandler();
        this.fileGetContentsHandler = new FileGetContentsHandler();
        this.fileGetInfoHandler = new FileGetInfoHandler();
        this.fileSystemDeleteHandler = new FileSystemDeleteHandler();
        this.fileSystemUndeleteHandler = new FileSystemUndeleteHandler();
    }

    /**
     * Returns the {@link FileAppendHandler} instance.
     *
     * @return the {@link FileAppendHandler} instance.
     */
    @NonNull
    public FileAppendHandler getFileAppendHandler() {
        return fileAppendHandler;
    }

    /**
     * Returns the {@link FileCreateHandler} instance.
     *
     * @return the {@link FileCreateHandler} instance.
     */
    @NonNull
    public FileCreateHandler getFileCreateHandler() {
        return fileCreateHandler;
    }

    /**
     * Returns the {@link FileDeleteHandler} instance.
     *
     * @return the {@link FileDeleteHandler} instance.
     */
    @NonNull
    public FileDeleteHandler getFileDeleteHandler() {
        return fileDeleteHandler;
    }

    /**
     * Returns the {@link FileUpdateHandler} instance.
     *
     * @return the {@link FileUpdateHandler} instance.
     */
    @NonNull
    public FileUpdateHandler getFileUpdateHandler() {
        return fileUpdateHandler;
    }

    /**
     * Returns the {@link FileGetContentsHandler} instance.
     *
     * @return the {@link FileGetContentsHandler} instance.
     */
    @NonNull
    public FileGetContentsHandler getFileGetContentsHandler() {
        return fileGetContentsHandler;
    }

    /**
     * Returns the {@link FileGetInfoHandler} instance.
     *
     * @return the {@link FileGetInfoHandler} instance.
     */
    @NonNull
    public FileGetInfoHandler getFileGetInfoHandler() {
        return fileGetInfoHandler;
    }

    /**
     * Returns the {@link FileSystemDeleteHandler} instance.
     *
     * @return the {@link FileSystemDeleteHandler} instance.
     */
    @NonNull
    public FileSystemDeleteHandler getFileSystemDeleteHandler() {
        return fileSystemDeleteHandler;
    }

    /**
     * Returns the {@link FileSystemUndeleteHandler} instance.
     *
     * @return the {@link FileSystemUndeleteHandler} instance.
     */
    @NonNull
    public FileSystemUndeleteHandler getFileSystemUndeleteHandler() {
        return fileSystemUndeleteHandler;
    }

    @NonNull
    @Override
    public Set<TransactionHandler> getTransactionHandler() {
        return Set.of(
                fileAppendHandler,
                fileCreateHandler,
                fileDeleteHandler,
                fileUpdateHandler,
                fileSystemDeleteHandler,
                fileSystemUndeleteHandler);
    }

    @NonNull
    @Override
    public Set<QueryHandler> getQueryHandler() {
        return Set.of(fileGetContentsHandler, fileGetInfoHandler);
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
                return Set.of(blobsDef());
            }
        };
    }

    private static StateDefinition<VirtualBlobKey, VirtualBlobValue> blobsDef() {
        final var keySerdes = MonoMapSerdesAdapter.serdesForVirtualKey(
                VirtualBlobKey.CURRENT_VERSION, VirtualBlobKey::new, new VirtualBlobKeySerializer());
        final var valueSerdes =
                MonoMapSerdesAdapter.serdesForVirtualValue(VirtualBlobValue.CURRENT_VERSION, VirtualBlobValue::new);

        return StateDefinition.onDisk(BLOBS_KEY, keySerdes, valueSerdes, MAX_BLOBS);
    }
}
