/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.metrics.LongGauge;
import com.swirlds.metrics.MetricConfig;

/**
 * A no-op implementation of a long gauge.
 */
public class NoOpLongGauge extends AbstractNoOpMetric implements LongGauge {

    public NoOpLongGauge(final MetricConfig<?, ?> config) {
        super(config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long get() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void set(final long newValue) {}
}
