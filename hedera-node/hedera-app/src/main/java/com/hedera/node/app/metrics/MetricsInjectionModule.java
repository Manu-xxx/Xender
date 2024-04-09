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

package com.hedera.node.app.metrics;

import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.system.Platform;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

/** A Dagger module for providing dependencies based on {@link Metrics}. */
@Module
public interface MetricsInjectionModule {
    @Provides
    @Singleton
    static Metrics provideMetrics(@NonNull final Platform platform) {
        return platform.getContext().getMetrics();
    }

    @Binds
    StoreMetricsService bindStoreMetricsService(@NonNull StoreMetricsServiceImpl storeMetricsService);
}
