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

package com.hedera.node.blocknode.core.services;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.filesystem.api.FileSystemApi;
import com.hedera.services.stream.v7.proto.Block;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockNodeLocalFileWatcherImpl {
    private static final Logger logger = LogManager.getLogger(BlockNodeLocalFileWatcherImpl.class);

    private static final Path blocksLocPath = Path.of(
            "/home/nikolay/Desktop/hedera-services-nick/hedera-node/hedera-app/build/node/hedera-node/data/block-streams/block0.0.3/");

    private final ConfigProvider configProvider;

    private final FileSystemApi fileSystemApi;

    private byte[] readCompressedFileBytes(final Path filepath) throws IOException {
        return (new GZIPInputStream(new FileInputStream(filepath.toString()))).readAllBytes();
    }

    private FileAlterationListenerAdaptor buildFileListener() {
        return new FileAlterationListenerAdaptor() {
            @Override
            public void onFileCreate(File file) {
                final Path newFilePath = file.toPath();
                try {
                    byte[] content = readCompressedFileBytes(newFilePath);
                    Block block = Block.parseFrom(content);
                    fileSystemApi.writeBlock(block);
                    block.getItemsList().stream().toList().forEach(logger::info);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                logger.info("--- file create: " + newFilePath);
            }

            @Override
            public void onFileDelete(File file) {
                // no-op
            }

            @Override
            public void onFileChange(File file) {
                // no-op
            }
        };
    }

    public BlockNodeLocalFileWatcherImpl(final ConfigProvider configProvider, final FileSystemApi fileSystemApi) {
        this.configProvider = configProvider;
        this.fileSystemApi = fileSystemApi;

        final FileAlterationObserver observer = new FileAlterationObserver(blocksLocPath.toFile());
        observer.addListener(buildFileListener());

        final FileAlterationMonitor monitor = new FileAlterationMonitor(500L);
        monitor.addObserver(observer);
        try {
            monitor.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
