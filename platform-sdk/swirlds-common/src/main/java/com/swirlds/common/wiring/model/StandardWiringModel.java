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

package com.swirlds.common.wiring.model;

import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.NO_OP;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.SEQUENTIAL;
import static com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType.SEQUENTIAL_THREAD;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.threading.locks.AutoClosableLock;
import com.swirlds.common.threading.locks.internal.AutoLock;
import com.swirlds.common.threading.locks.locked.Locked;
import com.swirlds.common.wiring.model.diagram.HyperlinkBuilder;
import com.swirlds.common.wiring.model.internal.monitor.HealthMonitor;
import com.swirlds.common.wiring.model.internal.standard.HeartbeatScheduler;
import com.swirlds.common.wiring.model.internal.standard.JvmAnchor;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.builders.internal.StandardTaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.internal.SequentialThreadTaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;

/**
 * A standard implementation of a wiring model suitable for production use.
 */
public class StandardWiringModel extends TraceableWiringModel {

    /**
     * The platform context.
     */
    private final PlatformContext platformContext;

    /**
     * Schedules heartbeats. Not created unless needed.
     */
    private HeartbeatScheduler heartbeatScheduler = null;

    /**
     * The scheduler that the health monitor runs on.
     */
    private final TaskScheduler<Duration> healthMonitorScheduler;

    /**
     * The input wire for the health monitor.
     */
    private final BindableInputWire<Instant, Duration> healthMonitorInputWire;

    /**
     * Thread schedulers need to have their threads started/stopped.
     */
    private final List<SequentialThreadTaskScheduler<?>> threadSchedulers = new ArrayList<>();

    /**
     * The default fork join pool, schedulers not explicitly assigned a pool will use this one.
     */
    private final ForkJoinPool defaultPool;

    /**
     * Used to protect access to the JVM anchor.
     */
    private final AutoClosableLock jvmExitLock = new AutoLock();

    /**
     * Used to prevent the JVM from prematurely exiting.
     */
    private JvmAnchor anchor;

    /**
     * Constructor.
     *
     * @param platformContext         the platform context
     * @param defaultPool             the default fork join pool, schedulers not explicitly assigned a pool will use
     *                                this one
     * @param healthMonitorEnabled    true if the health monitor should be enabled, false otherwise
     * @param hardBackpressureEnabled true if hard backpressure should be enabled, false otherwise
     */
    StandardWiringModel(
            @NonNull final PlatformContext platformContext,
            @NonNull final ForkJoinPool defaultPool,
            final boolean healthMonitorEnabled,
            final boolean hardBackpressureEnabled) {
        super(hardBackpressureEnabled);

        this.platformContext = Objects.requireNonNull(platformContext);
        this.defaultPool = Objects.requireNonNull(defaultPool);

        final TaskSchedulerBuilder<Duration> healthMonitorSchedulerBuilder = this.schedulerBuilder("HealthMonitor");
        healthMonitorSchedulerBuilder.withHyperlink(HyperlinkBuilder.platformCoreHyperlink(HealthMonitor.class));
        if (healthMonitorEnabled) {
            healthMonitorSchedulerBuilder
                    .withType(SEQUENTIAL)
                    .withUnhandledTaskMetricEnabled(true)
                    .withUnhandledTaskCapacity(500); // TODO configure
        } else {
            healthMonitorSchedulerBuilder.withType(NO_OP);
        }

        healthMonitorScheduler = healthMonitorSchedulerBuilder.build();
        healthMonitorInputWire = healthMonitorScheduler.buildInputWire("check system health");
        buildHeartbeatWire(Duration.ofMillis(100))
                .solderTo(healthMonitorInputWire); // TODO time should be configurable!
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public final <O> TaskSchedulerBuilder<O> schedulerBuilder(@NonNull final String name) {
        throwIfStarted();
        return new StandardTaskSchedulerBuilder<>(platformContext, this, name, defaultPool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public OutputWire<Instant> buildHeartbeatWire(@NonNull final Duration period) {
        throwIfStarted();
        return getHeartbeatScheduler().buildHeartbeatWire(period);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public OutputWire<Duration> getHealthMonitorWire() {
        return healthMonitorScheduler.getOutputWire();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public OutputWire<Instant> buildHeartbeatWire(final double frequency) {
        throwIfStarted();
        return getHeartbeatScheduler().buildHeartbeatWire(frequency);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preventJvmExit() {
        try (final Locked ignored = jvmExitLock.lock()) {
            if (anchor == null) {
                anchor = new JvmAnchor();
                anchor.start();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void permitJvmExit() {
        try (final Locked ignored = jvmExitLock.lock()) {
            if (anchor != null) {
                anchor.stop();
                anchor = null;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerScheduler(@NonNull final TaskScheduler<?> scheduler, @Nullable final String hyperlink) {
        super.registerScheduler(scheduler, hyperlink);
        if (scheduler.getType() == SEQUENTIAL_THREAD) {
            threadSchedulers.add((SequentialThreadTaskScheduler<?>) scheduler);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        throwIfStarted();
        markAsStarted();

        final HealthMonitor healthMonitor = new HealthMonitor(platformContext, schedulers);
        healthMonitorInputWire.bind(healthMonitor::checkSystemHealth);

        // We don't have to do anything with the output of these sanity checks.
        // The methods below will log errors if they find problems.
        checkForCyclicalBackpressure();
        checkForIllegalDirectSchedulerUsage();
        checkForUnboundInputWires();

        if (heartbeatScheduler != null) {
            heartbeatScheduler.start();
        }

        for (final SequentialThreadTaskScheduler<?> threadScheduler : threadSchedulers) {
            threadScheduler.start();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        throwIfNotStarted();
        if (heartbeatScheduler != null) {
            heartbeatScheduler.stop();
        }

        for (final SequentialThreadTaskScheduler<?> threadScheduler : threadSchedulers) {
            threadScheduler.stop();
        }

        permitJvmExit();
    }

    /**
     * Get the heartbeat scheduler, creating it if necessary.
     *
     * @return the heartbeat scheduler
     */
    @NonNull
    private HeartbeatScheduler getHeartbeatScheduler() {
        if (heartbeatScheduler == null) {
            heartbeatScheduler = new HeartbeatScheduler(this, platformContext.getTime(), "Heartbeat");
        }
        return heartbeatScheduler;
    }
}
