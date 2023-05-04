/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.config.data;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("netty")
public record NettyConfig(
        //@ConfigProperty(defaultValue = "PROD") Profile mode,
        @ConfigProperty(value = "prod.flowControlWindow", defaultValue = "10240") int prodFlowControlWindow,
        @ConfigProperty(value = "prod.maxConcurrentCalls", defaultValue = "10") int prodMaxConcurrentCalls,
        @ConfigProperty(value = "prod.maxConnectionAge", defaultValue = "15") long prodMaxConnectionAge,
        @ConfigProperty(value = "prod.maxConnectionAgeGrace", defaultValue = "5") long prodMaxConnectionAgeGrace,
        @ConfigProperty(value = "prod.maxConnectionIdle", defaultValue = "10") long prodMaxConnectionIdle,
        @ConfigProperty(value = "prod.keepAliveTime", defaultValue = "10") long prodKeepAliveTime,
        @ConfigProperty(value = "prod.keepAliveTimeout", defaultValue = "3") long prodKeepAliveTimeout,
        @ConfigProperty(defaultValue = "90") int startRetries,
        @ConfigProperty(defaultValue = "1000") long startRetryIntervalMs,
        @ConfigProperty(value = "tlsCrt.path", defaultValue = "hedera.crt") String tlsCrtPath,
        @ConfigProperty(value = "tlsKey.path", defaultValue = "hedera.key") String tlsKeyPath) {
}
