package com.swirlds.platform.state.nexus;

import com.swirlds.common.system.status.PlatformStatus;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A thread-safe container that also manages reservations for the emergency state.
 */
public class EmergencyStateNexus extends SignedStateNexus {
    /**
     * Clears the current state when the platform becomes active.
     *
     * @param status the new platform status
     */
    public void platformStatusChanged(@NonNull final PlatformStatus status) {
        if (status == PlatformStatus.ACTIVE) {
            clear();
        }
    }
}
