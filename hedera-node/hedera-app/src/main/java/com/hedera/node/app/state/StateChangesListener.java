/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state;

import com.hedera.hapi.block.stream.output.NewStateType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * An interface responsible for observing any state changes occurred on state
 * and some additional helper methods
 */
public interface StateChangesListener {
    /**
     * The types of data structures whose changes can be listened to.
     */
    enum DataType {
        MAP,
        QUEUE,
        SINGLETON,
        SCHEMA
    }

    /**
     * The target data types that the listener is interested in.
     * @return the target data types
     */
    Set<DataType> targetDataTypes();

    /**
     * Save the state change when an entry is added in to a map.
     *
     * @param label The label of the map
     * @param key The key added to the map
     * @param value The value added to the map
     * @param <K> The type of the key
     * @param <V> The type of the value
     */
    default <K, V> void mapUpdateChange(@NonNull final String label, @NonNull final K key, @NonNull final V value) {}

    /**
     * Save the state change when an entry is removed from a map.
     *
     * @param label The label of the map
     * @param key The key removed from the map
     * @param <K> The type of the key
     */
    default <K> void mapDeleteChange(@NonNull final String label, @NonNull final K key) {}

    /**
     * Save the state change when a value is added to a queue
     *
     * @param label The label of the queue
     * @param value The value added to the queue
     * @param <V> The type of the value
     */
    default <V> void queuePushChange(@NonNull final String label, @NonNull final V value) {}

    /**
     * Save the state change when a value is removed from a queue
     *
     * @param label The label of the queue
     */
    default void queuePopChange(@NonNull final String label) {}

    /**
     * Save the state change when the value of a singleton is written.
     *
     * @param label The label of the singleton
     * @param value The value of the singleton
     * @param <V> The type of the value
     */
    default <V> void singletonUpdateChange(@NonNull final String label, @NonNull final V value) {}

    /**
     * Addition of a new state.
     * This may be a singleton, virtual map, or queue state.
     *
     * @param stateName The name of the new state
     * @param type The type of the new state
     */
    default <K, V> void schemaAddStateChange(@NonNull final String stateName, @NonNull final NewStateType type) {}

    /**
     *  Removal of an existing state.
     *  The entire singleton, virtual map, or queue state is removed,
     *  and not just the contents.
     *
     * @param stateName The name of the state to be removed
     */
    default <V> void schemaRemoveStateChange(@NonNull final String stateName) {}
}
