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

package com.swirlds.logging.legacy.json;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A filter that operates on log4j markers.
 */
public class HasMarkerFilter implements Predicate<JsonLogEntry> {

    private final Set<String> markers;

    public static HasMarkerFilter hasMarker(final String... markers) {
        Set<String> set = new HashSet<>();
        Collections.addAll(set, markers);
        return new HasMarkerFilter(set);
    }

    public static HasMarkerFilter hasMarker(List<String> markerNames) {
        return new HasMarkerFilter(new HashSet<>(markerNames));
    }

    public static HasMarkerFilter hasMarker(Set<String> markerNames) {
        return new HasMarkerFilter(markerNames);
    }

    /**
     * Create a filter that allows only certain markers to pass.
     *
     * @param markers
     * 		markers to be allowed by this filter
     */
    public HasMarkerFilter(final Set<String> markers) {
        this.markers = markers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean test(final JsonLogEntry entry) {
        if (markers == null) {
            return false;
        }
        return markers.contains(entry.getMarker());
    }
}
