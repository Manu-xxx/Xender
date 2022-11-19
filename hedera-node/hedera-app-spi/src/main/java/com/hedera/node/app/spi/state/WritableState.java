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
package com.hedera.node.app.spi.state;

import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * A {@link ReadableState} who's values can be mutated.
 *
 * @param <K> The key, which must be of the appropriate kind depending on whether it is stored in
 *     memory, or on disk.
 * @param <V> The value, which must be of the appropriate kind depending on whether it is stored in
 *     memory, or on disk.
 */
public interface WritableState<K, V> extends ReadableState<K, V> {

    /**
     * Gets the value associated with the given key in a <strong>READ-WRITE</strong> way. The
     * returned {@link Optional} will be empty if the key does not exist in the store. If the value
     * did exist, but the expiration time has been exceeded, then the value will be unset in the
     * {@link Optional}.
     *
     * @param key The key. Cannot be null, otherwise an exception is thrown.
     * @return A non-null optional. It may be empty if there is no value for this associated key.
     * @throws NullPointerException if the key is null.
     */
    @Nonnull
    Optional<V> getForModify(@Nonnull K key);

    /**
     * Adds a new value to the store, or updates an existing value. It is generally preferred to use
     * {@link #getForModify(Object)} to get a writable value, and only use this method if the key
     * does not already exist in the store.
     *
     * @param key The key. Cannot be null.
     * @param value The value. Cannot be null.
     * @throws NullPointerException if the key or value is null.
     */
    void put(@Nonnull K key, @Nonnull V value);

    /**
     * Removes the given key and its associated value from the map. Subsequent calls to {@link
     * #contains(Object)} with the given key will return false, and subsequent calls to {@link
     * #get(Object)} and {@link #getForModify(Object)} will return empty optionals.
     *
     * @param key The key representing the key/value to remove. Cannot be null.
     * @throws NullPointerException if the key or value is null.
     */
    void remove(@Nonnull K key);

    /**
     * Gets a {@link Set} of all keys that have been modified through this {@link WritableState}.
     * Keys used with {@link #getForModify(Object)} and {@link #put(Object, Object)} and {@link
     * #remove(Object)} are included in this set.
     *
     * @return A non-null set of modified keys, which may be empty.
     */
    @Nonnull
    Set<K> modifiedKeys();
}
