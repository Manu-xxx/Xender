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

package com.hedera.storage.core;

import com.hedera.storage.config.ConfigProvider;
import com.hedera.storage.config.data.BlockNodeGrpcConfig;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class BlockNodeServer {
    private static final Logger logger = LogManager.getLogger(BlockNodeServer.class);
    private final int port;
    private final Server server;

    public BlockNodeServer() {
        final var configProvider = new ConfigProvider();
        final var blockNodeConfig = configProvider.configuration.getConfigData(BlockNodeGrpcConfig.class);

        this.port = blockNodeConfig.port();
        this.server =
                ServerBuilder.forPort(port).addService(new BlockNodeService()).build();
    }

    public void start() throws IOException {
        server.start();
        logger.info("Block app started at port: " + port);
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public static void main(final String... args) {
        logger.info("BlockNode - Main");
        BlockNodeServer server = new BlockNodeServer();

        try {
            server.start();
            server.blockUntilShutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
