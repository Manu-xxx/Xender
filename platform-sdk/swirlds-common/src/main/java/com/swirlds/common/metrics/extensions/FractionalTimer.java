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

package com.swirlds.common.metrics.extensions;

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.FloatFormats;
import com.swirlds.common.metrics.FunctionGauge;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.time.IntegerEpochTime;
import com.swirlds.common.utility.ByteUtils;
import com.swirlds.common.utility.StackTrace;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.logging.LogMarker;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A utility that measures the fraction of time that is spent in one of two phases. For example, can be used to track
 * the overall busy time of a thread, or the busy time of a specific subtask. The granularity of this metric is in
 * microseconds.
 * <p>
 * This object must be measured at least once every 34 minutes or else it will overflow and return -1.
 * </p>
 */
public class FractionalTimer {
    private static final Logger logger = LogManager.getLogger(FractionalTimer.class);

    /**
     * passed to the accumulator method to indicate that work has started
     */
    private static final long WORK_START = 0;

    /**
     * passed to the accumulator method to indicate that work has ended
     */
    private static final long WORK_END = 1;

    /**
     * the initial value of status when the instance is created
     */
    private static final int INITIAL_STATUS = -1;

    /**
     * the value of start time when the metric has overflowed
     */
    private static final int OVERFLOW = -1;

    /**
     * if an error occurs, do not write a log statement more often than this
     */
    private static final Duration LOG_PERIOD = Duration.ofMinutes(5);

    /**
     * An instance that provides the current time
     */
    private final IntegerEpochTime time;

    /**
     * Used to atomically update and reset the time and status
     */
    private final AtomicLong accumulator;

    /**
     * limits the frequency of error log statements
     */
    private final RateLimitedLogger errorLogger;

    /**
     * A constructor where a custom {@link Time} instance could be supplied
     *
     * @param time provides the current time
     */
    public FractionalTimer(@NonNull final Time time) {
        this.time = new IntegerEpochTime(time);
        this.accumulator = new AtomicLong(ByteUtils.combineInts(this.time.getMicroTime(), INITIAL_STATUS));
        this.errorLogger = new RateLimitedLogger(logger, time, LOG_PERIOD);
    }

    /**
     * Registers a {@link FunctionGauge} that tracks the fraction of time that this object has been active (out of
     * 1.0).
     *
     * @param metrics     the metrics instance to add the metric to
     * @param category    the kind of {@code Metric} (metrics are grouped or filtered by this)
     * @param name        a short name for the {@code Metric}
     * @param description a one-sentence description of the {@code Metric}
     */
    public void registerMetric(
            @NonNull final Metrics metrics,
            @NonNull final String category,
            @NonNull final String name,
            @NonNull final String description) {
        metrics.getOrCreate(new FunctionGauge.Config<>(category, name, Double.class, this::getAndReset)
                .withDescription(description)
                .withUnit("fraction")
                .withFormat(FloatFormats.FORMAT_1_3));
    }

    /**
     * Notifies the metric that we are entering an active period.
     */
    public void activate() { // TODO method that takes time from the caller
        accumulator.accumulateAndGet(WORK_START, this::statusUpdate);
    }

    /**
     * Notifies the metric that we are entering an inactive period.
     */
    public void deactivate() { // TODO method that takes time from the caller
        accumulator.accumulateAndGet(WORK_END, this::statusUpdate);
    }

    /**
     * @return the fraction of time that this object has been active, where 0.0 means not at all active, and 1.0 means
     * that this object has been 100% active.
     */
    public double getActiveFraction() {
        final long pair = accumulator.get();
        return activeFraction(ByteUtils.extractLeftInt(pair), ByteUtils.extractRightInt(pair));
    }

    /**
     * Same as {@link #getActiveFraction()} but also resets the metric.
     *
     * @return the fraction of time that this object has been active, where 0.0 means this object is not at all active,
     * and 1.0 means that this object has been is 100% active.
     */
    public double getAndReset() {
        final long pair = accumulator.getAndUpdate(this::reset);
        return activeFraction(ByteUtils.extractLeftInt(pair), ByteUtils.extractRightInt(pair));
    }

    /**
     * Gets the fraction of time this object has been active since the last reset.
     *
     * @param measurementStart the micro epoch time when the last reset occurred
     * @param status           the current status of this object and the time spent in the opposite status
     * @return the fraction of time that this object has been active, where 0.0 means this object is not at all active,
     * and 1.0 means that this object is 100% active, or -1 if the metric has overflowed because it was not reset
     */
    private double activeFraction(final int measurementStart, final int status) {
        if (measurementStart < 0) {
            return OVERFLOW;
        }
        final int elapsedTime = time.microsElapsed(measurementStart);
        if (elapsedTime == 0) {
            return 0;
        }
        final int activeTime;
        if (isInactive(status)) {
            activeTime = Math.abs(status) - 1;
        } else {
            activeTime = elapsedTime - (status - 1);
        }
        return ((double) activeTime) / elapsedTime;
    }

    private boolean isInactive(final int status) {
        return status < 0;
    }

    private long statusUpdate(final long previousPair, final long statusChange) {
        // the epoch time when the last reset occurred
        final int measurementStart = ByteUtils.extractLeftInt(previousPair);
        // The current status is represented by the (+ -) sign. The number represents the time spent in the
        // opposite status. This is that its time spent being active/inactive can be deduced whenever the sample is
        // taken. If the time spent active is X, and the measurement time is Y, then inactive time is Y-X. Since zero
        // is neither positive nor negative, the values are offset by 1
        final int currentStatus = ByteUtils.extractRightInt(previousPair);
        // the current micro epoch time
        final int currentTime = time.getMicroTime();

        if ((statusChange == WORK_START && !isInactive(currentStatus))
                || (statusChange == WORK_END && isInactive(currentStatus))) {
            // this means that the metric has not been updated correctly, we will not change the value
            errorLogger.error(
                    LogMarker.EXCEPTION.getMarker(),
                    "FractionalTimer has been updated incorrectly. "
                            + "Current status: {}, status change: {}, stack trace: \n{}",
                    currentStatus,
                    statusChange,
                    StackTrace.getStackTrace().toString());
            return previousPair;
        }
        // the time elapsed since the last reset
        final int elapsedTime = IntegerEpochTime.elapsed(measurementStart, currentTime);
        // the time spent in the opposite status, either active or inactive
        final int statusTime = Math.abs(currentStatus) - 1;
        // the time spent inactive is all the elapsed time minus the time spent active
        // the time spent active is all the elapsed time minus the time spent inactive
        final int newTime = elapsedTime - statusTime;
        if (newTime < 0 || measurementStart < 0) {
            // this is an overflow because the metric was not reset, we are in a state where we can no longer track
            // the time spent inactive or active
            return ByteUtils.combineInts(OVERFLOW, statusChange == WORK_START ? 1 : -1);
        }
        if (statusChange == WORK_START) {
            // this means this was previously inactive and now started working
            return ByteUtils.combineInts(measurementStart, newTime + 1);
        }
        // this means the object was previously active and now stopped working
        return ByteUtils.combineInts(measurementStart, -newTime - 1);
    }

    private long reset(final long currentPair) {
        return ByteUtils.combineInts(time.getMicroTime(), resetStatus(ByteUtils.extractRightInt(currentPair)));
    }

    private int resetStatus(final int status) {
        if (status > 0) {
            return 1;
        }
        return -1;
    }
}
