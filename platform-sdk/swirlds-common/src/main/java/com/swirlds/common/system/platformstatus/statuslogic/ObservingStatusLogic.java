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
import com.swirlds.common.system.platformstatus.PlatformStatusAction;
import com.swirlds.common.system.platformstatus.PlatformStatusConfig;
import com.swirlds.common.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;

/**
 * Class containing the state machine logic for the {@link PlatformStatus#OBSERVING OBSERVING} status.
 */
public class ObservingStatusLogic extends AbstractStatusLogic {
    /**
     * Whether a freeze period has been entered
     */
    private boolean freezePeriodEntered = false;

    /**
     * Constructor
     *
     * @param time   a source of time
     * @param config the platform status config
     */
    public ObservingStatusLogic(@NonNull final Time time, @NonNull final PlatformStatusConfig config) {
        super(time, config);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus processStatusAction(@NonNull final PlatformStatusAction action) {
        return switch (action) {
            case FREEZE_PERIOD_ENTERED -> {
                freezePeriodEntered = true;
                yield getStatus();
            }
            case FALLEN_BEHIND -> PlatformStatus.BEHIND;
            case STATE_WRITTEN_TO_DISK -> getStatus();
            case CATASTROPHIC_FAILURE -> PlatformStatus.CATASTROPHIC_FAILURE;
            case TIME_ELAPSED -> {
                if (Duration.between(getStatusStartTime(), getTime().now())
                                .compareTo(getConfig().observingStatusDelay())
                        < 0) {
                    // if the wait period hasn't elapsed, then stay in this status
                    yield getStatus();
                }

                if (freezePeriodEntered) {
                    yield PlatformStatus.FREEZING;
                } else {
                    yield PlatformStatus.CHECKING;
                }
            }
            default -> throw new IllegalArgumentException(getUnexpectedStatusActionLog(action));
        };
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public PlatformStatus getStatus() {
        return PlatformStatus.OBSERVING;
    }
}
