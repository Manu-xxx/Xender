/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform;

import static com.swirlds.common.metrics.Metric.ValueType.VALUE;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.metrics.platform.Snapshot.SnapshotEntry;
import com.swirlds.common.threading.atomic.AtomicDouble;
import java.util.List;

/**
 * Platform-implementation of {@link DoubleGauge}
 */
public class DefaultDoubleGauge extends DefaultMetric implements DoubleGauge {

    private final AtomicDouble value;

    public DefaultDoubleGauge(final DoubleGauge.Config config) {
        super(config);
        value = new AtomicDouble(config.getInitialValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SnapshotEntry> takeSnapshot() {
        return List.of(new SnapshotEntry(VALUE, get()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double get() {
        return value.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(final double newValue) {
        this.value.set(newValue);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .appendSuper(super.toString())
                .append("value", value.get())
                .toString();
    }
}
