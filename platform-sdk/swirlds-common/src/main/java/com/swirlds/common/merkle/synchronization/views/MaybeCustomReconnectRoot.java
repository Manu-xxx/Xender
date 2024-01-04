/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.synchronization.views;

/**
 * A node that is possibly the root of a tree with a custom reconnect root.
 */
public interface MaybeCustomReconnectRoot {

    /**
     * Return true if this node is the root of a subtree that has a custom view for reconnect. Nodes that return
     * true must implement the interface {@link CustomReconnectRoot}.
     *
     * @return true if the node has a custom view to be used during reconnect
     */
    default boolean hasCustomReconnectView() {
        return false;
    }
}
