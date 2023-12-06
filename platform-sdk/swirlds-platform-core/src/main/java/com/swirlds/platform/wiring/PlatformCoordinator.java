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

package com.swirlds.platform.wiring;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Responsible for coordinating the clearing of the platform wiring objects.
 */
public class PlatformCoordinator {
    private final InternalEventValidatorWiring internalEventValidatorWiring;
    private final EventDeduplicatorWiring eventDeduplicatorWiring;
    private final EventSignatureValidatorWiring eventSignatureValidatorWiring;
    private final OrphanBufferWiring orphanBufferWiring;
    private final InOrderLinkerWiring inOrderLinkerWiring;
    private final LinkedEventIntakeWiring linkedEventIntakeWiring;

    /**
     * Constructor
     *
     * @param internalEventValidatorWiring  the internal event validator wiring
     * @param eventDeduplicatorWiring       the event deduplicator wiring
     * @param eventSignatureValidatorWiring the event signature validator wiring
     * @param orphanBufferWiring            the orphan buffer wiring
     * @param inOrderLinkerWiring           the in order linker wiring
     * @param linkedEventIntakeWiring       the linked event intake wiring
     */
    public PlatformCoordinator(
            @NonNull final InternalEventValidatorWiring internalEventValidatorWiring,
            @NonNull final EventDeduplicatorWiring eventDeduplicatorWiring,
            @NonNull final EventSignatureValidatorWiring eventSignatureValidatorWiring,
            @NonNull final OrphanBufferWiring orphanBufferWiring,
            @NonNull final InOrderLinkerWiring inOrderLinkerWiring,
            @NonNull final LinkedEventIntakeWiring linkedEventIntakeWiring) {

        this.internalEventValidatorWiring = Objects.requireNonNull(internalEventValidatorWiring);
        this.eventDeduplicatorWiring = Objects.requireNonNull(eventDeduplicatorWiring);
        this.eventSignatureValidatorWiring = Objects.requireNonNull(eventSignatureValidatorWiring);
        this.orphanBufferWiring = Objects.requireNonNull(orphanBufferWiring);
        this.inOrderLinkerWiring = Objects.requireNonNull(inOrderLinkerWiring);
        this.linkedEventIntakeWiring = Objects.requireNonNull(linkedEventIntakeWiring);
    }

    /**
     * Safely clears the intake pipeline
     * <p>
     * Future work: this method should be expanded to coordinate the clearing of the entire system
     */
    public void clear() {
        // pause the orphan buffer to break the cycle that exists in intake, and flush the pause through
        orphanBufferWiring.pauseInput().inject(true);
        orphanBufferWiring.flushRunnable().run();

        // now that no cycles exist, flush all the wiring objects
        internalEventValidatorWiring.flushRunnable().run();
        eventDeduplicatorWiring.flushRunnable().run();
        eventSignatureValidatorWiring.flushRunnable().run();
        orphanBufferWiring.flushRunnable().run();
        inOrderLinkerWiring.flushRunnable().run();
        linkedEventIntakeWiring.flushRunnable().run();

        // once everything has been flushed through the system, it's safe to unpause the orphan buffer
        orphanBufferWiring.pauseInput().inject(false);

        // data is no longer moving through the system. clear all the internal data structures in the wiring objects
        eventDeduplicatorWiring.clearInput().inject(new ClearTrigger());
        eventDeduplicatorWiring.flushRunnable().run();
        orphanBufferWiring.clearInput().inject(new ClearTrigger());
        orphanBufferWiring.flushRunnable().run();
        inOrderLinkerWiring.clearInput().inject(new ClearTrigger());
        inOrderLinkerWiring.flushRunnable().run();
    }
}
