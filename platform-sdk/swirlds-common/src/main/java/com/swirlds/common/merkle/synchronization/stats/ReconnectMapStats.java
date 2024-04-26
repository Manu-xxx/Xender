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

package com.swirlds.common.merkle.synchronization.stats;

/**
 * An interface that helps gather statistics about the reconnect of tree-like data structures, such as VirtualMaps.
 * <p>
 * An implementation could gather aggregate statistics for all maps, or it could gather the counters for a specific
 * map and then also optionally delegate to another instance of the interface that would compute aggregate stats
 * for all maps.
 * <p>
 * All the methods have default no-op implementations to help with stubbing of the instances until an implementation
 * is ready, or in tests.
 */
public interface ReconnectMapStats {
    /**
     * Increment a transfers counter.
     * <p>
     * Different reconnect algorithms may define the term "transfer" differently. Examples of a transfer: <br>
     * * a lesson from the teacher, <br>
     * * a query response to the teacher, <br>
     * * a request from the learner, <br>
     * * a response from the teacher.
     */
    default void incrementTransfers() {}

    /**
     * Gather stats about internal nodes transfers.
     * @param num the number of hashes of internal nodes transferred
     * @param cleanNum the number of hashes of internal nodes transferred unnecessarily because they were clean
     */
    default void incrementInternalNodes(int num, int cleanNum) {}

    /**
     * Gather stats about leaf nodes transfers.
     * @param hashNum the number of hashes of leaf nodes transferred
     * @param dataNum the number of data payloads of leaf nodes transferred
     * @param cleanDataNum the number of data payloads transferred unnecessarily because they were clean
     */
    default void incrementLeafNodes(int hashNum, int dataNum, int cleanDataNum) {}
}
