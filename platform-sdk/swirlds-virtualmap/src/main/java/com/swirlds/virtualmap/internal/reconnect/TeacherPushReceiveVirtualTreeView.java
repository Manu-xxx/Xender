/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;
import static com.swirlds.virtualmap.internal.Path.getLeftChildPath;
import static com.swirlds.virtualmap.internal.Path.getRightChildPath;

import com.swirlds.base.time.Time;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.merkle.synchronization.internal.Lesson;
import com.swirlds.common.merkle.synchronization.internal.QueryResponse;
import com.swirlds.common.merkle.synchronization.internal.TeacherReceivingThread;
import com.swirlds.common.merkle.synchronization.internal.TeacherSendingThread;
import com.swirlds.common.merkle.synchronization.internal.TeacherSubtree;
import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.merkle.synchronization.streams.AsyncOutputStream;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.TeacherTreeView;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.ConcurrentNodeStatusTracker;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import com.swirlds.virtualmap.internal.pipeline.VirtualPipeline;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An implementation of {@link TeacherTreeView} designed for virtual merkle trees.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public final class TeacherPushReceiveVirtualTreeView<K extends VirtualKey, V extends VirtualValue>
        extends VirtualTreeViewBase<K, V> implements TeacherTreeView<Long> {

    private static final Logger logger = LogManager.getLogger(TeacherPushReceiveVirtualTreeView.class);

    private final ReconnectConfig reconnectConfig;

    /**
     * A queue of the nodes (by path) that we are about to handle. Note that ConcurrentBitSetQueue
     * cleans up after itself in "chunks", such that we don't end up consuming a ton of memory.
     */
    private final ConcurrentBitSetQueue handleQueue = new ConcurrentBitSetQueue();

    /**
     * A queue of the nodes (by path) that we expect responses for.
     */
    private final ConcurrentBitSetQueue expectedResponseQueue = new ConcurrentBitSetQueue();

    /**
     * Keeps track of responses from learner about known, unknown, and not known nodes.
     */
    private final ConcurrentNodeStatusTracker nodeStatusTracker = new ConcurrentNodeStatusTracker(Long.MAX_VALUE);

    /**
     * The {@link RecordAccessor} used for accessing the original map state.
     */
    private RecordAccessor<K, V> records;

    /**
     * This latch counts down when the view is fully initialized and ready for use.
     */
    private final CountDownLatch ready = new CountDownLatch(1);

    /**
     * Create a new {@link TeacherPushReceiveVirtualTreeView}.
     *
     * @param threadManager
     * 		responsible for creating and managing threads
     * @param root
     * 		The root node on the teacher side of the saved state that we are going to reconnect.
     * @param state
     * 		The state of the virtual tree that we are synchronizing.
     * @param pipeline
     * 		The pipeline managing the virtual map.
     */
    public TeacherPushReceiveVirtualTreeView(
            final ThreadManager threadManager,
            final ReconnectConfig reconnectConfig,
            final VirtualRootNode<K, V> root,
            final VirtualStateAccessor state,
            final VirtualPipeline pipeline) {
        // There is no distinction between originalState and reconnectState in this implementation
        super(root, state, state);
        this.reconnectConfig = reconnectConfig;
        new ThreadConfiguration(threadManager)
                .setRunnable(() -> {
                    records = pipeline.detachCopy(root);
                    ready.countDown();
                })
                .setComponent("virtualmap")
                .setThreadName("detacher")
                .build()
                .start();
    }

    @Override
    public void startTeacherThreads(
            final Time time,
            final StandardWorkGroup workGroup,
            final MerkleDataInputStream inputStream,
            final MerkleDataOutputStream outputStream,
            final Queue<TeacherSubtree> subtrees) {
        final AsyncInputStream<QueryResponse> in =
                new AsyncInputStream<>(inputStream, workGroup, QueryResponse::new, reconnectConfig);
        final AsyncOutputStream<Lesson<Long>> out = new AsyncOutputStream<>(outputStream, workGroup, reconnectConfig);

        in.start();
        out.start();

        final AtomicBoolean senderIsFinished = new AtomicBoolean(false);

        final TeacherSendingThread<Long> teacherSendingThread =
                new TeacherSendingThread<>(time, reconnectConfig, workGroup, in, out, subtrees, this, senderIsFinished);
        teacherSendingThread.start();
        final TeacherReceivingThread<Long> teacherReceivingThread =
                new TeacherReceivingThread<>(workGroup, in, this, senderIsFinished);
        teacherReceivingThread.start();
    }

    @Override
    public ReconnectConfig getReconnectConfig() {
        return reconnectConfig;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitUntilReady() throws InterruptedException {
        ready.await();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getRoot() {
        return ROOT_PATH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addToHandleQueue(final Long node) {
        checkValidNode(node, reconnectState);
        handleQueue.add(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNextNodeToHandle() {
        return handleQueue.remove();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean areThereNodesToHandle() {
        return !handleQueue.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getChildAndPrepareForQueryResponse(final Long parent, final int childIndex) {
        final long child = getChild(parent, childIndex);
        expectedResponseQueue.add(child);
        return child;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getNodeForNextResponse() {
        return expectedResponseQueue.remove();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isResponseExpected() {
        return !expectedResponseQueue.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerResponseForNode(final Long node, final boolean learnerHasNode) {
        final ConcurrentNodeStatusTracker.Status status = learnerHasNode
                ? ConcurrentNodeStatusTracker.Status.KNOWN
                : ConcurrentNodeStatusTracker.Status.NOT_KNOWN;
        nodeStatusTracker.set(node, status);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasLearnerConfirmedFor(final Long node) {
        return nodeStatusTracker.getStatus(node) == ConcurrentNodeStatusTracker.Status.KNOWN;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serializeLeaf(final SerializableDataOutputStream out, final Long leaf) throws IOException {
        checkValidLeaf(leaf, reconnectState);
        final VirtualLeafRecord<K, V> leafRecord = records.findLeafRecord(leaf, false);
        assert leafRecord != null : "Unexpected null leaf record at path=" + leaf;
        out.writeSerializable(leafRecord, false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serializeInternal(final SerializableDataOutputStream out, final Long internal) throws IOException {
        checkValidInternal(internal, reconnectState);
        // We don't need to really serialize anything here, except the learner needs a long. So we'll send a long.
        out.writeLong(internal); // <--- works for VIRTUAL_MAP or any virtual internal node
        if (internal == ROOT_PATH) {
            out.writeLong(reconnectState.getFirstLeafPath());
            out.writeLong(reconnectState.getLastLeafPath());
        }
    }

    @Override
    public void writeChildHashes(final Long parent, final SerializableDataOutputStream out) throws IOException {
        checkValidInternal(parent, reconnectState);
        if (parent == ROOT_PATH && reconnectState.getLastLeafPath() == INVALID_PATH) {
            // out.writeSerializableList() writes just a single int if the list is empty
            out.writeInt(0);
            return;
        }
        final int size;
        if (parent > ROOT_PATH || (parent == ROOT_PATH && reconnectState.getLastLeafPath() > 1)) {
            size = 2;
        } else if (parent == ROOT_PATH && reconnectState.getLastLeafPath() == 1) {
            size = 1;
        } else {
            throw new MerkleSynchronizationException("Unexpected parent " + parent);
        }
        out.writeInt(size);
        // All same class? true
        out.writeBoolean(true);

        final long leftPath = getLeftChildPath(parent);
        // Is null? false
        out.writeBoolean(false);
        // Class version is written for the first entry only
        out.writeInt(Hash.CLASS_VERSION);
        // Write hash in SelfSerializable format
        if (!records.findAndWriteHash(leftPath, out)) {
            throw new MerkleSynchronizationException("Null hash for path = " + leftPath);
        }

        if (size == 2) {
            final long rightPath = getRightChildPath(parent);
            // Is null? false
            out.writeBoolean(false);
            // Class version is not written
            // Write hash in SelfSerializable format
            if (!records.findAndWriteHash(rightPath, out)) {
                throw new MerkleSynchronizationException("Null hash for path = " + rightPath);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCustomReconnectRoot(final Long node) {
        return node == ROOT_PATH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        try {
            waitUntilReady();
            records.getDataSource().close();
        } catch (final IOException e) {
            logger.error(EXCEPTION.getMarker(), "interrupted while attempting to close data source");
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "Failed to close data source properly", e);
            Thread.currentThread().interrupt();
        }
    }
}
