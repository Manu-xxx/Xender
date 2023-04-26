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

package com.hedera.node.app.workflows.prehandle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of {@link PreHandleContext} (which will become an interface soon).
 */
public class PreHandleContextImpl extends PreHandleContext {

    private final ReadableStoreFactory storeFactory;

    public PreHandleContextImpl(@NonNull final ReadableStoreFactory storeFactory, @NonNull final TransactionBody txn)
            throws PreCheckException {
        super(storeFactory.createStore(ReadableAccountStore.class), txn);
        this.storeFactory = requireNonNull(storeFactory, "The supplied argument 'storeFactory' must not be null.");
    }

    @Override
    @NonNull
    public <C> C createStore(@NonNull Class<C> storeInterface) {
        return storeFactory.createStore(storeInterface);
    }
}
