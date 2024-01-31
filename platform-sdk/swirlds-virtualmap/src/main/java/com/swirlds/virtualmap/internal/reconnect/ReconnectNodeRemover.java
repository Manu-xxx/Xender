/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.reconnect;

import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/**
 * During reconnect, information about all existing nodes is sent from the teacher to the learner. However,
 * the leaner may have old nodes in its virtual tree, and these nodes aren't transferred from the teacher.
 * This class is used to identify such stale nodes in the learner tree. The list of nodes to remove is then
 * used during flushes that happen periodically during reconnects (during hashing).
 *
 * <p>Node remover gets notifications about internal and leaf nodes received from the teacher during reconnect
 * and checks what nodes were in the learner (original) virtual tree at the corresponding paths. For example,
 * if an internal node is received for a path from the teacher, but it was a leaf node on the learner, the
 * key (that corresponds to that leaf) needs to removed. Other cases are handled in a similar way.
 *
 * <p>One particular case is complicated. Assume the learner tree has a key K at path N, and the teacher has
 * the same key at path M, and M &lt; N, while at path N the teacher has a different key L. During reconnects the
 * path M is processed first. At this step, some old key is marked for removal. Some time later path N is
 * processed, and this time key K is marked for removal, but this is wrong as it's still a valid key, just at
 * a different path. To handle this case, during flushes we check all leaf candidates for removal, where in
 * the tree they are located. In the scenario above, when path M was processed, key location was changed from N
 * to M. Later during flush, key K is in the list of candidates to remove, but with path N (this is where it
 * was originally located in the learner tree). Since the path is different, the leaf will not be actually
 * removed from disk.
 *
 * @param <K>
 * 		the type of the key
 * @param <V>
 * 		the type of the value
 */
public class ReconnectNodeRemover<K extends VirtualKey, V extends VirtualValue> {

    /**
     * The first leaf path of the new tree being received.
     */
    private long newFirstLeafPath;

    /**
     * The last leaf path of the new tree being received.
     */
    private long newLastLeafPath;

    /**
     * Can be used to access the tree being constructed.
     */
    private final RecordAccessor<K, ?> oldRecords;

    /**
     * The first leaf path of the original merkle tree.
     */
    private final long oldFirstLeafPath;

    /**
     * The last leaf path of the original merkle tree.
     */
    private final long oldLastLeafPath;

    private Set<VirtualLeafRecord<K, ?>> leavesToDelete = new HashSet<>();

    /**
     * Create an object responsible for removing virtual map nodes during a reconnect.
     *
     * @param oldRecords
     * 		a record accessor for the original map from before the reconnect
     * @param oldFirstLeafPath
     * 		the original first leaf path from before the reconnect
     * @param oldLastLeafPath
     * 		the original last leaf path from before the reconnect
     */
    public ReconnectNodeRemover(
            final RecordAccessor<K, ?> oldRecords, final long oldFirstLeafPath, final long oldLastLeafPath) {
        this.oldRecords = oldRecords;
        this.oldFirstLeafPath = oldFirstLeafPath;
        this.oldLastLeafPath = oldLastLeafPath;
    }

    /**
     * Set the first/last leaf path for the tree as it will be after reconnect is completed. Expected
     * to be called before the first node is passed to this object.
     *
     * @param newFirstLeafPath
     * 		the first leaf path after reconnect completes
     * @param newLastLeafPath
     * 		the last leaf path after reconnect completes
     */
    public void setPathInformation(final long newFirstLeafPath, final long newLastLeafPath) {
        this.newFirstLeafPath = newFirstLeafPath;
        this.newLastLeafPath = newLastLeafPath;
    }

    /**
     * Register the receipt of a new internal node. If the internal node is in the position that was
     * formally occupied by a leaf node then this method will ensure that the old leaf node is properly
     * deleted.
     *
     * @param path
     * 		the path of the node
     */
    public void newInternalNode(final long path) {
        if ((path >= oldFirstLeafPath) && (path <= oldLastLeafPath) && (path > 0)) {
            final VirtualLeafRecord<K, ?> oldRecord = oldRecords.findLeafRecord(path, false);
            assert oldRecord != null;
            leavesToDelete.add(oldRecord);
        }
    }

    /**
     * Register the receipt of a new leaf node. If the leaf node is in the position that was formally occupied by
     * a leaf node then this method will ensure that the old leaf node is properly deleted.
     *
     * @param path
     * 		the path to the node
     * @param newKey
     * 		the key of the new leaf node
     */
    public void newLeafNode(final long path, final K newKey) {
        final VirtualLeafRecord<K, ?> oldRecord = oldRecords.findLeafRecord(path, false);
        if ((oldRecord != null) && !newKey.equals(oldRecord.getKey())) {
            leavesToDelete.add(oldRecord);
        }

        if ((path == newLastLeafPath) && (newLastLeafPath < oldLastLeafPath)) {
            for (long p = newLastLeafPath + 1; p <= oldLastLeafPath; p++) {
                final VirtualLeafRecord<K, ?> oldExtraLeafRecord = oldRecords.findLeafRecord(p, false);
                assert oldExtraLeafRecord != null || p < oldFirstLeafPath;
                if (oldExtraLeafRecord != null) {
                    leavesToDelete.add(oldExtraLeafRecord);
                }
            }
        }
    }

    /**
     * Return a stream of keys that require deletion.
     *
     * @return a stream of keys to be deleted. Only the key and path in these records
     * 		are populated, all other data is uninitialized.
     */
    public Stream<VirtualLeafRecord<K, V>> getRecordsToDelete() {
        final Stream<VirtualLeafRecord<K, V>> stream =
                leavesToDelete.stream().map(r -> new VirtualLeafRecord<>(r.getPath(), r.getKey(), null));
        // Don't use clear(), as it would affect the returned stream
        leavesToDelete = new HashSet<>();
        return stream;
    }
}
