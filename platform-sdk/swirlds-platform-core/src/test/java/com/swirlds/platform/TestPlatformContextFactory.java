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

package com.swirlds.platform;

import static com.swirlds.common.config.ConfigUtils.scanAndRegisterAllConfigTypes;

import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.test.metrics.NoOpMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;

/**
 * A utility class for creating a {@link PlatformContext} for testing.
 */
public final class TestPlatformContextFactory {

    private TestPlatformContextFactory() {}

    /**
     * Create a new {@link PlatformContext} for testing.
     *
     * @return a new {@link PlatformContext} for testing
     */
    public static PlatformContext build() {
        final Configuration configuration =
                scanAndRegisterAllConfigTypes(ConfigurationBuilder.create()).build();
        return new DefaultPlatformContext(configuration, new NoOpMetrics(), CryptographyHolder.get());
    }
}
