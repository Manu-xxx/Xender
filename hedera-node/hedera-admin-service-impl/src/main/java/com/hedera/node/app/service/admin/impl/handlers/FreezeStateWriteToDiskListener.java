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

package com.hedera.node.app.service.admin.impl.handlers;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.txns.network.UpgradeActions;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Listener that will be notified with {@link
 * StateWriteToDiskCompleteNotification} when state is
 * written to disk. This writes {@code NOW_FROZEN_MARKER} to disk when upgrade is pending
 */
@Singleton
public class FreezeStateWriteToDiskListener implements StateWriteToDiskCompleteListener {
    private static final Logger log = LogManager.getLogger(FreezeStateWriteToDiskListener.class);

    private final UpgradeActions upgradeActions;

    @Inject
    public FreezeStateWriteToDiskListener(@NonNull final UpgradeActions upgradeActions) {
        requireNonNull(upgradeActions);
        this.upgradeActions = upgradeActions;
    }

    @Override
    public void notify(@NonNull final StateWriteToDiskCompleteNotification notification) {
        requireNonNull(notification);
        if (notification.isFreezeState()) {
            log.info(
                    "Notification Received: Freeze State Finished. "
                            + "consensusTimestamp: {}, roundNumber: {}, sequence: {}",
                    notification.getConsensusTimestamp(),
                    notification.getRoundNumber(),
                    notification.getSequence());
            upgradeActions.externalizeFreezeIfUpgradePending();
        }
    }
}
