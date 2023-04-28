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

package com.hedera.node.app.service.network;

import com.swirlds.common.crypto.RunningHash;

/**
 * Provides access to the underlying running hash leaf in state.
 */
public interface ReadableRunningHashLeafStore {
    /**
     * Get the n-3 record's running hash
     * @return the n-3 record's running hash
     */
    RunningHash getNMinusThreeRunningHash();
}
