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

package com.swirlds.common.wiring.model.deterministic;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.internal.TraceableWiringModel;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A deterministic implementation of a wiring model. Suitable for testing, not intended for production use cases.
 */
public class DeterministicWiringModel extends TraceableWiringModel {

    private final PlatformContext platformContext;

    /**
     * Work that we will perform in the current cycle.
     */
    private List<Runnable> currentCycleWork = new ArrayList<>();

    /**
     * Work that we will perform in the next cycle.
     */
    private List<Runnable> nextCycleWork = new ArrayList<>();

    public DeterministicWiringModel(@NonNull final PlatformContext platformContext) {

        this.platformContext = Objects.requireNonNull(platformContext);
    }

    /**
     * Advance time by the given duration.
     *
     * @param duration the duration to advance time by
     */
    public void tick(@NonNull final Duration duration) {
        for (final Runnable work : currentCycleWork) {
            work.run();
        }

        // TODO send out heartbeats here

        currentCycleWork = nextCycleWork;
        nextCycleWork = new ArrayList<>();
    }

    /**
     * Submit a unit of work to be performed.
     *
     * @param work the work to be performed
     */
    private void submitWork(@NonNull final Runnable work) {
        // Work is never handled in the same cycle as when it is submitted.
        nextCycleWork.add(work);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public <O> TaskSchedulerBuilder<O> schedulerBuilder(@NonNull final String name) {
        return new DeterministicTaskSchedulerBuilder<>(platformContext, this, name, this::submitWork);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public OutputWire<Instant> buildHeartbeatWire(@NonNull final Duration period) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public OutputWire<Instant> buildHeartbeatWire(final double frequency) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void preventJvmExit() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void permitJvmExit() {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfStarted();
        markAsStarted();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotStarted();
    }
}
