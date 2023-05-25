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

package com.swirlds.demo.iss;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedList;
import java.util.List;

/**
 * Config for ISS testing tool
 *
 * @param transactionsPerSecond the number of transactions per second that should be created (network wide)
 * @param plannedISSs           a list of {@link PlannedIss}s. If multiple ISS events are scheduled, it's important that
 *                              they be arranged in chronological order. Breaking this rule may cause undefined
 *                              behavior.
 * @param plannedLogError       a {@link PlannedLogError}
 */
@ConfigData("issTestingTool")
public record ISSTestingToolConfig(
        @ConfigProperty(defaultValue = "1000") int transactionsPerSecond,
        @ConfigProperty(defaultValue = "[]") List<String> plannedISSs,
        @ConfigProperty(defaultValue = "") String plannedLogError) {

    /**
     * Get the list of {@link PlannedIss}s
     *
     * @return a list of {@link PlannedIss}s
     */
    @NonNull
    public List<PlannedIss> getPlannedISSs() {
        final List<PlannedIss> parsedISSs = new LinkedList<>();

        for (final String plannedISSString : plannedISSs()) {
            parsedISSs.add(PlannedIss.fromString(plannedISSString));
        }

        return parsedISSs;
    }

    /**
     * Get the {@link PlannedLogError}, if one exists
     *
     * @return a {@link PlannedLogError}, or null if one doesn't exist
     */
    @Nullable
    public PlannedLogError getPlannedLogError() {
        if (plannedLogError == null || plannedLogError.isEmpty()) {
            return null;
        }

        return PlannedLogError.fromString(plannedLogError);
    }
}
