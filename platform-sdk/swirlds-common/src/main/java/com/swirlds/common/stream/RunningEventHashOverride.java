/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.stream;

import com.swirlds.common.crypto.Hash;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A record used to override the running event hash on various components when a new state is loaded (i.e. after a
 * reconnect or a restart).
 *
 * @param legacyRunningEventHash the legacy running event hash of the loaded state, used by the consensus event stream
 * @param runningEventHash       the running event hash of the loaded state
 * @param isReconnect            whether or not this is a reconnect state
 */
public record RunningEventHashOverride(
        @NonNull Hash legacyRunningEventHash, @NonNull Hash runningEventHash, boolean isReconnect) {}
