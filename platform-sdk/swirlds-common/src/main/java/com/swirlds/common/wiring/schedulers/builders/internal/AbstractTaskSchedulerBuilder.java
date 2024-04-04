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

package com.swirlds.common.wiring.schedulers.builders.internal;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.model.internal.StandardWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerBuilder;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerConfiguration;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A builder for {@link TaskScheduler}s.
 *
 * @param <OUT> the output type of the primary output wire for this task scheduler (use {@link Void} for a scheduler with
 *            no output)
 */
public abstract class AbstractTaskSchedulerBuilder<OUT> implements TaskSchedulerBuilder<OUT> {

    private static final Logger logger = LogManager.getLogger(AbstractTaskSchedulerBuilder.class);

    protected final StandardWiringModel model;

    protected TaskSchedulerType type = TaskSchedulerType.SEQUENTIAL;
    protected final String name;
    protected long unhandledTaskCapacity = 1;
    protected boolean flushingEnabled = false;
    protected boolean squelchingEnabled = false;
    protected boolean externalBackPressure = false;
    protected ObjectCounter onRamp;
    protected ObjectCounter offRamp;
    protected ForkJoinPool pool;
    protected UncaughtExceptionHandler uncaughtExceptionHandler;
    protected String hyperlink;

    protected boolean unhandledTaskMetricEnabled = false;
    protected boolean busyFractionMetricEnabled = false;

    protected Duration sleepDuration = Duration.ofNanos(100);

    protected final PlatformContext platformContext;

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param model           the wiring model
     * @param name            the name of the task scheduler. Used for metrics and debugging. Must be unique. Must only
     *                        contain alphanumeric characters and underscores.
     * @param defaultPool     the default fork join pool, if none is provided then this pool will be used
     */
    public AbstractTaskSchedulerBuilder(
            @NonNull final PlatformContext platformContext,
            @NonNull final StandardWiringModel model,
            @NonNull final String name,
            @NonNull final ForkJoinPool defaultPool) {

        this.platformContext = Objects.requireNonNull(platformContext);
        this.model = Objects.requireNonNull(model);

        // The reason why wire names have a restricted character set is because downstream consumers of metrics
        // are very fussy about what characters are allowed in metric names.
        if (!name.matches("^[a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Illegal name: \"" + name
                    + "\". Task Schedulers name must only contain alphanumeric characters and underscores");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("TaskScheduler name must not be empty");
        }
        this.name = name;
        this.pool = Objects.requireNonNull(defaultPool);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> configure(@NonNull final TaskSchedulerConfiguration configuration) {
        if (configuration.type() != null) {
            withType(configuration.type());
        }
        if (configuration.unhandledTaskCapacity() != null) {
            withUnhandledTaskCapacity(configuration.unhandledTaskCapacity());
        }
        if (configuration.unhandledTaskMetricEnabled() != null) {
            withUnhandledTaskMetricEnabled(configuration.unhandledTaskMetricEnabled());
        }
        if (configuration.busyFractionMetricEnabled() != null) {
            withBusyFractionMetricsEnabled(configuration.busyFractionMetricEnabled());
        }
        if (configuration.flushingEnabled() != null) {
            withFlushingEnabled(configuration.flushingEnabled());
        }
        if (configuration.squelchingEnabled() != null) {
            withSquelchingEnabled(configuration.squelchingEnabled());
        }
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withType(@NonNull final TaskSchedulerType type) {
        this.type = Objects.requireNonNull(type);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withUnhandledTaskCapacity(final long unhandledTaskCapacity) {
        this.unhandledTaskCapacity = unhandledTaskCapacity;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withFlushingEnabled(final boolean requireFlushCapability) {
        this.flushingEnabled = requireFlushCapability;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withSquelchingEnabled(final boolean squelchingEnabled) {
        this.squelchingEnabled = squelchingEnabled;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withOnRamp(@NonNull final ObjectCounter onRamp) {
        this.onRamp = Objects.requireNonNull(onRamp);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withOffRamp(@NonNull final ObjectCounter offRamp) {
        this.offRamp = Objects.requireNonNull(offRamp);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withExternalBackPressure(final boolean externalBackPressure) {
        this.externalBackPressure = externalBackPressure;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withSleepDuration(@NonNull final Duration backpressureSleepDuration) {
        if (backpressureSleepDuration.isNegative()) {
            throw new IllegalArgumentException("Backpressure sleep duration must not be negative");
        }
        this.sleepDuration = backpressureSleepDuration;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withUnhandledTaskMetricEnabled(final boolean enabled) {
        this.unhandledTaskMetricEnabled = enabled;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withBusyFractionMetricsEnabled(final boolean enabled) {
        this.busyFractionMetricEnabled = enabled;
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withPool(@NonNull final ForkJoinPool pool) {
        this.pool = Objects.requireNonNull(pool);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withUncaughtExceptionHandler(
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public AbstractTaskSchedulerBuilder<OUT> withHyperlink(@Nullable final String hyperlink) {
        this.hyperlink = hyperlink;
        return this;
    }
}
