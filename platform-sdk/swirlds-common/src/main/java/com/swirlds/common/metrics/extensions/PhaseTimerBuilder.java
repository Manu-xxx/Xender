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

import static com.swirlds.common.units.TimeUnit.UNIT_MICROSECONDS;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.units.TimeUnit;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * A fluent style builder for a {@link PhaseTimer}.
 */
public class PhaseTimerBuilder<T extends Enum<T>> {

    private final PlatformContext platformContext;
    private final Time time;
    private final Class<T> clazz;
    private final Set<T> phases;
    private final String metricsCategory;

    private String metricsNamePrefix;
    private T initialPhase;
    private boolean fractionMetricsEnabled = false;
    private boolean absoluteTimeMetricsEnabled = false;
    private TimeUnit absoluteTimeUnit = UNIT_MICROSECONDS;

    /**
     * Create a new {@link PhaseTimerBuilder} instance.
     *
     * @param platformContext the platform context
     * @param time            the time provider
     * @param metricsCategory the metrics category
     * @param clazz           the enum class that describes the phases
     */
    public PhaseTimerBuilder(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final String metricsCategory,
            @NonNull final Class<T> clazz) {

        this.clazz = Objects.requireNonNull(clazz);
        this.platformContext = Objects.requireNonNull(platformContext);
        this.time = Objects.requireNonNull(time);
        this.metricsCategory = Objects.requireNonNull(metricsCategory);
        this.phases = EnumSet.allOf(Objects.requireNonNull(clazz));
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("The enum class " + clazz.getName() + " has zero values.");
        }
        this.initialPhase = phases.iterator().next();
    }

    /**
     * Build the {@link PhaseTimer} instance.
     *
     * @return the {@link PhaseTimer} instance
     */
    @NonNull
    public PhaseTimer<T> build() {
        return new PhaseTimer<>(this);
    }

    /**
     * Set the prefix for the metrics names created by this object. If not set, a default is generated.
     *
     * @param metricsNamePrefix the prefix for the metrics names created by this object
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> setMetricsNamePrefix(@NonNull final String metricsNamePrefix) {
        this.metricsNamePrefix = Objects.requireNonNull(metricsNamePrefix);
        return this;
    }

    /**
     * Set the initial phase. If not set, the phase with ordinal 0 is used.
     *
     * @param initialPhase the initial phase
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> setInitialPhase(@NonNull final T initialPhase) {
        this.initialPhase = initialPhase;
        return this;
    }

    /**
     * Enable fractional metrics. Disabled by default.
     *
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> enableFractionalMetrics() {
        this.fractionMetricsEnabled = true;
        return this;
    }

    /**
     * Enable absolute time metrics. Disabled by default.
     *
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> enableAbsoluteTimeMetrics() {
        this.absoluteTimeMetricsEnabled = true;
        return this;
    }

    /**
     * Set the unit for absolute time metrics. If not set, microseconds are used.
     *
     * @param absoluteTimeUnit the unit for absolute time metrics
     * @return this
     */
    @NonNull
    public PhaseTimerBuilder<T> setAbsoluteTimeUnit(@NonNull final TimeUnit absoluteTimeUnit) {
        this.absoluteTimeUnit = Objects.requireNonNull(absoluteTimeUnit);
        return this;
    }

    /**
     * Get the platform context.
     *
     * @return the platform context
     */
    @NonNull
    PlatformContext getPlatformContext() {
        return platformContext;
    }

    /**
     * Get the time provider.
     *
     * @return the time provider
     */
    @NonNull
    Time getTime() {
        return time;
    }

    /**
     * Get the set of phases.
     *
     * @return the set of phases
     */
    @NonNull
    Set<T> getPhases() {
        return phases;
    }

    /**
     * Get the metrics category.
     *
     * @return the metrics category
     */
    @NonNull
    String getMetricsCategory() {
        return metricsCategory;
    }

    /**
     * Get the metrics name prefix.
     *
     * @return the metrics name prefix
     */
    @NonNull
    String getMetricsNamePrefix() {
        if (metricsNamePrefix == null) {
            // No metrics prefix provided, generate a default.
            return clazz.getSimpleName();
        }
        return metricsNamePrefix;
    }

    /**
     * Get the initial phase.
     *
     * @return the initial phase
     */
    @NonNull
    T getInitialPhase() {
        return initialPhase;
    }

    /**
     * Get whether fractional metrics are enabled.
     *
     * @return whether fractional metrics are enabled
     */
    boolean areFractionMetricsEnabled() {
        return fractionMetricsEnabled;
    }

    /**
     * Get whether absolute time metrics are enabled.
     *
     * @return whether absolute time metrics are enabled
     */
    boolean areAbsoluteTimeMetricsEnabled() {
        return absoluteTimeMetricsEnabled;
    }

    /**
     * Get the unit for absolute time metrics.
     *
     * @return the unit for absolute time metrics
     */
    @NonNull
    TimeUnit getAbsoluteTimeUnit() {
        return absoluteTimeUnit;
    }
}
