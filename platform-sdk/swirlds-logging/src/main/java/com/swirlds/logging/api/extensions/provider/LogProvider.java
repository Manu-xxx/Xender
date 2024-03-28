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

package com.swirlds.logging.api.extensions.provider;

import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.event.LogEventFactory;
import com.swirlds.logging.api.internal.LoggingSystem;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A log provider that can be used to provide log events from custom logging implementations. An example would be an
 * implementation that converts Log4J events to our {@link LogEvent} format and than forwards them to our logging.
 *
 * <p>
 * Log providers are created by {@link LogProviderFactory} instances. The factory uses SPI.
 *
 * @see LogProviderFactory
 */
public interface LogProvider {

    /**
     * Checks if the log provider is active. If the log provider is not active, it will not be used. This can be used to
     * disable a log provider without removing it from the configuration. The current logging implementation checks that
     * state at startup and not for every log event.
     *
     * @return true if the log provider is active, false otherwise
     */
    boolean isActive();

    /**
     * Returns the name of the log provider.
     *
     * @return the name of the log provider
     */
    @NonNull
    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * Installs the log event consumer. The log provider should forward all log events to the consumer.
     *
     * @param logEventConsumer the log event consumer
     */
    void install(@NonNull LogEventFactory logEventFactory, @NonNull LoggingSystem logEventConsumer);
}
