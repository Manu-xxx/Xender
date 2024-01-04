/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.config;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.test.framework.config.TestConfigBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BasicConfigTest {

    @Test
    public void testDefaultValuesValid() {
        // given
        final ConfigurationBuilder builder = ConfigurationBuilder.create().withConfigDataType(BasicConfig.class);

        // then
        Assertions.assertDoesNotThrow(() -> builder.build(), "All default values of BasicConfig should be valid");
    }

    @Test
    void propertiesHasNoPrefix() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue(BasicConfig_.JVM_PAUSE_DETECTOR_SLEEP_MS, "42")
                .getOrCreateConfig();
        final BasicConfig basicConfig = configuration.getConfigData(BasicConfig.class);

        // then
        Assertions.assertEquals(42, basicConfig.jvmPauseDetectorSleepMs());
    }
}
