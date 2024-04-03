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

package com.hedera.node.app.service.mono.statedumpers.scheduledtransactions;

import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

@SuppressWarnings("java:S6218")
public record BBMScheduledSecondValue(NavigableMap<Instant, List<Long>> ids) {

    static BBMScheduledSecondValue fromMono(@NonNull final ScheduleSecondVirtualValue scheduleVirtualValue) {
        final var newMap = new TreeMap<Instant, List<Long>>();
        scheduleVirtualValue
                .getIds()
                .forEach((key, value) -> newMap.put(
                        key.toJava(), value.collect(Long::valueOf).stream().toList()));
        return new BBMScheduledSecondValue(newMap);
    }
}
