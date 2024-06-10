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

package com.hedera.services.bdd.junit.hedera.embedded;

import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_FIRST_NODE_ACCOUNT_NUM;
import static com.hedera.services.bdd.junit.hedera.utils.AddressBookUtils.CLASSIC_NODE_NAMES;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.Hedera;
import com.hedera.services.bdd.junit.hedera.AbstractLocalNode;
import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeMetadata;
import com.hedera.services.bdd.junit.hedera.subprocess.NodeStatus;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A node running in the same OS process as the JUnit test runner, with a direct reference
 * to a single {@link Hedera} instance shared by every node in the embedded "network".
 *
 * <p>This {@link Hedera} instance does not have a reference to an actual {@link Platform},
 * but instead a {@code FakePlatform} which orders submitted transactions exactly as they
 * are received.
 */
public class EmbeddedNode extends AbstractLocalNode<EmbeddedNode> implements HederaNode {
    public EmbeddedNode(@NonNull final NodeMetadata metadata) {
        super(metadata);
    }

    @Override
    public HederaNode start() {
        assertWorkingDirInitialized();
        return this;
    }

    @Override
    public boolean stop() {
        throw new UnsupportedOperationException("Cannot stop a single node in an embedded network");
    }

    @Override
    public boolean terminate() {
        throw new UnsupportedOperationException("Cannot terminate a single node in an embedded network");
    }

    @Override
    public CompletableFuture<Void> statusFuture(
            @NonNull final PlatformStatus status, @Nullable final Consumer<NodeStatus> nodeStatusObserver) {
        throw new UnsupportedOperationException("Prefer awaiting status of the embedded network");
    }

    @Override
    public CompletableFuture<Void> stopFuture() {
        throw new UnsupportedOperationException("Cannot stop a single node in an embedded network");
    }

    @Override
    protected EmbeddedNode self() {
        return this;
    }

    /**
     * Returns this {@link EmbeddedNode} with classic book data for the given node ID for
     * use in creating an address book.
     *
     * @param nodeId the node ID
     * @return this {@link EmbeddedNode} with classic book data
     */
    public HederaNode withClassicBookDataFor(final int nodeId) {
        return new EmbeddedNode(new NodeMetadata(
                nodeId,
                CLASSIC_NODE_NAMES[nodeId],
                AccountID.newBuilder()
                        .accountNum(CLASSIC_FIRST_NODE_ACCOUNT_NUM + nodeId)
                        .build(),
                metadata.host(),
                metadata.grpcPort(),
                metadata.gossipPort(),
                metadata.tlsGossipPort(),
                metadata.prometheusPort(),
                metadata.workingDir()));
    }
}
