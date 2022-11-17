/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.node.app.spi.state.States;
import javax.annotation.Nonnull;

/**
 * A definition of an interface that will be implemented by each conceptual "service" like
 * crypto-service, token-service etc.,
 */
public interface Service {
    /**
     * Creates and returns a new {@link PreTransactionHandler}
     *
     * @param states the {@link States} the handler should use
     * @return A new {@link PreTransactionHandler}
     */
    @Nonnull
    PreTransactionHandler createPreTransactionHandler(@Nonnull States states);

    /**
     * Creates and returns a new {@link QueryHandler}
     *
     * @param states the {@link States} the handler should use
     * @return a new {@code QueryHandler}
     */
    @Nonnull
    QueryHandler createQueryHandler(@Nonnull States states);
}
