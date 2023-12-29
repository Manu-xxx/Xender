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

package com.swirlds.base.time.internal;

import com.swirlds.base.time.Time;
import com.swirlds.base.time.TimeSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.time.ZoneId;

/**
 * An implementation of {@link Time} that will return the true wall clock time (according to the OS).
 * @deprecated use {@link TimeSource} or {@link InstantSource} instead
 */
@Deprecated
public final class OSTime implements Time {

    private static final OSTime instance = new OSTime();

    private OSTime() {}

    /**
     * Get a static instance of a standard time implementation.
     */
    @NonNull
    public static Time getInstance() {
        return instance;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long nanoTime() {
        return System.nanoTime();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public Instant instant() {
        return Instant.now();
    }

    @Override
    public long millis() {
        return System.currentTimeMillis();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return InstantSource.system().withZone(zone);
    }
}
