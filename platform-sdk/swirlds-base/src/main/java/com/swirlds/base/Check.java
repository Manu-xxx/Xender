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

package com.swirlds.base;

import java.util.Objects;

/**
 * Class that contains common checks like null checks as static methods.
 */
public class Check {

    /**
     * Private constructor to prevent instantiation.
     *
     * @throws IllegalAccessException if the constructor is called
     */
    private Check() throws IllegalAccessException {
        throw new IllegalAccessException("Check should never be instantiated!");
    }

    /**
     * Throw an {@link NullPointerException} if the supplied argument is {@code null}.
     *
     * @param argument     the argument to check
     * @param argumentName the name of the argument
     */
    public static <T> T forNull(final T argument, final String argumentName) {
        return Objects.requireNonNull(
                argument, () -> String.format("The supplied argument '%s' cannot be null!", argumentName));
    }
}
