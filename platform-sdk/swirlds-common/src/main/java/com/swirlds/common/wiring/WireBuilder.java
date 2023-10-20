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

package com.swirlds.common.wiring;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.metrics.extensions.FractionalTimer;
import com.swirlds.common.metrics.extensions.NoOpFractionalTimer;
import com.swirlds.common.wiring.counters.BackpressureObjectCounter;
import com.swirlds.common.wiring.counters.MultiObjectCounter;
import com.swirlds.common.wiring.counters.NoOpObjectCounter;
import com.swirlds.common.wiring.counters.ObjectCounter;
import com.swirlds.common.wiring.counters.StandardObjectCounter;
import com.swirlds.common.wiring.wires.ConcurrentWire;
import com.swirlds.common.wiring.wires.SequentialWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A builder for wires.
 *
 * @param <O> the output time of the wire (use {@link Void}) for a wire with no output type)
 */
public class WireBuilder<O> {

    private static final Logger logger = LogManager.getLogger(WireBuilder.class);

    public static final long UNLIMITED_CAPACITY = -1;

    private boolean concurrent = false;
    private final String name;
    private WireMetricsBuilder metricsBuilder;
    private long unhandledTaskCapacity = UNLIMITED_CAPACITY;
    private boolean flushingEnabled = false;
    private ObjectCounter onRamp;
    private ObjectCounter offRamp;
    private ForkJoinPool pool = ForkJoinPool.commonPool();
    private UncaughtExceptionHandler uncaughtExceptionHandler;

    private Duration sleepDuration = Duration.ofNanos(100);

    /**
     * Constructor.
     *
     * @param name the name of the wire. Used for metrics and debugging. Must be unique (not enforced by framework).
     *             Must only contain alphanumeric characters and underscores (enforced by framework).
     */
    WireBuilder(@NonNull final String name) {
        // The reason why wire names have a restricted character set is because downstream consumers of metrics
        // are very fussy about what characters are allowed in metric names.
        if (!name.matches("^[a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException(
                    "Wire name must only contain alphanumeric characters, underscores, and hyphens");
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Wire name must not be empty");
        }
        this.name = name;
    }

    /**
     * This is a convenience method for hinting to the compiler the generic type of this builder. This method is a no
     * op.
     *
     * @param clazz the class of the output type
     * @param <X>   the output type
     * @return this
     */
    @SuppressWarnings("unchecked")
    public <X> WireBuilder<X> withOutputType(@NonNull final Class<X> clazz) {
        return (WireBuilder<X>) this;
    }

    /**
     * Set whether the wire should be concurrent or not. Default false.
     *
     * @param concurrent true if the wire should be concurrent, false otherwise
     * @return this
     */
    @NonNull
    public WireBuilder<O> withConcurrency(boolean concurrent) {
        this.concurrent = concurrent;
        return this;
    }

    /**
     * Set the maximum number of permitted scheduled tasks in the wire. Default is unlimited.
     *
     * @param unhandledTaskCapacity the maximum number of permitted unhandled tasks on the wire
     * @return this
     */
    @NonNull
    public WireBuilder<O> withUnhandledTaskCapacity(final long unhandledTaskCapacity) {
        this.unhandledTaskCapacity = unhandledTaskCapacity;
        return this;
    }

    /**
     * Set whether the wire should enable flushing. Default false. Flushing a wire with this disabled will cause the
     * wire to throw an exception.
     *
     * @param requireFlushCapability true if the wire should require flush capability, false otherwise
     * @return this
     */
    @NonNull
    public WireBuilder<O> withFlushingEnabled(final boolean requireFlushCapability) {
        this.flushingEnabled = requireFlushCapability;
        return this;
    }

    /**
     * Specify an object counter that should be notified when data is added to the wire. This is useful for implementing
     * backpressure that spans multiple wires.
     *
     * @param onRamp the object counter that should be notified when data is added to the wire
     * @return this
     */
    @NonNull
    public WireBuilder<O> withOnRamp(@NonNull final ObjectCounter onRamp) {
        this.onRamp = Objects.requireNonNull(onRamp);
        return this;
    }

    /**
     * Specify an object counter that should be notified when data is removed from the wire. This is useful for
     * implementing backpressure that spans multiple wires.
     *
     * @param offRamp the object counter that should be notified when data is removed from the wire
     * @return this
     */
    @NonNull
    public WireBuilder<O> withOffRamp(@NonNull final ObjectCounter offRamp) {
        this.offRamp = Objects.requireNonNull(offRamp);
        return this;
    }

    /**
     * If a method needs to block, this is the amount of time that is slept while waiting for the needed condition.
     *
     * @param backpressureSleepDuration the length of time to sleep when backpressure needs to be applied
     * @return this
     */
    @NonNull
    public WireBuilder<O> withSleepDuration(@NonNull final Duration backpressureSleepDuration) {
        if (backpressureSleepDuration.isNegative()) {
            throw new IllegalArgumentException("Backpressure sleep duration must not be negative");
        }
        this.sleepDuration = backpressureSleepDuration;
        return this;
    }

    /**
     * Provide a builder for metrics. If none is provided then no metrics will be enabled.
     *
     * @param metricsBuilder the metrics builder
     * @return this
     */
    @NonNull
    public WireBuilder<O> withMetricsBuilder(@NonNull final WireMetricsBuilder metricsBuilder) {
        this.metricsBuilder = Objects.requireNonNull(metricsBuilder);
        return this;
    }

    /**
     * Provide a custom thread pool for this wire. If none is provided then the common fork join pool will be used.
     *
     * @param pool the thread pool
     * @return this
     */
    @NonNull
    public WireBuilder<O> withPool(@NonNull final ForkJoinPool pool) {
        this.pool = Objects.requireNonNull(pool);
        return this;
    }

    /**
     * Provide a custom uncaught exception handler for this wire. If none is provided then the default uncaught
     * exception handler will be used. The default handler will write a message to the log.
     *
     * @param uncaughtExceptionHandler the uncaught exception handler
     * @return this
     */
    @NonNull
    public WireBuilder<O> withUncaughtExceptionHandler(
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler) {
        this.uncaughtExceptionHandler = Objects.requireNonNull(uncaughtExceptionHandler);
        return this;
    }

    /**
     * Build an uncaught exception handler if one was not provided.
     *
     * @return the uncaught exception handler
     */
    @NonNull
    private UncaughtExceptionHandler buildUncaughtExceptionHandler() {
        if (uncaughtExceptionHandler != null) {
            return uncaughtExceptionHandler;
        } else {
            return (thread, throwable) ->
                    logger.error(EXCEPTION.getMarker(), "Uncaught exception in wire {}", name, throwable);
        }
    }

    private record Counters(@NonNull ObjectCounter onRamp, @NonNull ObjectCounter offRamp) {}

    /**
     * Combine two counters into one.
     *
     * @param innerCounter the counter needed for internal implementation details, or null if not needed
     * @param outerCounter the counter provided by the outer scope, or null if not provided
     * @return the combined counter, or a no op counter if both are null
     */
    @NonNull
    private static ObjectCounter combineCounters(
            @Nullable final ObjectCounter innerCounter, @Nullable final ObjectCounter outerCounter) {
        if (innerCounter == null) {
            if (outerCounter == null) {
                return NoOpObjectCounter.getInstance();
            } else {
                return outerCounter;
            }
        } else {
            if (outerCounter == null) {
                return innerCounter;
            } else {
                return new MultiObjectCounter(innerCounter, outerCounter);
            }
        }
    }

    /**
     * Figure out which counters to use for this wire (if any), constructing them if they need to be constructed.
     */
    @NonNull
    private Counters buildCounters() {
        final ObjectCounter innerCounter;

        if (unhandledTaskCapacity != UNLIMITED_CAPACITY) {
            innerCounter = new BackpressureObjectCounter(unhandledTaskCapacity, sleepDuration);
        } else if (metricsBuilder != null && metricsBuilder.isUnhandledTaskMetricEnabled()
                || (concurrent && flushingEnabled)) {
            innerCounter = new StandardObjectCounter(sleepDuration);
        } else {
            innerCounter = null;
        }

        return new Counters(combineCounters(innerCounter, onRamp), combineCounters(innerCounter, offRamp));
    }

    /**
     * Build a busy timer if enabled.
     *
     * @return the busy timer, or null if not enabled
     */
    @NonNull
    private FractionalTimer buildBusyTimer() {
        if (metricsBuilder == null || !metricsBuilder.isBusyFractionMetricEnabled()) {
            return NoOpFractionalTimer.getInstance();
        }
        if (concurrent) {
            throw new IllegalStateException("Busy fraction metric is not compatible with concurrent wires");
        }
        return metricsBuilder.buildBusyTimer();
    }

    /**
     * Build the wire.
     *
     * @return the wire
     */
    @NonNull
    public Wire<O> build() {
        final Counters counters = buildCounters();
        final FractionalTimer busyFractionTimer = buildBusyTimer();

        if (metricsBuilder != null) {
            metricsBuilder.registerMetrics(name, counters.onRamp());
        }

        if (concurrent) {
            return new ConcurrentWire<>(
                    name,
                    pool,
                    buildUncaughtExceptionHandler(),
                    counters.onRamp(),
                    counters.offRamp(),
                    flushingEnabled);
        } else {
            return new SequentialWire<>(
                    name,
                    pool,
                    buildUncaughtExceptionHandler(),
                    counters.onRamp(),
                    counters.offRamp(),
                    busyFractionTimer,
                    flushingEnabled);
        }
    }
}
