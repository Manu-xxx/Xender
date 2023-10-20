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

package com.swirlds.common.wiring.counters;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * This object counter combines multiple counters into a single counter. Every time a method on this object is called,
 * the same method is also called on all child counters.
 */
public class MultiObjectCounter extends ObjectCounter {

    private final ObjectCounter[] counters;

    /**
     * Constructor.
     *
     * @param counters one or more counters. The first counter in the array is the primary counter. {@link #getCount()}
     *                 always return the count of the primary counter. When {@link #attemptOnRamp()} is called,
     *                 on-ramping is attempted in the primary counter. If that fails, no other counter is on-ramped. If
     *                 that succeeds then on-ramping is forced in all other counters.
     */
    public MultiObjectCounter(@NonNull final ObjectCounter... counters) {
        this.counters = Objects.requireNonNull(counters);
        if (counters.length == 0) {
            throw new IllegalArgumentException("Must have at least one counter");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRamp() {
        for (final ObjectCounter counter : counters) {
            counter.onRamp();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptableOnRamp() throws InterruptedException {
        for (final ObjectCounter counter : counters) {
            counter.interruptableOnRamp();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean attemptOnRamp() {
        final boolean success = counters[0].attemptOnRamp();
        if (!success) {
            return false;
        }

        for (int i = 1; i < counters.length; i++) {
            counters[i].forceOnRamp();
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forceOnRamp() {
        for (final ObjectCounter counter : counters) {
            counter.forceOnRamp();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void offRamp() {
        for (final ObjectCounter counter : counters) {
            counter.offRamp();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCount() {
        return counters[0].getCount();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilEmpty() {
        for (final ObjectCounter counter : counters) {
            counter.waitUntilEmpty();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void interruptableWaitUntilEmpty() throws InterruptedException {
        for (final ObjectCounter counter : counters) {
            counter.interruptableWaitUntilEmpty();
        }
    }
}
