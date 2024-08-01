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

package com.swirlds.config.extensions;

import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility class for configuration operations.
 */
public class ConfigUtils {

    /**
     * Utility class constructor.
     */
    private ConfigUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Check if two configurations have exactly the same properties (key and value for each property must be equals).
     * Everything next to the properties is ignored in this check.
     *
     * @param config1 the first configuration
     * @param config2 the second configuration
     * @return true if the two configurations have exactly the same properties, false otherwise
     */
    public static boolean haveEqualProperties(
            @NonNull final Configuration config1, @NonNull final Configuration config2) {
        Objects.requireNonNull(config1, "config1 must not be null");
        Objects.requireNonNull(config2, "config2 must not be null");

        final List<String> names1 = config1.getPropertyNames().collect(Collectors.toList());
        final List<String> names2 = config2.getPropertyNames().collect(Collectors.toList());
        if (names1.size() != names2.size()) {
            return false;
        }
        return names1.stream()
                        .filter(name -> names2.contains(name))
                        .filter(name -> Objects.equals(config1.getValue(name), config2.getValue(name)))
                        .count()
                == names1.size();
    }
}
