/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.api.validation;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A constraint that validates a specific property
 *
 * @param <T> value type of the property
 */
@FunctionalInterface
public interface ConfigPropertyConstraint<T> {

    /**
     * Returns a violation if the check of the given property fails
     *
     * @param metadata metadata of the property that should be checked
     * @return a violation if the check fails or null
     */
    @Nullable
    ConfigViolation check(@NonNull final PropertyMetadata<T> metadata);
}
