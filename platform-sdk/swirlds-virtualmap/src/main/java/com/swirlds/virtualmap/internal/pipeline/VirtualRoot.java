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

package com.swirlds.virtualmap.internal.pipeline;

import com.swirlds.common.merkle.MerkleNode;
import java.nio.file.Path;

/**
 * The root of a merkle tree containing virtual nodes (i.e. nodes that can be flushed to disk).
 */
public interface VirtualRoot extends MerkleNode {

    /**
     * Check if this copy is a copy that has been designated for flushing. Once designated
     * as a flushable copy, this method should still return true after the flush has completed.
     *
     * All virtual roots enabled for flushing will eventually be flushed to disk. Some virtual
     * roots not explicitly enabled for flushing may also be flushed, for example, based on
     * memory consumption of the corresponding node cache.
     *
     * @return true if this copy should be flushed.
     */
    boolean requestedToFlush();

    /**
     * Flush the contents of this data structure to disk. Will be called at most once.
     *
     * This method is called only for the oldest released copy after it becomes immutable and before
     * it's fully evicted from memory (when released). Copies with {@link #requestedToFlush()}
     * returning true are guaranteed to be flushed, but other copies may be flushed, too.
     *
     * This method can be expensive and may block for a long time before returning.
     */
    void flush();

    /**
     * Check if this copy has already been flushed.
     *
     * @return true if this copy has been flushed
     */
    boolean isFlushed();

    /**
     * Block until this copy has been flushed.
     * May block forever if this copy returns false for {@link #requestedToFlush()}.
     *
     * @throws InterruptedException
     * 		if the calling thread is interrupted
     */
    void waitUntilFlushed() throws InterruptedException;

    default long estimatedSize() {
        return -1;
    }

    /**
     * Merge this copy into the next available newer copy. Will never be called on the mutable copy. Will not be called
     * if {@link #requestedToFlush()} returns true.
     */
    void merge();

    /**
     * Check if this copy has already been merged.
     *
     * @return true if this copy has been merged
     */
    boolean isMerged();

    /**
     * Check if the hash for this copy has already been computed.
     *
     * @return true if the hash has already been computed
     */
    boolean isHashed();

    /**
     * Compute the hash for this copy. Will be called at most once. Will not be called in parallel with respect
     * to other copies of the same object. Will always be called on round N-1 before round N. Will always be called
     * before this copy is flushed or merged.
     */
    void computeHash();

    /**
     * Prepares this copy so that it may be used even when removed from the pipeline. A detached copy is still part
     * of the pipeline until it is fully handled (merged or flushed). The pipeline will make sure any flushing
     * copy completes before calling this method, and will make sure no copy is being merged or flushed while
     * this method executes.
     *
     * @param destination
     * 		the location where files generated by the detachment will be located
     * @return a reference to the detached state
     */
    <T> T detach(final Path destination);

    /**
     * Gets whether this copy is detached.
     *
     * @return Whether this copy is detached.
     */
    boolean isDetached();

    /**
     * Check if this virtual root is registered to a given pipeline. Used for sanity checks.
     *
     * @param pipeline
     * 		the pipeline in question
     * @return true if this virtual root has been previously registered with the given pipeline
     */
    boolean isRegisteredToPipeline(final VirtualPipeline pipeline);

    /**
     * Called by the {@link VirtualPipeline} on the most recent remaining copy in the pipeline when the pipeline is
     * shut down gracefully, or due to some catastrophic failure.
     *
     * @param immediately
     * 		if true then the pipeline is being shut down immediately, without waiting for work to complete
     */
    void onShutdown(boolean immediately);

    /**
     * Gets this virtual root fast copy version. The version is increased every time a mutable
     * virtual root is copied.
     *
     * @return Fast copy version.
     */
    default long getFastCopyVersion() {
        return 0;
    }
}
