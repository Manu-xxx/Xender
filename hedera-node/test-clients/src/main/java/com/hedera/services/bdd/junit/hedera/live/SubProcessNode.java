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

package com.hedera.services.bdd.junit.hedera.live;

import static com.hedera.services.bdd.junit.hedera.live.ProcessUtils.destroyAnySubProcessNodeWithId;
import static com.hedera.services.bdd.junit.hedera.live.ProcessUtils.startSubProcessNodeFrom;
import static com.hedera.services.bdd.junit.hedera.live.WorkingDirUtils.recreateWorkingDir;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.Hedera;
import com.hedera.services.bdd.junit.hedera.AbstractNode;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.swirlds.base.function.BooleanFunction;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * A node running in its own OS process as a subprocess of the JUnit test runner.
 */
public class SubProcessNode extends AbstractNode implements HederaNode {

    private final GrpcPinger grpcPinger;
    private final PrometheusClient prometheusClient;
    /**
     * If this node is running, the {@link ProcessHandle} of the node's process; null otherwise.
     */
    @Nullable
    private ProcessHandle processHandle;
    /**
     * Whether the working directory has been initialized.
     */
    private boolean workingDirInitialized;

    public SubProcessNode(
            @NonNull final NodeMetadata metadata,
            @NonNull final GrpcPinger grpcPinger,
            @NonNull final PrometheusClient prometheusClient) {
        super(metadata);
        this.grpcPinger = requireNonNull(grpcPinger);
        this.prometheusClient = requireNonNull(prometheusClient);
        // Just something to keep checkModuleInfo from claiming we don't require com.hedera.node.app
        requireNonNull(Hedera.class);
    }

    @Override
    public void initWorkingDir(@NonNull final String configTxt) {
        recreateWorkingDir(metadata.workingDir(), configTxt);
        workingDirInitialized = true;
    }

    @Override
    public void start() {
        assertStopped();
        assertWorkingDirInitialized();
        destroyAnySubProcessNodeWithId(metadata.nodeId());
        processHandle = startSubProcessNodeFrom(metadata);
    }

    @Override
    public boolean stop() {
        return stopWith(ProcessHandle::destroy);
    }

    @Override
    public boolean terminate() {
        return stopWith(ProcessHandle::destroyForcibly);
    }

    @Override
    public CompletableFuture<Void> statusFuture(@NonNull final PlatformStatus status, @NonNull final Duration timeout) {
        return ProcessUtils.conditionFuture(
                () -> {
                    final var currentStatus = prometheusClient.statusFromLocalEndpoint(metadata.prometheusPort());
                    if (!status.equals(currentStatus)) {
                        return false;
                    }
                    return status != PlatformStatus.ACTIVE || grpcPinger.isLive(metadata.grpcPort());
                },
                timeout);
    }

    @Override
    public CompletableFuture<Void> stopFuture(@NonNull final Duration timeout) {
        return ProcessUtils.conditionFuture(() -> processHandle == null, timeout);
    }

    @Override
    public String toString() {
        return "SubProcessNode{" + "metadata=" + metadata + ", workingDirInitialized=" + workingDirInitialized + '}';
    }

    private boolean stopWith(@NonNull final BooleanFunction<ProcessHandle> stop) {
        if (processHandle == null) {
            return false;
        }
        final var result = stop.apply(processHandle);
        processHandle = null;
        return result;
    }

    private void assertStopped() {
        if (processHandle != null) {
            throw new IllegalStateException("Node is still running");
        }
    }

    private void assertWorkingDirInitialized() {
        if (!workingDirInitialized) {
            throw new IllegalStateException("Working directory not initialized");
        }
    }
}
