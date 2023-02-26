/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.synchronization.settings;

import java.time.Duration;

/**
 * Utility class for fetching reconnect settings.
 *
 * @deprecated will be replaced by the {@link com.swirlds.config.api.Configuration} API in near future. If you need
 * 		to use this class please try to do as less static access as possible.
 */
@Deprecated(forRemoval = true)
public final class ReconnectSettingsFactory {

    private static ReconnectSettings reconnectSettings;

    private ReconnectSettingsFactory() {}

    public static void configure(ReconnectSettings reconnectSettings) {
        ReconnectSettingsFactory.reconnectSettings = reconnectSettings;
    }

    public static ReconnectSettings get() {
        if (reconnectSettings == null) {
            reconnectSettings = getDefaultSettings();
        }
        return reconnectSettings;
    }

    private static ReconnectSettings getDefaultSettings() {
        return new ReconnectSettings() {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public int getReconnectWindowSeconds() {
                return -1;
            }

            @Override
            public double getFallenBehindThreshold() {
                return 0.5;
            }

            @Override
            public int getAsyncStreamTimeoutMilliseconds() {
                return 10_000;
            }

            @Override
            public int getAsyncOutputStreamFlushMilliseconds() {
                return 100;
            }

            @Override
            public int getAsyncStreamBufferSize() {
                return 100_000;
            }

            @Override
            public int getMaxAckDelayMilliseconds() {
                return 10;
            }

            @Override
            public int getMaximumReconnectFailuresBeforeShutdown() {
                return 10;
            }

            @Override
            public Duration getMinimumTimeBetweenReconnects() {
                return Duration.ofMinutes(10);
            }
        };
    }
}
