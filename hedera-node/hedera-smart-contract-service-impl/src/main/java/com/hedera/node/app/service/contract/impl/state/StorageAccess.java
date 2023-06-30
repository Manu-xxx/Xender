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

package com.hedera.node.app.service.contract.impl.state;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.tuweni.units.bigints.UInt256;

/**
 * Represents a storage access, which could be either a read or a mutation.
 *
 * <p>The type of access (read or mutation) is decided by whether the {@link StorageAccess#writtenValue}
 * field is null. If null, this access was just a read. Otherwise, it was a write.
 *
 * <p>Note we do not distinguish between the
 *
 * @param key the key of the access
 * @param value the value read or overwritten
 * @param writtenValue if not null, the overwriting value
 */
public record StorageAccess(@NonNull UInt256 key, @NonNull UInt256 value, @Nullable UInt256 writtenValue) {
    public StorageAccess {
        requireNonNull(key, "Key cannot be null");
        requireNonNull(value, "Current value cannot be null");
    }

    public static StorageAccess newRead(@NonNull UInt256 key, @NonNull UInt256 value) {
        return new StorageAccess(key, value, null);
    }

    public static StorageAccess newWrite(@NonNull UInt256 key, @NonNull UInt256 oldValue, @NonNull UInt256 newValue) {
        return new StorageAccess(key, oldValue, requireNonNull(newValue));
    }

    public boolean isRemoval() {
        return writtenValue != null && writtenValue.isZero() && !value.isZero();
    }

    public boolean isInsertion() {
        return writtenValue != null && !writtenValue.isZero() && value.isZero();
    }

    public boolean isReadOnly() {
        return writtenValue == null;
    }
}
