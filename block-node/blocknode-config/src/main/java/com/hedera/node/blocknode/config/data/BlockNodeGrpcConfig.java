/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.blocknode.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Max;
import com.swirlds.config.api.validation.annotation.Min;

@ConfigData("blockNodeGrpc")
public record BlockNodeGrpcConfig(
        @ConfigProperty(defaultValue = "50601") @Min(0) @Max(65535) int port,
        @ConfigProperty(defaultValue = "50602") @Min(0) @Max(65535) int tlsPort) {

    public BlockNodeGrpcConfig {
        if (port == tlsPort && port != 0) {
            throw new IllegalArgumentException("grpc.port and grpc.tlsPort must be different");
        }
    }
}
