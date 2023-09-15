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

package com.hedera.node.app;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.util.BootstrapUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Main hedera consensus node entry point.
 */
public final class ServicesMain {

    private ServicesMain() {}

    /**
     * Determine this node's ID based on the command line arguments.
     *
     * @param args The command line arguments
     * @return The node ID
     */
    @NonNull
    private static NodeId parseSelfId(@Nullable final String... args) {
        if (args == null || args.length == 0) {
            return new NodeId(0);
        }
        return new NodeId(Integer.parseInt(args[0]));
    }

    /**
     * Setup and get the constructable registry.
     *
     * @return The constructable registry
     */
    @NonNull
    private static ConstructableRegistry getRegistry() {
        BootstrapUtils.setupConstructableRegistry();
        return ConstructableRegistry.getInstance();
    }

    /**
     * Launches the application.
     *
     * @param args First arg, if specified, will be the node ID
     */
    public static void main(@Nullable final String... args) throws Exception {
        new Hedera(getRegistry(), parseSelfId(args)).start();
    }
}
