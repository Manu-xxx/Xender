/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sample;

import java.time.LocalDateTime;
import java.util.Objects;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class SomeService {

    private final String name;

    public SomeService(@NonNull final String name) {
        Objects.requireNonNull(name, "name cannot be null");
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @NonNull
    public LocalDateTime getLocalDateTime() {
        return LocalDateTime.now();
    }

    public boolean check(@NonNull final String value) {
        Objects.requireNonNull(value, "value cannot be null");
        return Objects.equals(value, "test");
    }

    public boolean checkNullable(@Nullable final String value) {
        return Objects.equals(value, "test");
    }
}
