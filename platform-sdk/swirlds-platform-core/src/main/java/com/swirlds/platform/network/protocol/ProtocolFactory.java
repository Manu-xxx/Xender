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

package com.swirlds.platform.network.protocol;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * API for building network protocols
 */
public interface ProtocolFactory {

    /**
     * Constructs an instance of a network protocol using the provided peerId
     * @return a network protocol for connectivity over the bidirectional network
     */
    @Nullable
    default Protocol build(@NonNull final NodeId peerId) {
        return null;
    }

    /**
     * Constructs an instance of a network protocol handshake between peers
     * @param throwOnMismatch if set to true, the protocol will throw an exception on a mismatch.
     *                        if set to false, protocol will log an error and continue
     * @return a protocol handshake between two peers
     */
    @NonNull
    default ProtocolRunnable build(final boolean throwOnMismatch) {
        return connection -> {};
    }
}
