/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.noop.internal;

import com.swirlds.common.metrics.IntegerPairAccumulator;
import com.swirlds.metrics.api.MetricConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A no-op implementation of an integer pair accumulator.
 */
public class NoOpIntegerPairAccumulator<T> extends AbstractNoOpMetric implements IntegerPairAccumulator<T> {

    private final @NonNull T value;

    public NoOpIntegerPairAccumulator(final @NonNull MetricConfig<?, ?> config, final @NonNull T value) {
        super(config);
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public T get() {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getLeft() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getRight() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(final int leftValue, final int rightValue) {}

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public DataType getDataType() {
        return DataType.INT;
    }
}
