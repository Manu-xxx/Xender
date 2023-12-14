/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.SOCKET_EXCEPTIONS;

import com.swirlds.base.time.Time;
import com.swirlds.common.config.SocketConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.gossip.sync.SyncInputStream;
import com.swirlds.platform.gossip.sync.SyncOutputStream;
import com.swirlds.platform.network.ByteConstants;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.ConnectionTracker;
import com.swirlds.platform.network.NetworkUtils;
import com.swirlds.platform.network.SocketConnection;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Accept inbound connections and executes the platform handshake. This class is thread-safe
 */
public class InboundConnectionHandler {
    private static final Logger logger = LogManager.getLogger(InboundConnectionHandler.class);

    private final ConnectionTracker connectionTracker;
    private final NodeId selfId;
    private final AddressBook addressBook;
    private final InterruptableConsumer<Connection> newConnectionConsumer;
    private final SocketConfig socketConfig;
    private final boolean doVersionCheck;
    private final SoftwareVersion softwareVersion;
    /** Rate Limited Logger for SocketExceptions */
    private final RateLimitedLogger socketExceptionLogger;

    private final PlatformContext platformContext;

    public InboundConnectionHandler(
            @NonNull final PlatformContext platformContext,
            @NonNull final ConnectionTracker connectionTracker,
            @NonNull final NodeId selfId,
            @NonNull final AddressBook addressBook,
            @NonNull final InterruptableConsumer<Connection> newConnectionConsumer,
            final boolean doVersionCheck,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final Time time) {
        this.platformContext = Objects.requireNonNull(platformContext);
        this.connectionTracker = Objects.requireNonNull(connectionTracker);
        this.selfId = Objects.requireNonNull(selfId);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.newConnectionConsumer = Objects.requireNonNull(newConnectionConsumer);
        this.doVersionCheck = doVersionCheck;
        this.softwareVersion = Objects.requireNonNull(softwareVersion);
        Objects.requireNonNull(time);
        this.socketExceptionLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));
        this.socketConfig = platformContext.getConfiguration().getConfigData(SocketConfig.class);
    }

    /**
     * Authenticate the peer that has just established a new connection and create a {@link Connection}
     *
     * @param clientSocket the newly created socket
     */
    public void handle(final Socket clientSocket) {
        SerializableDataInputStream dis = null;
        SerializableDataOutputStream dos = null;
        NodeId otherId = null;
        long acceptTime = 0;
        try {
            acceptTime = System.currentTimeMillis();
            clientSocket.setTcpNoDelay(socketConfig.tcpNoDelay());
            clientSocket.setSoTimeout(socketConfig.timeoutSyncClientSocket());

            final SyncInputStream sis = SyncInputStream.createSyncInputStream(
                    platformContext, clientSocket.getInputStream(), socketConfig.bufferSize());
            final SyncOutputStream sos = SyncOutputStream.createSyncOutputStream(
                    platformContext, clientSocket.getOutputStream(), socketConfig.bufferSize());

            dis = new SerializableDataInputStream(sis);
            dos = new SerializableDataOutputStream(sos);

            if (doVersionCheck) {
                dos.writeSerializable(softwareVersion, true);
                dos.flush();

                final SoftwareVersion otherVersion = dis.readSerializable();
                if (otherVersion == null
                        || otherVersion.getClass() != softwareVersion.getClass()
                        || otherVersion.compareTo(softwareVersion) != 0) {
                    throw new IOException("This node has software version " + softwareVersion
                            + " but the other node has software version " + otherVersion + ". Closing connection.");
                }
            }

            final String otherKey = dis.readUTF();

            otherId = addressBook.getNodeId(otherKey);

            dos.writeInt(ByteConstants.COMM_CONNECT); // send an ACK for creating connection
            dos.flush();

            final SocketConnection sc = SocketConnection.create(
                    selfId,
                    otherId,
                    connectionTracker,
                    false,
                    clientSocket,
                    sis,
                    sos,
                    platformContext.getConfiguration());
            newConnectionConsumer.accept(sc);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            String formattedException = NetworkUtils.formatException(e);
            logger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Inbound connection from {} to {} was interrupted: {}",
                    otherId == null ? "unknown" : otherId,
                    selfId,
                    formattedException);
            NetworkUtils.close(dis, dos, clientSocket);
        } catch (final IOException e) {
            String formattedException = NetworkUtils.formatException(e);
            socketExceptionLogger.warn(
                    SOCKET_EXCEPTIONS.getMarker(),
                    "Inbound connection from {} to {} had IOException: {}",
                    otherId == null ? "unknown" : otherId,
                    selfId,
                    formattedException);
            NetworkUtils.close(dis, dos, clientSocket);
        } catch (final RuntimeException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Inbound connection error, remote IP: {}\n" + "Time from accept to exception: {} ms",
                    clientSocket.getInetAddress().toString(),
                    acceptTime == 0 ? "N/A" : (System.currentTimeMillis() - acceptTime),
                    e);
            NetworkUtils.close(dis, dos, clientSocket);
        }
    }
}
