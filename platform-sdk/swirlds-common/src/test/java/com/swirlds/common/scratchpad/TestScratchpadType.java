/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.scratchpad;

/**
 * A scratchpad type for testing.
 */
public enum TestScratchpadType implements ScratchpadType {
    FOO(0),
    BAR(1),
    BAZ(2);

    private final int fieldId;

    TestScratchpadType(final int fieldId) {
        this.fieldId = fieldId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getFieldId() {
        return fieldId;
    }
}
