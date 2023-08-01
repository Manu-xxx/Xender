/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect.emergency;

import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteNotification;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.status.StatusActionSubmitter;
import com.swirlds.common.system.status.actions.EmergencyReconnectStartedAction;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.state.signed.SignedStateFinder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements the emergency reconnect protocol over a bidirectional network
 */
public class EmergencyReconnectProtocol implements Protocol {
    private static final Logger logger = LogManager.getLogger(EmergencyReconnectProtocol.class);
    private final NodeId peerId;
    private final EmergencyRecoveryManager emergencyRecoveryManager;
    private final ReconnectThrottle teacherThrottle;
    private final SignedStateFinder stateFinder;
    private final Duration reconnectSocketTimeout;
    private final ReconnectMetrics reconnectMetrics;
    private final ReconnectController reconnectController;
    private InitiatedBy initiatedBy = InitiatedBy.NO_ONE;
    private final ThreadManager threadManager;
    private final NotificationEngine notificationEngine;

    @NonNull
    private final FallenBehindManager fallenBehindManager;

    /**
     * Enables submitting platform status actions
     */
    private final StatusActionSubmitter statusActionSubmitter;

    /**
     * @param threadManager            responsible for managing thread lifecycles
     * @param notificationEngine       the notification engine to use
     * @param peerId                   the ID of the peer we are communicating with
     * @param emergencyRecoveryManager the state of emergency recovery, if any
     * @param teacherThrottle          restricts reconnects as a teacher
     * @param stateFinder              finds compatible states based on round number and hash
     * @param reconnectSocketTimeout   the socket timeout to use when executing a reconnect
     * @param reconnectMetrics         tracks reconnect metrics
     * @param reconnectController      controls reconnecting as a learner
     * @param fallenBehindManager      maintains this node's behind status
     * @param statusActionSubmitter    enables submitting platform status actions
     */
    public EmergencyReconnectProtocol(
            final ThreadManager threadManager,
            final NotificationEngine notificationEngine,
            final NodeId peerId,
            final EmergencyRecoveryManager emergencyRecoveryManager,
            final ReconnectThrottle teacherThrottle,
            final SignedStateFinder stateFinder,
            final Duration reconnectSocketTimeout,
            final ReconnectMetrics reconnectMetrics,
            final ReconnectController reconnectController,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final StatusActionSubmitter statusActionSubmitter) {

        this.threadManager = threadManager;
        this.notificationEngine = notificationEngine;
        this.peerId = peerId;
        this.emergencyRecoveryManager = emergencyRecoveryManager;
        this.teacherThrottle = teacherThrottle;
        this.stateFinder = stateFinder;
        this.reconnectSocketTimeout = reconnectSocketTimeout;
        this.reconnectMetrics = reconnectMetrics;
        this.reconnectController = reconnectController;
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.statusActionSubmitter = Objects.requireNonNull(statusActionSubmitter);
    }

    @Override
    public boolean shouldInitiate() {
        final boolean initiateEmergencyReconnect = emergencyRecoveryManager.isEmergencyStateRequired();
        if (initiateEmergencyReconnect) {
            // if a permit is acquired, it will be released by either initiateFailed or runProtocol
            final boolean shouldInitiate = reconnectController.acquireLearnerPermit();
            if (shouldInitiate) {
                statusActionSubmitter.submitStatusAction(new EmergencyReconnectStartedAction());
                initiatedBy = InitiatedBy.SELF;
            }
            return shouldInitiate;
        }
        return false;
    }

    @Override
    public void initiateFailed() {
        reconnectController.cancelLearnerPermit();
        initiatedBy = InitiatedBy.NO_ONE;
    }

    @Override
    public boolean shouldAccept() {
        // if the throttle is initiated, we should call markReconnectFinished in teacher()
        final boolean shouldAccept = teacherThrottle.initiateReconnect(peerId);
        if (shouldAccept) {
            initiatedBy = InitiatedBy.PEER;
        }
        return shouldAccept;
    }

    @Override
    public boolean acceptOnSimultaneousInitiate() {
        // if both nodes are missing the emergency state, it makes no sense to reconnect with each other
        return false;
    }

    @Override
    public void runProtocol(final Connection connection)
            throws NetworkProtocolException, IOException, InterruptedException {
        try {
            switch (initiatedBy) {
                case PEER -> teacher(connection);
                case SELF -> learner(connection);
                default -> throw new NetworkProtocolException(String.format(
                        "runProtocol() called for emergency reconnect with peer %d "
                                + "but it is unclear who the teacher and who the learner is",
                        peerId.id()));
            }
        } finally {
            initiatedBy = InitiatedBy.NO_ONE;
        }
    }

    private void teacher(final Connection connection) {
        try {
            new EmergencyReconnectTeacher(
                            threadManager,
                            stateFinder,
                            reconnectSocketTimeout,
                            fallenBehindManager::hasFallenBehind,
                            reconnectMetrics)
                    .execute(connection);
        } finally {
            teacherThrottle.reconnectAttemptFinished();
        }
    }

    private void learner(final Connection connection) {
        registerReconnectCompleteListener();
        try {
            final boolean peerHasState = new EmergencyReconnectLearner(
                            emergencyRecoveryManager.getEmergencyRecoveryFile(), reconnectController)
                    .execute(connection);
            if (!peerHasState) {
                reconnectController.cancelLearnerPermit();
            }
        } catch (final Exception e) {
            reconnectController.cancelLearnerPermit();
            throw e;
        }
    }

    private void registerReconnectCompleteListener() {
        notificationEngine.unregister(ReconnectCompleteListener.class, this::emergencyReconnectComplete);
        notificationEngine.register(ReconnectCompleteListener.class, this::emergencyReconnectComplete);
    }

    private void emergencyReconnectComplete(final ReconnectCompleteNotification notification) {
        if (emergencyRecoveryManager.isEmergencyStateRequired()) {
            logger.info(
                    RECONNECT.getMarker(),
                    "Emergency Reconnect Complete, round {} received from peer {}",
                    notification.getRoundNumber(),
                    peerId.id());
            emergencyRecoveryManager.emergencyStateLoaded();
        }
    }

    private enum InitiatedBy {
        NO_ONE,
        SELF,
        PEER
    }
}
