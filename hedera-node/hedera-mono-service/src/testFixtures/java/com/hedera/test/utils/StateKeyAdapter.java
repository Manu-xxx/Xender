/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.test.utils;

import com.hedera.node.app.spi.state.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Iterator;
import java.util.function.Function;

public class StateKeyAdapter<K1 extends Comparable<K1>, K2 extends Comparable<K2>, V>
        implements ReadableKVState<K2, V> {
    private final ReadableKVState<K1, V> delegate;
    private final Function<K2, K1> keyAdapter;

    public StateKeyAdapter(
            final ReadableKVState<K1, V> delegate, final Function<K2, K1> keyAdapter) {
        this.delegate = delegate;
        this.keyAdapter = keyAdapter;
    }

    @Override
    public String getStateKey() {
        return delegate.getStateKey();
    }

    @Override
    public boolean contains(@NonNull K2 key) {
        return ReadableKVState.super.contains(key);
    }

    @Override
    public V get(final K2 key) {
        return delegate.get(keyAdapter.apply(key));
    }

    @NonNull
    @Override
    public Iterator<K2> keys() {
        return null;
    }
}
