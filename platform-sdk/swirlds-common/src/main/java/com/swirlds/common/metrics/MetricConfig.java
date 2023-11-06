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

package com.swirlds.common.metrics;

import com.swirlds.base.ArgumentUtils;
import com.swirlds.base.utility.ToStringBuilder;
import java.util.Objects;

/**
 * An instance of {@code MetricConfig} contains all configuration parameters needed to create a {@link Metric}.
 * <p>
 * This class is abstract and contains only common parameters. If you want to define the configuration for a specific
 * {@code Metric}, there are special purpose configuration objects (e.g. {@link Counter.Config}).
 * <p>
 * A {@code MetricConfig} should be used with {@link Metrics#getOrCreate(MetricConfig)} to create a new {@code Metric}
 * <p>
 * This class is immutable, changing a parameter creates a new instance.
 *
 * @param <T> the {@code Class} for which the configuration is
 */
@SuppressWarnings("removal")
public abstract class MetricConfig<T extends Metric, C extends MetricConfig<T, C>> {

    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private final String category;
    private final String name;

    private final String description;
    private final String unit;
    private final String format;

    /**
     * Constructor of {@code MetricConfig}
     *
     * @param category
     * 		the kind of metric (metrics are grouped or filtered by this)
     * @param name
     * 		a short name for the metric
     * @param format
     * 		a string that can be passed to String.format() to format the metric
     * @throws IllegalArgumentException
     * 		if one of the parameters is {@code null} or consists only of whitespaces
     */
    protected MetricConfig(
            final String category,
            final String name,
            final String description,
            final String unit,
            final String format) {

        this.category = ArgumentUtils.throwArgBlank(category, "category");
        this.name = ArgumentUtils.throwArgBlank(name, "name");
        this.description = ArgumentUtils.throwArgBlank(description, "description");
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                    "Description has " + description.length() + " characters, must not be longer than "
                            + MAX_DESCRIPTION_LENGTH + " characters: "
                            + description);
        }
        this.unit = Objects.requireNonNull(unit, "unit");
        this.format = ArgumentUtils.throwArgBlank(format, "format");
    }

    /**
     * Constructor of {@code MetricConfig}
     *
     * @param category
     * 		the kind of metric (metrics are grouped or filtered by this)
     * @param name
     * 		a short name for the metric
     * @param defaultFormat
     * 		a string that can be passed to String.format() to format the metric
     * @throws IllegalArgumentException
     * 		if one of the parameters is {@code null} or consists only of whitespaces
     */
    protected MetricConfig(final String category, final String name, final String defaultFormat) {
        this(category, name, name, "", defaultFormat);
    }

    /**
     * Getter of the {@link Metric#getCategory() Metric.category}
     *
     * @return the {@code category}
     */
    public String getCategory() {
        return category;
    }

    /**
     * Getter of the {@link Metric#getName() Metric.name}
     *
     * @return the {@code name}
     */
    public String getName() {
        return name;
    }

    /**
     * Getter of the {@link Metric#getDescription() Metric.description}
     *
     * @return the {@code description}
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the {@link Metric#getDescription() Metric.description} in fluent style.
     *
     * @param description
     * 		the description
     * @return a new configuration-object with updated {@code description}
     * @throws IllegalArgumentException
     * 		if {@code description} is {@code null}, too long or consists only of whitespaces
     */
    public abstract C withDescription(final String description);

    /**
     * Getter of the {@link Metric#getUnit() Metric.unit}
     *
     * @return the {@code unit}
     */
    public String getUnit() {
        return unit;
    }

    /**
     * Sets the {@link Metric#getUnit() Metric.unit} in fluent style.
     *
     * @param unit
     * 		the unit
     * @return a new configuration-object with updated {@code unit}
     * @throws IllegalArgumentException
     * 		if {@code unit} is {@code null}
     */
    public abstract C withUnit(final String unit);

    /**
     * Getter of the {@link Metric#getFormat() Metric.format}
     *
     * @return the format-{@code String}
     */
    public String getFormat() {
        return format;
    }

    /**
     * Class of the {@code Metric} that this configuration is meant for
     *
     * @return the {@code Class}
     */
    public abstract Class<T> getResultClass();

    /**
     * Create a {@code Metric} using the given {@link MetricsFactory}
     *
     * Implementation note: we use the double-dispatch pattern when creating a {@link Metric}. More details
     * can be found at {@link Metrics#getOrCreate(MetricConfig)}.
     *
     * @param factory
     * 		the {@code MetricFactory}
     * @return the new {@code Metric}-instance
     */
    public abstract T create(final MetricsFactory factory);

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("category", category)
                .append("name", name)
                .append("description", description)
                .append("unit", unit)
                .append("format", format)
                .append("resultClass", getResultClass())
                .toString();
    }

    public static Metric.DataType mapDataType(final Class<?> type) {
        if (Double.class.equals(type) || Float.class.equals(type)) {
            return Metric.DataType.FLOAT;
        }
        if (Number.class.isAssignableFrom(type)) {
            return Metric.DataType.INT;
        }
        if (Boolean.class.equals(type)) {
            return Metric.DataType.BOOLEAN;
        }
        return Metric.DataType.STRING;
    }
}
