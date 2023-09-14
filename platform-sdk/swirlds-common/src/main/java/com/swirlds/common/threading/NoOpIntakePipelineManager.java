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

package com.swirlds.common.threading;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * A no-op implementation of {@link IntakePipelineManager}.
 */
public class NoOpIntakePipelineManager implements IntakePipelineManager {
    /**
     * {@inheritDoc}
     */
    @Override
    public void eventAddedToIntakePipeline(@Nullable NodeId eventSender) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void eventThroughIntakePipeline(@Nullable NodeId eventSender) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasUnprocessedEvents(@NonNull NodeId peer) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset() {
        // no-op
    }
}
