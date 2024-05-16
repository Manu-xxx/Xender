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

package com.swirlds.virtualmap.internal.reconnect;

import com.swirlds.common.merkle.synchronization.streams.AsyncInputStream;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A task running on the learner side, which is responsible for getting responses from the teacher.
 *
 * <p>The task keeps running as long as the corresponding {@link LearnerPullVirtualTreeSendTask}
 * is alive, or some responses are expected from the teacher.
 *
 * <p>For every response from the teacher, the learner view is notified, which in turn notifies
 * the current traversal order, so it can recalculate the next virtual path to request.
 */
public class LearnerPullVirtualTreeReceiveTask {

    private static final Logger logger = LogManager.getLogger(LearnerPullVirtualTreeReceiveTask.class);

    private static final String NAME = "reconnect-learner-receiver";

    private final StandardWorkGroup workGroup;
    private final int viewId;
    private final AsyncInputStream in;
    private final LearnerPullVirtualTreeView<?, ?> view;

    // Indicates if the learner sender task is done sending all requests to the teacher
    private final AtomicBoolean senderIsFinished;

    // Number of requests sent to teacher / responses expected from the teacher. Increased in
    // the sending task, decreased in this task
    private final AtomicLong expectedResponses;

    // Indicates if a response for path 0 (virtual root node) has been received
    private final CountDownLatch rootResponseReceived;

    private final Consumer<Boolean> completeListener;

    /**
     * Create a thread for receiving responses to queries from the teacher.
     *
     * @param workGroup
     * 		the work group that will manage this thread
     * @param in
     * 		the input stream, this object is responsible for closing this when finished
     * @param view
     * 		the view to be used when touching the merkle tree
     * @param senderIsFinished
     * 		becomes true once the sending thread has finished
     */
    public LearnerPullVirtualTreeReceiveTask(
            final StandardWorkGroup workGroup,
            final int viewId,
            final AsyncInputStream in,
            final LearnerPullVirtualTreeView<?, ?> view,
            final AtomicBoolean senderIsFinished,
            final AtomicLong expectedResponses,
            final CountDownLatch rootResponseReceived,
            final Consumer<Boolean> completeListener) {
        this.workGroup = workGroup;
        this.viewId = viewId;
        this.in = in;
        this.view = view;
        this.senderIsFinished = senderIsFinished;
        this.expectedResponses = expectedResponses;
        this.rootResponseReceived = rootResponseReceived;
        this.completeListener = completeListener;
    }

    public void exec() {
        workGroup.execute(NAME, this::run);
    }

    private void run() {
        boolean success = false;
        try (view) {
            boolean finished = senderIsFinished.get();
            boolean responseExpected = expectedResponses.get() > 0;

            while (!finished || responseExpected) {
                if (responseExpected) {
                    final PullVirtualTreeResponse response = in.readAnticipatedMessage(viewId);
                    view.responseReceived(response);
                    if (response.getPath() == 0) {
                        rootResponseReceived.countDown();
                    }
                    expectedResponses.decrementAndGet();
                } else {
                    Thread.onSpinWait();
                }

                finished = senderIsFinished.get();
                responseExpected = expectedResponses.get() > 0;
            }
            success = true;
        } catch (final Exception ex) {
            workGroup.handleError(ex);
        } finally {
            completeListener.accept(success);
        }
    }
}
