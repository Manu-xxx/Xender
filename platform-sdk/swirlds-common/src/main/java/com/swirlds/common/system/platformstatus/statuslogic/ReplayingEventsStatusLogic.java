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

/**
 * Class containing the state machine logic for the {@link PlatformStatus#REPLAYING_EVENTS} status.
 */
public class ReplayingEventsStatusLogic implements PlatformStatusLogic {
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
     * @param config the platform status config
     */
    public ReplayingEventsStatusLogic(@NonNull final PlatformStatusConfig config) {
        this.config = config;
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@link PlatformStatus#REPLAYING_EVENTS} status unconditionally transitions to
     * {@link PlatformStatus#CATASTROPHIC_FAILURE} when a {@link CatastrophicFailureAction} is processed.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processCatastrophicFailureAction(@NonNull CatastrophicFailureAction action) {
        return new CatastrophicFailureStatusLogic();
    }

    /**
     * {@inheritDoc}
     * <p>
     * If a freeze boundary wasn't crossed while replaying events, then receiving a {@link DoneReplayingEventsAction}
     * causes a transition to {@link PlatformStatus#OBSERVING}.
     * <p>
     * If a freeze boundary was crossed while replaying events, then the {@link DoneReplayingEventsAction} doesn't
     * affect the state machine. The status will remain in {@link PlatformStatus#REPLAYING_EVENTS} until the freeze
     * state has been saved.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processDoneReplayingEventsAction(@NonNull DoneReplayingEventsAction action) {
        // always transition to a new status when done replaying events
        if (freezeRound != null) {
            // if a freeze boundary was crossed, we won't transition out of this state until the freeze state
            // has been saved
            return this;
        } else {
            return new ObservingStatusLogic(action.instant(), config);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link FallenBehindAction} while in {@link PlatformStatus#REPLAYING_EVENTS} throws an exception,
     * since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processFallenBehindAction(@NonNull FallenBehindAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link FreezePeriodEnteredAction} while in {@link PlatformStatus#REPLAYING_EVENTS} doesn't ever
     * result in a status transition, but this logic method does record the freeze round, which will inform the status
     * progression later.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processFreezePeriodEnteredAction(@NonNull FreezePeriodEnteredAction action) {
        freezeRound = action.freezeRound();
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link ReconnectCompleteAction} while in {@link PlatformStatus#REPLAYING_EVENTS} throws an exception,
     * since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processReconnectCompleteAction(@NonNull ReconnectCompleteAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link SelfEventReachedConsensusAction} while in {@link PlatformStatus#REPLAYING_EVENTS} has no
     * effect on the state machine.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processSelfEventReachedConsensusAction(@NonNull SelfEventReachedConsensusAction action) {
        return this;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link StartedReplayingEventsAction} while in {@link PlatformStatus#REPLAYING_EVENTS} throws an
     * exception, since this is not conceivable in standard operation.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processStartedReplayingEventsAction(@NonNull StartedReplayingEventsAction action) {
        throw new IllegalStateException(getUnexpectedStatusActionLog(action));
    }

    /**
     * {@inheritDoc}
     * <p>
     * If a freeze boundary has been crossed, and the state saved was the freeze state, then the status will transition
     * to {@link PlatformStatus#FREEZE_COMPLETE}.
     * <p>
     * Otherwise, a state being saved has no effect on the state machine.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processStateWrittenToDiskAction(@NonNull StateWrittenToDiskAction action) {
        if (freezeRound != null && action.round() == freezeRound) {
            return new FreezeCompleteStatusLogic();
        } else {
            return this;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Receiving a {@link TimeElapsedAction} while in {@link PlatformStatus#REPLAYING_EVENTS} has no effect on the state
     * machine.
     */
    @NonNull
    @Override
    public PlatformStatusLogic processTimeElapsedAction(@NonNull TimeElapsedAction action) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus getStatus() {
        return PlatformStatus.REPLAYING_EVENTS;
    }
}
