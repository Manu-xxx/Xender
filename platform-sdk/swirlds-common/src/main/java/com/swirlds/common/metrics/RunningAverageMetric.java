/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics;

import static com.swirlds.metrics.api.Metric.ValueType.MAX;
import static com.swirlds.metrics.api.Metric.ValueType.MIN;
import static com.swirlds.metrics.api.Metric.ValueType.STD_DEV;
import static com.swirlds.metrics.api.Metric.ValueType.VALUE;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.metrics.api.FloatFormats;
import com.swirlds.metrics.api.Metric;
import com.swirlds.metrics.api.MetricType;
import java.util.EnumSet;

/**
 * This class maintains a running average of some numeric value. It is exponentially weighted in time, with
 * a given half life. If it is always given the same value, then that value will be the average, regardless
 * of the timing.
 */
public interface RunningAverageMetric extends Metric {

    /**
     * {@inheritDoc}
     */
    @Override
    default MetricType getMetricType() {
        return MetricType.RUNNING_AVERAGE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default DataType getDataType() {
        return DataType.FLOAT;
    }

    /**
     * {@inheritDoc}
     */
    default EnumSet<ValueType> getValueTypes() {
        return EnumSet.of(VALUE, MAX, MIN, STD_DEV);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    Double get(final ValueType valueType);

    /**
     * Getter of the {@code halfLife}
     *
     * @return the {@code halfLife}
     */
    double getHalfLife();

    /**
     * Incorporate "value" into the running average. If it is the same on every call, then the average will
     * equal it, no matter how those calls are timed. If it has various values on various calls, then the
     * running average will weight the more recent ones more heavily, with a half life of halfLife seconds,
     * where halfLife was passed in when this object was instantiated.
     * <p>
     * If this is called repeatedly with a value of X over a long period, then suddenly all calls start
     * having a value of Y, then after half-life seconds, the average will have moved halfway from X to Y,
     * regardless of how often update was called, as long as it is called at least once at the end of that
     * period.
     *
     * @param value
     * 		the value to incorporate into the running average
     */
    void update(final double value);

    /**
     * Get the average of recent calls to recordValue(). This is an exponentially-weighted average of recent
     * calls, with the weighting by time, not by number of calls to recordValue().
     *
     * @return the running average as of the last time recordValue was called
     */
    double get();

    /**
     * Configuration of a {@link RunningAverageMetric}
     */
    final class Config extends PlatformMetricConfig<RunningAverageMetric, Config> {

        private final double halfLife;
        private final boolean useDefaultHalfLife;

        /**
         * Constructor of {@code RunningAverageMetric.Config}
         *
         * The {@code useDefaultHalfLife} determines whether the default {@code halfLife} value
         * (see {@link MetricsConfig#halfLife()}) should be used during the creation of a metric based on
         * this configuration. If set to {@code false}, the specific {@code halfLife} defined in this configuration will
         * be used instead.
         *
         * @param category
         * 		the kind of metric (stats are grouped or filtered by this)
         * @param name
         * 		a short name for the statistic
         * @throws IllegalArgumentException
         * 		if one of the parameters is {@code null} or consists only of whitespaces
         */
        public Config(final String category, final String name) {
            super(category, name, FloatFormats.FORMAT_11_3);
            this.halfLife = -1;
            this.useDefaultHalfLife = true;
        }

        private Config(
                final String category,
                final String name,
                final String description,
                final String unit,
                final String format,
                final double halfLife,
                final boolean useDefaultHalfLife) {

            super(category, name, description, unit, format);
            this.halfLife = halfLife;
            this.useDefaultHalfLife = useDefaultHalfLife;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RunningAverageMetric.Config withDescription(final String description) {
            return new RunningAverageMetric.Config(
                    getCategory(),
                    getName(),
                    description,
                    getUnit(),
                    getFormat(),
                    getHalfLife(),
                    isUseDefaultHalfLife());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RunningAverageMetric.Config withUnit(final String unit) {
            return new RunningAverageMetric.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    unit,
                    getFormat(),
                    getHalfLife(),
                    isUseDefaultHalfLife());
        }

        /**
         * Sets the {@link Metric#getFormat() Metric.format} in fluent style.
         *
         * @param format
         * 		the format-string
         * @return a new configuration-object with updated {@code format}
         * @throws IllegalArgumentException
         * 		if {@code format} is {@code null} or consists only of whitespaces
         */
        public RunningAverageMetric.Config withFormat(final String format) {
            return new RunningAverageMetric.Config(
                    getCategory(),
                    getName(),
                    getDescription(),
                    getUnit(),
                    format,
                    getHalfLife(),
                    isUseDefaultHalfLife());
        }

        /**
         * Getter of the {@code halfLife}.
         *
         * @return the {@code halfLife}
         */
        public double getHalfLife() {
            return halfLife;
        }

        /**
         * Getter of the {@code useDefaultHalfLife}.
         *
         * @return the {@code useDefaultHalfLife}
         */
        public boolean isUseDefaultHalfLife() {
            return useDefaultHalfLife;
        }

        /**
         * Fluent-style setter of the {@code halfLife}.
         *
         * @param halfLife
         * 		the {@code halfLife}
         * @return a new configuration-object with updated {@code halfLife}
         */
        public RunningAverageMetric.Config withHalfLife(final double halfLife) {
            return new RunningAverageMetric.Config(
                    getCategory(), getName(), getDescription(), getUnit(), getFormat(), halfLife, false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Class<RunningAverageMetric> getResultClass() {
            return RunningAverageMetric.class;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public RunningAverageMetric create(final PlatformMetricsFactory factory) {
            return factory.createRunningAverageMetric(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return new ToStringBuilder(this)
                    .appendSuper(super.toString())
                    .append("halfLife", halfLife)
                    .toString();
        }
    }
}
