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

package com.swirlds.base.internal;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

public interface BaseExecutorObserver {

    void onTaskSubmitted(@NonNull String type, @NonNull String name);

    void onTaskStarted(@NonNull String type, @NonNull String name);

    void onTaskDone(@NonNull String type, @NonNull String name, @NonNull Duration duration);

    void onTaskFailed(@NonNull String type, @NonNull String name, @NonNull Duration duration);
}
