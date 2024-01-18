/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.workflows.prehandle;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.WarmupContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

public class WarmupContextImpl implements WarmupContext {

    @NonNull
    private final TransactionInfo txInfo;
    @NonNull
    private final ReadableStoreFactory storeFactory;

    public WarmupContextImpl(@NonNull final  TransactionInfo txInfo, @NonNull final ReadableStoreFactory storeFactory) {
        this.txInfo = txInfo;
        this.storeFactory = storeFactory;
    }

    @NonNull
    @Override
    public TransactionBody body() {
        return txInfo.txBody();
    }

    @NonNull
    @Override
    public <C> C createStore(@NonNull final Class<C> storeInterface) {
        return storeFactory.getStore(storeInterface);
    }
}
