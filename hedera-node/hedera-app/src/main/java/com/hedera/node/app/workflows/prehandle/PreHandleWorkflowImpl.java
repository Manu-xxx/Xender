/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows.prehandle;

import com.swirlds.common.system.events.Event;
import javax.annotation.Nonnull;

/**
 * Dummy implementation. To be implemented by <a
 * href="https://github.com/hashgraph/hedera-services/issues/4210">#4210</a>.
 */
public final class PreHandleWorkflowImpl implements PreHandleWorkflow {
    @Override
    public void start(@Nonnull final Event event) {
        // To be implemented by Issue #4210
    }
}
