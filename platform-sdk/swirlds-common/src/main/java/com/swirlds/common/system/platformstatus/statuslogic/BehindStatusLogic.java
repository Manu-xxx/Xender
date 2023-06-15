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

package com.swirlds.common.system.platformstatus.statuslogic;

import com.swirlds.common.system.platformstatus.IllegalPlatformStatusException;
import com.swirlds.common.system.platformstatus.PlatformStatus;
import com.swirlds.common.system.platformstatus.PlatformStatusConfig;
import com.swirlds.common.system.platformstatus.statusactions.CatastrophicFailureAction;
import com.swirlds.common.system.platformstatus.statusactions.DoneReplayingEventsAction;
import com.swirlds.common.system.platformstatus.statusactions.FallenBehindAction;
import com.swirlds.common.system.platformstatus.statusactions.FreezePeriodEnteredAction;
import com.swirlds.common.system.platformstatus.statusactions.ReconnectCompleteAction;
import com.swirlds.common.system.platformstatus.statusactions.SelfEventReachedConsensusAction;
import com.swirlds.common.system.platformstatus.statusactions.StartedReplayingEventsAction;
import com.swirlds.common.system.platformstatus.statusactions.StateWrittenToDiskAction;
import com.swirlds.common.system.platformstatus.statusactions.TimeElapsedAction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Class containing the state machine logic for the {@link PlatformStatus#BEHIND} status.
 */
public class BehindStatusLogic implements PlatformStatusLogic {
    /**
     * The platform status config
     */
    private final PlatformStatusConfig config;

    /**
     * The round number of the freeze period if one has been entered, otherwise null
     */
    private Long freezeRound = null;

    /**
     * Constructor
     *
     * @param config the platform status config object
     */
    public BehindStatusLogic(@NonNull final PlatformStatusConfig config) {
        this.config = config;
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link PlatformStatus#BEHIND} status unconditionally transitions to {@link PlatformStatus#CATASTROPHIC_FAILURE}
     * when a {@link CatastrophicFailureAction} is processed.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processCatastrophicFailureAction(@NonNull final CatastrophicFailureAction action) {
        return new CatastrophicFailureStatusLogic();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link DoneReplayingEventsAction} while in {@link PlatformStatus#BEHIND} throws an exception, since
     * this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processDoneReplayingEventsAction(@NonNull final DoneReplayingEventsAction action) {
        Objects.requireNonNull(action);

        throw new IllegalPlatformStatusException(action, getStatus());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link FallenBehindAction} while in {@link PlatformStatus#BEHIND} throws an exception, since this is
     * not conceivable in standard operation. It shouldn't be possible to receive another fallen behind notification
     * while already behind.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processFallenBehindAction(@NonNull final FallenBehindAction action) {
        Objects.requireNonNull(action);

        throw new IllegalPlatformStatusException(action, getStatus());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link FreezePeriodEnteredAction} while in {@link PlatformStatus#BEHIND} doesn't ever result in a
     * status transition, but this logic method does record the freeze round, to be able to pass that information on the
     * {@link ReconnectCompleteStatusLogic} once reconnect is complete.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processFreezePeriodEnteredAction(@NonNull final FreezePeriodEnteredAction action) {
        if (freezeRound != null) {
            throw new IllegalPlatformStatusException(
                    "Received duplicate freeze period notification in BEHIND status. Previous notification was for round "
                            + freezeRound + ", new notification is for round " + action.freezeRound());
        }

        freezeRound = action.freezeRound();
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link PlatformStatus#BEHIND} status unconditionally transitions to {@link PlatformStatus#RECONNECT_COMPLETE}
     * when a {@link ReconnectCompleteAction} is processed.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processReconnectCompleteAction(@NonNull final ReconnectCompleteAction action) {
        return new ReconnectCompleteStatusLogic(action.reconnectStateRound(), freezeRound, config);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link SelfEventReachedConsensusAction} while in {@link PlatformStatus#BEHIND} has no effect on the
     * state machine.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processSelfEventReachedConsensusAction(
            @NonNull final SelfEventReachedConsensusAction action) {

        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link StartedReplayingEventsAction} while in {@link PlatformStatus#BEHIND} throws an exception,
     * since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processStartedReplayingEventsAction(@NonNull final StartedReplayingEventsAction action) {
        Objects.requireNonNull(action);

        throw new IllegalPlatformStatusException(action, getStatus());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link StateWrittenToDiskAction} while in {@link PlatformStatus#BEHIND} has no effect on the state
     * machine.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processStateWrittenToDiskAction(@NonNull final StateWrittenToDiskAction action) {
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link TimeElapsedAction} while in {@link PlatformStatus#BEHIND} has no effect on the state machine.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processTimeElapsedAction(@NonNull final TimeElapsedAction action) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus getStatus() {
        return PlatformStatus.BEHIND;
    }
}
