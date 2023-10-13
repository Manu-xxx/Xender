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

package com.swirlds.common.scratchpad;

/**
 * Defines a {@link StandardScratchpad} type. Implementations must be enums.
 */
public interface ScratchpadType {

    /**
     * Get the field ID for this scratchpad type. Must be unique within this type.
     *
     * @return the field ID
     */
    int getFieldId();
}
