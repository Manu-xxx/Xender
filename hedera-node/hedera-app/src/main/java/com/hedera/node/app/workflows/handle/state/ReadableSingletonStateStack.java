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

package com.hedera.node.app.workflows.handle.state;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.state.ReadableSingletonState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class ReadableSingletonStateStack<T> implements ReadableSingletonState<T> {

    private final ReadableStatesStack readableStatesStack;
    private final String stateKey;

    public ReadableSingletonStateStack(
            @NonNull final ReadableStatesStack readableStatesStack, @NonNull final String stateKey) {
        this.readableStatesStack = requireNonNull(readableStatesStack, "readableStates must not be null");
        this.stateKey = requireNonNull(stateKey, "stateKey must not be null");
    }

    @NonNull
    private ReadableSingletonState<T> getCurrent() {
        return readableStatesStack.getCurrent().getSingleton(stateKey);
    }

    @NonNull
    @Override
    public String getStateKey() {
        return getCurrent().getStateKey();
    }

    @Nullable
    @Override
    public T get() {
        return getCurrent().get();
    }

    @Override
    public boolean isRead() {
        return getCurrent().isRead();
    }
}
