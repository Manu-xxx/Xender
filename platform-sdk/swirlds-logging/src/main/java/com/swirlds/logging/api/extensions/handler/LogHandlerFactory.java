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

package com.swirlds.logging.api.extensions.handler;

import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A factory that creates {@link LogHandler}s. The factory is used by the Java SPI to create log handlers.
 *
 * @see LogHandler
 * @see java.util.ServiceLoader
 */
public interface LogHandlerFactory {

    /**
     * Creates a new log handler.
     *
     * @param configuration the configuration
     * @return the log handler
     */
    @NonNull
    LogHandler create(@NonNull Configuration configuration);
}
