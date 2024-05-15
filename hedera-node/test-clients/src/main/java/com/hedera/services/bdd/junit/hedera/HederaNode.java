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

package com.hedera.services.bdd.junit.hedera;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.services.bdd.spec.HapiSpec;
import com.swirlds.platform.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.CompletableFuture;

public interface HederaNode {
    /**
     * Gets the port number of the gRPC service.
     *
     * @return the port number of the gRPC service
     */
    int getPort();

    /**
     * Gets the node ID, such as 0, 1, 2, or 3.
     * @return the node ID
     */
    long getNodeId();

    /**
     * The name of the node, such as "Alice" or "Bob".
     * @return the node name
     */
    String getName();

    /**
     * Gets the node Account ID
     * @return the node account ID
     */
    AccountID getAccountId();

    /**
     * Starts the node software.
     */
    void start(String configTxt);

    /**
     * Stops the node software gracefully
     */
    boolean stop();

    /**
     * Stops the node software forcibly.
     */
    boolean terminate();

    /**
     * Returns a future that resolves when the node has the given status.
     *
     * @param status the status to wait for
     * @return a future that resolves when the node has the given status
     */
    CompletableFuture<Void> waitForStatus(@NonNull PlatformStatus status);

    /**
     * Returns a future that resolves when the node has stopped.
     *
     * @return a future that resolves when the node has stopped
     */
    CompletableFuture<Void> waitForStopped();

    /**
     * Returns the string that would identify this node as a target
     * server in a {@link HapiSpec} that is submitting transactions
     * and sending queries via gRPC.
     *
     * <p><b>(FUTURE)</b> Remove this method once {@link HapiSpec} is
     * refactored to be agnostic about how a target node should
     * receive transactions and queries. (E.g. an embedded node
     * can have its workflows directly invoked.)
     *
     * @return this node's HAPI spec identifier
     */
    default String hapiSpecIdentifier() {
        return "127.0.0.1:" + getPort() + ":0.0." + getNodeId();
    }
}
