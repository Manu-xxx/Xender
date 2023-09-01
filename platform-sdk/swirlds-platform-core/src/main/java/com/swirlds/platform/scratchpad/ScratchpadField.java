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

package com.swirlds.platform.scratchpad;

/**
 * Fields in the scratchpad.
 */
public enum ScratchpadField {
    /**
     * The current epoch hash. If this value is different than the epoch hash specified by the emergency recovery file
     * (if present) or the latest state (if recovery file is not present), then this indicates that the platform is
     * entering a new epoch. This value is updated after all cleanup and preparation for the new epoch has been
     * completed to ensure that preparation for a new epoch is performed exactly once regardless of crashes.
     */
    EPOCH_HASH(0);

    /**
     * The unique index of this field.
     */
    private final int index;

    /**
     * Create a new scratchpad field with the given index.
     *
     * @param index the unique index of this field
     */
    ScratchpadField(final int index) {
        this.index = index;
    }

    /**
     * Get the unique index of this field. Prevents the scratchpad file from being corrupted by changing the order of
     * the fields.
     *
     * @return the unique index of this field
     */
    public int getIndex() {
        return index;
    }
}
