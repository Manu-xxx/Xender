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

package com.hedera.node.app.info;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.info.CurrentPlatformStatus;
import com.swirlds.common.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation of {@link CurrentPlatformStatus} that delegates to the mono-service.
 */
public class MonoCurrentPlatformStatus implements CurrentPlatformStatus {

    private final com.hedera.node.app.service.mono.context.CurrentPlatformStatus delegate;

    /**
     * Constructs a {@link MonoCurrentPlatformStatus} with the given delegate.
     *
     * @param delegate the delegate
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public MonoCurrentPlatformStatus(@NonNull com.hedera.node.app.service.mono.context.CurrentPlatformStatus delegate) {
        this.delegate = requireNonNull(delegate);
    }

    @Override
    @NonNull
    public PlatformStatus get() {
        final var status = delegate.get();
        return status != null ? status : PlatformStatus.STARTING_UP;
    }
}
