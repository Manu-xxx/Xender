/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.network.connectivity;

import com.swirlds.common.config.SocketConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;

/**
 * used to create and receive unencrypted TCP connections
 */
public class TcpFactory implements SocketFactory {
    private final SocketConfig socketConfig;

    public TcpFactory(@NonNull final SocketConfig socketConfig) {
        this.socketConfig = Objects.requireNonNull(socketConfig);
    }

    @Override
    public ServerSocket createServerSocket(final byte[] ipAddress, final int port) throws IOException {
        final ServerSocket serverSocket = new ServerSocket();
        SocketFactory.configureAndBind(serverSocket, socketConfig, ipAddress, port);
        return serverSocket;
    }

    @Override
    public Socket createClientSocket(final String ipAddress, final int port) throws IOException {
        final Socket clientSocket = new Socket();
        SocketFactory.configureAndConnect(clientSocket, socketConfig, ipAddress, port);
        return clientSocket;
    }
}
