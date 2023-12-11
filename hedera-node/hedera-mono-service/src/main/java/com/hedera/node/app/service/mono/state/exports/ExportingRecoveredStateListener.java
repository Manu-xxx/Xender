/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.exports;

import com.hedera.node.app.service.mono.stream.RecordStreamManager;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateListener;
import com.swirlds.platform.system.state.notifications.NewRecoveredStateNotification;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A tiny {@link NewRecoveredStateListener}, registered <i>only</i> when {@code ServicesState#init()} is called with
 * {@link InitTrigger#EVENT_STREAM_RECOVERY}, that upon receiving a recovered state notification
 * <ol>
 *     <li>Freezes the record stream (hence flushing any pending items to a final file); and,</li>
 *     <li>Exports the account balances from the recovered state.</li>
 * </ol>
 */
public class ExportingRecoveredStateListener implements NewRecoveredStateListener {
    private final RecordStreamManager recordStreamManager;
    private final BalancesExporter balancesExporter;
    private final NodeId nodeId;

    public ExportingRecoveredStateListener(
            @NonNull final RecordStreamManager recordStreamManager,
            @NonNull final BalancesExporter balancesExporter,
            @NonNull final NodeId nodeId) {
        this.recordStreamManager = Objects.requireNonNull(recordStreamManager);
        this.balancesExporter = Objects.requireNonNull(balancesExporter);
        this.nodeId = Objects.requireNonNull(nodeId);
    }

    @Override
    public void notify(@NonNull final NewRecoveredStateNotification notification) {
        recordStreamManager.setInFreeze(true);
        balancesExporter.exportBalancesFrom(
                notification.getSwirldState(), notification.getConsensusTimestamp(), nodeId);
    }
}
