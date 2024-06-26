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

package com.hedera.services.bdd.junit.hedera.subprocess;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.ADDRESS_BOOK;
import static com.hedera.services.bdd.junit.hedera.subprocess.ProcessUtils.awaitStatus;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.classicMetadataFor;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.configTxtForLocal;
import static com.hedera.services.bdd.suites.TargetNetworkType.SUBPROCESS_NETWORK;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static java.util.concurrent.CompletableFuture.runAsync;

import com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension;
import com.hedera.services.bdd.junit.hedera.AbstractGrpcNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.spec.infrastructure.HapiClients;
import com.hedera.services.bdd.suites.TargetNetworkType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

/**
 * A network of Hedera nodes started in subprocesses and accessed via gRPC. Unlike
 * nodes in a remote or embedded network, its nodes support lifecycle operations like
 * stopping and restarting.
 */
public class SubProcessNetwork extends AbstractGrpcNetwork implements HederaNetwork {
    // We need 5 ports for each node in the network (gRPC, gRPC, gossip, gossip TLS, prometheus)
    private static final int PORTS_PER_NODE = 5;
    private static final SplittableRandom RANDOM = new SplittableRandom();
    private static final int FIRST_CANDIDATE_PORT = 30000;
    private static final int LAST_CANDIDATE_PORT = 40000;

    private static final String SUBPROCESS_HOST = "127.0.0.1";
    private static final String SHARED_NETWORK_NAME = "SHARED_NETWORK";
    private static final GrpcPinger GRPC_PINGER = new GrpcPinger();
    private static final PrometheusClient PROMETHEUS_CLIENT = new PrometheusClient();

    // We initialize these randomly to reduce risk of port binding conflicts in CI runners
    private static int nextGrpcPort;
    private static int nextGossipPort;
    private static int nextGossipTlsPort;
    private static int nextPrometheusPort;
    private static boolean nextPortsInitialized = false;

    private final String configTxt;

    private AtomicReference<CompletableFuture<Void>> ready = new AtomicReference<>();

    private SubProcessNetwork(@NonNull final String networkName, @NonNull final List<SubProcessNode> nodes) {
        super(networkName, nodes.stream().map(node -> (HederaNode) node).toList());
        this.configTxt = configTxtForLocal(name(), nodes(), nextGossipPort, nextGossipTlsPort);
    }

    /**
     * Creates a shared network of sub-process nodes with the given size.
     *
     * @param size the number of nodes in the network
     * @return the shared network
     */
    public static synchronized HederaNetwork newSharedNetwork(final int size) {
        if (NetworkTargetingExtension.SHARED_NETWORK.get() != null) {
            throw new UnsupportedOperationException("Only one shared network allowed per launcher session");
        }
        final var sharedNetwork = liveNetwork(SHARED_NETWORK_NAME, size);
        NetworkTargetingExtension.SHARED_NETWORK.set(sharedNetwork);
        return sharedNetwork;
    }

    /**
     * Returns the network type; for now this is always
     * {@link TargetNetworkType#SUBPROCESS_NETWORK}.
     *
     * @return the network type
     */
    @Override
    public TargetNetworkType type() {
        return SUBPROCESS_NETWORK;
    }

    /**
     * Starts all nodes in the network.
     */
    @Override
    public void start() {
        nodes.forEach(node -> node.initWorkingDir(configTxt).start());
    }

    /**
     * Forcibly stops all nodes in the network.
     */
    @Override
    public void terminate() {
        nodes.forEach(HederaNode::terminate);
    }

    /**
     * Waits for all nodes in the network to be ready within the given timeout.
     */
    @Override
    public void awaitReady(@NonNull final Duration timeout) {
        if (ready.get() == null) {
            final var future = runAsync(() -> {
                final var deadline = Instant.now().plus(timeout);
                nodes.forEach(node -> awaitStatus(node, ACTIVE, Duration.between(Instant.now(), deadline)));
                this.clients = HapiClients.clientsFor(this);
            });
            if (!ready.compareAndSet(null, future)) {
                // We only need one thread to wait for readiness
                future.cancel(true);
            }
        }
        ready.get().join();
    }

    /**
     * Assigns new ports to all nodes in the network.
     *
     * <p>Overwrites the existing <i>config.txt</i> file for each node in the network with the new ports.
     */
    public void assignNewPorts() {
        String configTxt;
        try {
            configTxt = Files.readString(nodes().getFirst().getExternalPath(ADDRESS_BOOK));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final var networkSize = nodes().size();
        final var firstGrpcPort = nodes().getFirst().getGrpcPort();
        final var totalPortsUsed = networkSize * PORTS_PER_NODE;
        final var newFirstGrpcPort = firstGrpcPort + totalPortsUsed;
        for (int port = firstGrpcPort; port < newFirstGrpcPort; port++) {
            configTxt = configTxt.replaceAll("" + port, "" + (port + totalPortsUsed));
        }
        final var newConfigTxt = configTxt;
        initializeNextPortsForNetwork(networkSize, newFirstGrpcPort);
        nodes.forEach(node -> {
            final var configTxtLoc = node.getExternalPath(ADDRESS_BOOK);
            try {
                Files.writeString(configTxtLoc, newConfigTxt);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            final int nodeId = (int) node.getNodeId();
            ((SubProcessNode) node)
                    .reassignPorts(
                            nextGrpcPort + nodeId * 2,
                            nextGossipPort + nodeId * 2,
                            nextGossipTlsPort + nodeId * 2,
                            nextPrometheusPort + nodeId);
        });
        this.clients = HapiClients.clientsFor(this);
    }

    /**
     * Creates a network of live (sub-process) nodes with the given name and size. This method is
     * synchronized because we don't want to re-use any ports across different networks.
     *
     * @param name the name of the network
     * @param size the number of nodes in the network
     * @return the network
     */
    private static synchronized HederaNetwork liveNetwork(@NonNull final String name, final int size) {
        if (!nextPortsInitialized) {
            initializeNextPortsForNetwork(size);
        }
        final var network = new SubProcessNetwork(
                name,
                IntStream.range(0, size)
                        .mapToObj(nodeId -> new SubProcessNode(
                                classicMetadataFor(
                                        nodeId,
                                        name,
                                        SUBPROCESS_HOST,
                                        SHARED_NETWORK_NAME.equals(name) ? null : name,
                                        nextGrpcPort,
                                        nextGossipPort,
                                        nextGossipTlsPort,
                                        nextPrometheusPort),
                                GRPC_PINGER,
                                PROMETHEUS_CLIENT))
                        .toList());
        Runtime.getRuntime().addShutdownHook(new Thread(network::terminate));
        return network;
    }

    private static void initializeNextPortsForNetwork(final int size) {
        initializeNextPortsForNetwork(size, randomPortAfter(FIRST_CANDIDATE_PORT, size * PORTS_PER_NODE));
    }

    private static void initializeNextPortsForNetwork(final int size, final int firstGrpcPort) {
        // Suppose firstGrpcPort is 10000 with 4 nodes in the network, then:
        //   - nextGrpcPort = 10000
        //   - nextGossipPort = 10008
        //   - nextGossipTlsPort = 10009
        //   - nextPrometheusPort = 10016
        // So for a nodeId of 2, the assigned ports are:
        //   - grpcPort = nextGrpcPort + nodeId * 2 = 10004
        //   - gossipPort = nextGossipPort + nodeId * 2 = 10012
        //   - gossipTlsPort = nextGossipTlsPort + nodeId * 2 = 10013
        //   - prometheusPort = nextPrometheusPort + nodeId = 10018
        nextGrpcPort = firstGrpcPort;
        nextGossipPort = nextGrpcPort + 2 * size;
        nextGossipTlsPort = nextGossipPort + 1;
        nextPrometheusPort = nextGossipPort + 2 * size;
        nextPortsInitialized = true;
    }

    private static int randomPortAfter(final int firstAvailable, final int numRequired) {
        return RANDOM.nextInt(firstAvailable, LAST_CANDIDATE_PORT + 1 - numRequired);
    }
}
