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

package com.swirlds.platform.recovery;

import static com.swirlds.common.system.SystemExitCode.EMERGENCY_RECOVERY_ERROR;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.dispatch.triggers.control.ShutdownRequestedTrigger;
import com.swirlds.platform.reconnect.emergency.EmergencySignedStateValidator;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.state.signed.SavedStateInfo;
import com.swirlds.platform.state.signed.SavedStateMetadata;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Contains the current state of emergency recovery.
 */
public class EmergencyRecoveryManager {
    private static final Logger logger = LogManager.getLogger(EmergencyRecoveryManager.class);

    private final ShutdownRequestedTrigger shutdownRequestedTrigger;
    private final EmergencyRecoveryFile emergencyRecoveryFile;
    private final StateConfig stateConfig;
    private volatile boolean emergencyStateRequired;

    private final EmergencySignedStateValidator stateValidator;

    /**
     * If true, we need to clear the preconsensus event stream.
     */
    private boolean preconsensusEventStreamCleanupRequired = false;

    /**
     * @param stateConfig              the state configuration from the platform
     * @param shutdownRequestedTrigger a trigger that requests the platform to shut down
     * @param emergencyRecoveryDir     the directory to look for an emergency recovery file in
     */
    public EmergencyRecoveryManager(
            @NonNull final StateConfig stateConfig,
            @NonNull final ShutdownRequestedTrigger shutdownRequestedTrigger,
            @NonNull final Path emergencyRecoveryDir) {

        this.stateConfig = stateConfig;
        this.shutdownRequestedTrigger = shutdownRequestedTrigger;
        this.emergencyRecoveryFile = readEmergencyRecoveryFile(emergencyRecoveryDir);
        stateValidator = new EmergencySignedStateValidator(stateConfig, emergencyRecoveryFile);
        emergencyStateRequired = emergencyRecoveryFile != null;
    }

    /**
     * Signal that we need to clear the preconsensus event stream.
     */
    public void preconsensusEventStreamCleanupRequired() {
        preconsensusEventStreamCleanupRequired = true;
    }

    /**
     * Check if the preconsensus event stream should be cleared as a result of an emergency recovery.
     */
    public boolean shouldPreconsensusEventStreamBeCleared() {
        return preconsensusEventStreamCleanupRequired;
    }

    /**
     * Returns whether an emergency state is required to start the node. The state can be loaded from disk or acquired
     * via an emergency reconnect.
     *
     * @return {@code true} if an emergency recovery state is required, {@code false} otherwise
     */
    public boolean isEmergencyStateRequired() {
        return emergencyStateRequired;
    }

    /**
     * Invoked when an emergency state has been loaded into the system.
     */
    public void emergencyStateLoaded() {
        emergencyStateRequired = false;
    }

    /**
     * Provides the emergency recovery file, or null if there was none at node boot time.
     *
     * @return the emergency recovery files, or null if none
     */
    public EmergencyRecoveryFile getEmergencyRecoveryFile() {
        return emergencyRecoveryFile;
    }

    // TODO tests

    /**
     * Check if a state is in the hash epoch defined by the emergency recovery file.
     *
     * @param stateHash      the hash of the state in question
     * @param stateHashEpoch the epoch hash of the state in question
     * @return {@code true} if the state is in the hash epoch, {@code false} otherwise
     */
    public boolean isInHashEpoch(@NonNull final Hash stateHash, @Nullable final Hash stateHashEpoch) {

        if (emergencyRecoveryFile == null) {
            throw new IllegalStateException("Emergency recovery file is not present");
        }

        return stateHash.equals(emergencyRecoveryFile.hash())
                || Objects.equals(stateHashEpoch, emergencyRecoveryFile.hash());
    }

    /**
     * Check if a given state file on disk is compatible with the emergency recovery file.
     *
     * @param candidateState the state file to check
     * @return {@code true} if the state file is compatible, {@code false} otherwise
     */
    public boolean isStateSuitableForStartup(@NonNull final SavedStateInfo candidateState) {
        if (emergencyRecoveryFile == null) {
            throw new IllegalStateException("Emergency recovery file is not present");
        }

        if (candidateState.metadata().hash() == null
                || candidateState.metadata().epochHash() == null) {
            // This state was created with an old version of the metadata, do not consider it.
            // Any state written with the current software version will have a non-null value for this field.
            return false;
        }

        final SavedStateMetadata metadata = candidateState.metadata();

        return isInHashEpoch(metadata.hash(), metadata.epochHash()) || metadata.round() < emergencyRecoveryFile.round();
    }

    private EmergencyRecoveryFile readEmergencyRecoveryFile(final Path dir) {
        try {
            return EmergencyRecoveryFile.read(stateConfig, dir);
        } catch (final IOException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Detected an emergency recovery file at {} but was unable to read it",
                    dir,
                    e);
            shutdownRequestedTrigger.dispatch("Emergency Recovery Error", EMERGENCY_RECOVERY_ERROR);
            return null;
        }
    }
}
