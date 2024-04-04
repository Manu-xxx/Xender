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

package com.hedera.node.blocknode.filesystem.local;

import com.hedera.node.blocknode.config.ConfigProvider;
import com.hedera.node.blocknode.config.data.BlockNodeFileSystemConfig;
import com.hedera.node.blocknode.filesystem.api.FileSystemApi;
import com.hedera.node.blocknode.util.BlockNodeUtil;
import com.hedera.services.stream.v7.proto.Block;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LocalFileSystem implements FileSystemApi {
    private static final Logger logger = LogManager.getLogger(LocalFileSystem.class);

    private final BlockNodeFileSystemConfig fileSystemConfig;

    private final Path blocksExportPath;

    public LocalFileSystem(ConfigProvider configProvider) {
        this.fileSystemConfig = configProvider.getConfiguration().getConfigData(BlockNodeFileSystemConfig.class);

        this.blocksExportPath = Path.of(System.getProperty("user.dir") + fileSystemConfig.blocksExportPath());
        blocksExportPath.toFile().mkdirs();
    }

    @Override
    public void writeBlock(Block block) {
        Path blockFilePath = null;
        try {
            String blockFileName = BlockNodeUtil.extractBlockFileNameFromBlock(block);
            blockFilePath = blocksExportPath.resolve(blockFileName);

            OutputStream out = Files.newOutputStream(blockFilePath);
            block.writeTo(new GZIPOutputStream(out, 1024 * 256));
            out.close();

            logger.info("Block successfully written to file: {}", blockFilePath);
        } catch (IOException e) {
            logger.error("Error writing block to: {}", blockFilePath, e);
        }
    }

    @Override
    public Block readBlock(long number) {
        return null;
    }
}
