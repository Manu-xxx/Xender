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
package com.hedera.node.app.spi.key;

import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.virtualmap.VirtualValue;

import java.util.function.Consumer;

/** A replacement class for legacy {@link com.hedera.services.legacy.core.jproto.JKey}.
 * It represents different types of {@link Key}s supported in the codebase.*/
public interface HederaKey extends VirtualValue {
    boolean isPrimitive();
    boolean isEmpty();
    boolean isValid();
    default void visitPrimitiveKeys(final Consumer<HederaKey> actionOnSimpleKey) {
        actionOnSimpleKey.accept(this);
    }
}
