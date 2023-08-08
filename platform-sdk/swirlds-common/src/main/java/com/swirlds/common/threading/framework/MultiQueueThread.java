/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.threading.framework;

import com.swirlds.common.utility.Clearable;

/**
 * Similar to a {@link QueueThread}, but can hold multiple types of data.
 */
public interface MultiQueueThread extends StoppableThread, Clearable {

    /**
     * Get the inserter for a particular data type.
     *
     * @param clazz the class of the data type
     * @return the inserter for the data type
     */
    <T> BlockingQueueInserter<T> getInserter(final Class<T> clazz);

    /**
     * Wait until this queue thread has handled all enqueued work and is no longer busy. This method may continue to
     * block indefinitely if new work is continuously added to the queue.
     *
     * @throws InterruptedException if this method is interrupted during execution
     */
    void waitUntilNotBusy() throws InterruptedException;

    /**
     * Get the number of items in the queue. Does not include items currently sitting in the buffer.
     *
     * @return the number of items in the queue
     */
    int size(); // TODO test
}
