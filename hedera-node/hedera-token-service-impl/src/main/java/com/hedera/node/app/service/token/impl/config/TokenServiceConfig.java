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

package com.hedera.node.app.service.token.impl.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Provides configuration for the token service. Currently, it just has the maximum number of custom fees allowed.
 * In the future all the configurations needed by handlers in the token service will be added here.
 * @param maxCustomFeesAllowed the maximum number of custom fees allowed
 */
@ConfigData("token")
public record TokenServiceConfig(@ConfigProperty(defaultValue = "10") long maxCustomFeesAllowed) {}
