/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.state;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.workflows.handle.stack.TransactionStackImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class WritableStatesStack implements WritableStates {

    private final TransactionStackImpl stack;
    private final String statesName;

    public WritableStatesStack(TransactionStackImpl stack, String statesName) {
        this.stack = requireNonNull(stack, "stack must not be null");
        this.statesName = requireNonNull(statesName, "statesName must not be null");
    }

    @NonNull
    WritableStates getCurrent() {
        return stack.peek().state().getOrCreateWritableStates(statesName);
    }

    @Override
    @NonNull
    public <K, V> WritableKVState<K, V> get(@NonNull final String stateKey) {
        return new WritableKVStateStack<>(this, stateKey);
    }

    @Override
    @NonNull
    public <T> WritableSingletonState<T> getSingleton(@NonNull final String stateKey) {
        return new WritableSingletonStateStack<>(this, stateKey);
    }

    @Override
    public boolean contains(@NonNull final String stateKey) {
        return getCurrent().contains(stateKey);
    }

    @Override
    @NonNull
    public Set<String> stateKeys() {
        return getCurrent().stateKeys();
    }
}
