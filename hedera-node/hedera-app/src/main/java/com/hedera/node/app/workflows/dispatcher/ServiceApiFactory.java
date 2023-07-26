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

package com.hedera.node.app.workflows.dispatcher;

import static com.hedera.node.app.service.token.impl.api.TokenServiceApiProvider.TOKEN_SERVICE_API_PROVIDER;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.api.ServiceApiProvider;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * A factory for creating service APIs.
 */
public class ServiceApiFactory {
    private final SavepointStackImpl stack;

    private static final Map<Class<?>, ServiceApiProvider<?>> API_PROVIDER =
            Map.of(TokenServiceApi.class, TOKEN_SERVICE_API_PROVIDER);

    public ServiceApiFactory(@NonNull final SavepointStackImpl stack) {
        this.stack = requireNonNull(stack);
    }

    public <C> C getApi(@NonNull final Class<C> apiInterface) throws IllegalArgumentException {
        requireNonNull(apiInterface);
        final var provider = API_PROVIDER.get(apiInterface);
        if (provider != null) {
            final var config = stack.peek().configuration();
            final var writableStates = stack.createWritableStates(provider.serviceName());
            final var api = provider.newInstance(config, writableStates);
            assert apiInterface.isInstance(api); // This needs to be ensured while apis are registered
            return apiInterface.cast(api);
        }
        throw new IllegalArgumentException("No provider of the given API is available");
    }
}
