/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi;

import com.hedera.node.app.spi.state.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A definition of an interface that will be implemented by each conceptual "service" like crypto-service, token-service
 * etc.,
 */
public interface Service {

    /**
     * Registers the schemas this service uses when running with {@code mono-service} adapters
     * with the given {@link SchemaRegistry}. We can remove this method once we are no longer
     * relying on EET's with {@code workflows.enabled} to validate behavior.
     *
     * @deprecated because this method is only used when running with {@code mono-service} adapters
     * @param registry the registry to register the schemas with
     * */
    @Deprecated(forRemoval = true)
    void registerMonoAdapterSchemas(@NonNull SchemaRegistry registry);

    /**
     * Registers the schemas this service really uses with the given {@link SchemaRegistry}.
     *
     * @param registry the registry to register the schemas with
     */
    default void registerSchemas(@NonNull SchemaRegistry registry) {
        // No-op
    }
}
