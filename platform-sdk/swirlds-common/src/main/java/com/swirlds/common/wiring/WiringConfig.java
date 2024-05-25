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

package com.swirlds.common.wiring;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

// TODO we should read all of these settings in the platform builder, NOT inside the framework

/**
 * Configuration for the wiring framework.
 *
 * @param healthMonitorEnabled    whether the health monitor is enabled
 * @param hardBackpressureEnabled whether hard backpressure is enabled
 * @param defaultPoolMultiplier   used when calculating the size of the default platform fork join pool. Maximum
 *                                parallelism in this pool is calculated as max(1, (defaultPoolMultipler * [number of
 *                                processors] + defaultPoolConstant)).
 * @param defaultPoolConstant     used when calculating the size of the default platform fork join pool. Maximum
 *                                parallelism in this pool is calculated as max(1, (defaultPoolMultipler * [number of
 *                                processors] + defaultPoolConstant)). It is legal for this constant to be a negative
 *                                number.
 * @param healthThreshold         the amount of time a scheduler may be unhealthy before the platform is considered to
 *                                be unhealthy
 * @param healthLogPeriod         the minimum amount of time that must pass between health log messages for the same
 *                                scheduler
 */
@ConfigData("platform.wiring")
public record WiringConfig(
        @ConfigProperty(defaultValue = "true") boolean healthMonitorEnabled,
        @ConfigProperty(defaultValue = "false") boolean hardBackpressureEnabled,
        @ConfigProperty(defaultValue = "1.0") double defaultPoolMultiplier,
        @ConfigProperty(defaultValue = "0") int defaultPoolConstant,
        @ConfigProperty(defaultValue = "5s") Duration healthThreshold,
        @ConfigProperty(defaultValue = "10m") Duration healthLogPeriod) {}
