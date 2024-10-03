package com.swirlds.platform.event.preconsensus;

import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

public class NoOpInlinePcesWriter implements InlinePcesWriter{
    @Override
    public void beginStreamingNewEvents() {

    }

    @NonNull
    @Override
    public PlatformEvent writeEvent(@NonNull final PlatformEvent event) {
        return event;
    }

    @Override
    public void registerDiscontinuity(@NonNull final Long newOriginRound) {

    }

    @Override
    public void updateNonAncientEventBoundary(@NonNull final EventWindow nonAncientBoundary) {

    }

    @Override
    public void setMinimumAncientIdentifierToStore(@NonNull final Long minimumAncientIdentifierToStore) {

    }
}
