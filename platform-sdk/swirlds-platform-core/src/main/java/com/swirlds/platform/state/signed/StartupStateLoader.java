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

package com.swirlds.platform.state.signed;

import static com.swirlds.common.merkle.utility.MerkleUtils.rehashTree;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STARTUP;
import static com.swirlds.platform.state.GenesisStateBuilder.buildGenesisState;
import static com.swirlds.platform.state.signed.ReservedSignedState.createNullReservation;
import static com.swirlds.platform.state.signed.SignedStateFileReader.getSavedStateFiles;
import static com.swirlds.platform.state.signed.SignedStateFileReader.readStateFile;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.logging.payloads.SavedStateLoadedPayload;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utilities for loading the state at startup time.
 */
public final class StartupStateLoader {

    private static final Logger logger = LogManager.getLogger(StartupStateLoader.class);

    private StartupStateLoader() {}

    /**
     * Get the initial state to be used by this node. May return a state loaded from disk, or may return a genesis state
     * if no valid state is found on disk.
     *
     * @param platformContext          the platform context
     * @param recycleBin               the recycle bin
     * @param appMain                  the app main
     * @param mainClassName            the name of the app's SwirldMain class
     * @param swirldName               the name of this swirld
     * @param selfId                   the node id of this node
     * @param configAddressBook        the address book from config.txt
     * @param emergencyRecoveryManager the emergency recovery manager
     * @return the initial state to be used by this node
     */
    @NonNull
    public static ReservedSignedState getInitialState(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final SwirldMain appMain,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final NodeId selfId,
            @NonNull final AddressBook configAddressBook,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(mainClassName);
        Objects.requireNonNull(swirldName);
        Objects.requireNonNull(selfId);
        Objects.requireNonNull(configAddressBook);
        Objects.requireNonNull(emergencyRecoveryManager);

        final ReservedSignedState loadedState = StartupStateLoader.loadState(
                platformContext,
                recycleBin,
                selfId,
                mainClassName,
                swirldName,
                appMain.getSoftwareVersion(),
                emergencyRecoveryManager);

        try (loadedState) {
            if (loadedState.isNotNull()) {
                logger.info(
                        STARTUP.getMarker(),
                        new SavedStateLoadedPayload(
                                loadedState.get().getRound(), loadedState.get().getConsensusTimestamp()));

                return copyInitialSignedState(platformContext, loadedState.get());
            }
        }

        final ReservedSignedState genesisState =
                buildGenesisState(platformContext, configAddressBook, appMain.getSoftwareVersion(), appMain.newState());

        try (genesisState) {
            return copyInitialSignedState(platformContext, genesisState.get());
        }
    }

    /**
     * Looks at the states on disk, chooses one to load, and then loads the chosen state.
     *
     * @param platformContext          the platform context
     * @param recycleBin               the recycle bin
     * @param selfId                   the ID of this node
     * @param mainClassName            the name of the main class
     * @param swirldName               the name of the swirld
     * @param currentSoftwareVersion   the current software version
     * @param emergencyRecoveryManager the emergency recovery manager
     * @return a reserved signed state (wrapped state will be null if no state could be loaded)
     */
    @NonNull
    public static ReservedSignedState loadState(
            @NonNull final PlatformContext platformContext,
            @NonNull final RecycleBin recycleBin,
            @NonNull final NodeId selfId,
            @NonNull final String mainClassName,
            @NonNull final String swirldName,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {

        final StateConfig stateConfig = platformContext.getConfiguration().getConfigData(StateConfig.class);
        final String actualMainClassName = stateConfig.getMainClassName(mainClassName);

        final List<SavedStateInfo> savedStateFiles = getSavedStateFiles(actualMainClassName, selfId, swirldName);
        logStatesFound(savedStateFiles);

        if (savedStateFiles.isEmpty()) {
            // No states were found on disk.
            return createNullReservation();
        }

        final boolean emergencyStateRequired = emergencyRecoveryManager.isEmergencyStateRequired();

        final ReservedSignedState state;
        if (emergencyStateRequired) {
            state = loadEmergencyState(
                    platformContext, currentSoftwareVersion, savedStateFiles, emergencyRecoveryManager);
        } else {
            state = loadLatestState(platformContext, currentSoftwareVersion, savedStateFiles);
        }

        final long loadedRound = state.isNull() ? -1 : state.get().getRound();
        final boolean statesRecycled = cleanupUnusedStates(recycleBin, savedStateFiles, loadedRound);

        if (statesRecycled && emergencyStateRequired) {
            // TODO is there a better way?
            emergencyRecoveryManager.preconsensusEventStreamCleanupRequired();
        }

        return state;
    }


    /**
     * Create a copy of the initial signed state. There are currently data structures that become immutable after being
     * hashed, and we need to make a copy to force it to become mutable again.
     *
     * @param platformContext    the platform's context
     * @param initialSignedState the initial signed state
     * @return a copy of the initial signed state
     */
    public static @NonNull ReservedSignedState copyInitialSignedState(
            @NonNull final PlatformContext platformContext, @NonNull final SignedState initialSignedState) {
        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(initialSignedState);

        final State stateCopy = initialSignedState.getState().copy();
        final SignedState signedStateCopy =
                new SignedState(platformContext, stateCopy, "Browser create new copy of initial state");
        signedStateCopy.setSigSet(initialSignedState.getSigSet());

        return signedStateCopy.reserve("Browser copied initial state");
    }

    /**
     * Log the states that were discovered on disk.
     *
     * @param savedStateFiles the states that were discovered on disk
     */
    private static void logStatesFound(@NonNull final List<SavedStateInfo> savedStateFiles) {
        if (savedStateFiles.isEmpty()) {
            logger.info(STARTUP.getMarker(), "No saved states were found on disk.");
            return;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("The following saved states were found on disk:");
        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            sb.append("\n  - ").append(savedStateFile.stateFile());
        }
        logger.info(STARTUP.getMarker(), sb.toString());
    }

    /**
     * Load the latest state that is compatible with the emergency recovery file.
     *
     * @param platformContext          the platform context
     * @param currentSoftwareVersion   the current software version
     * @param savedStateFiles          the saved states to try
     * @param emergencyRecoveryManager the emergency recovery manager
     * @return the loaded state
     */
    @NonNull
    private static ReservedSignedState loadEmergencyState(
            @NonNull final PlatformContext platformContext,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final List<SavedStateInfo> savedStateFiles,
            @NonNull final EmergencyRecoveryManager emergencyRecoveryManager) {

        final EmergencyRecoveryFile recoveryFile = emergencyRecoveryManager.getEmergencyRecoveryFile();
        logger.info(
                STARTUP.getMarker(),
                "Loading state in emergency recovery mode. " + "Epoch hash = {}, recovery round = {}",
                recoveryFile.hash(),
                recoveryFile.hash());

        ReservedSignedState state = null;
        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            if (!emergencyRecoveryManager.isStateSuitableForStartup(savedStateFile)) {
                continue;
            }

            try {
                state = loadState(platformContext, currentSoftwareVersion, savedStateFile);
                break;
            } catch (final IOException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Failed to load saved state from file: {}",
                        savedStateFile.stateFile(),
                        e);
            }
        }

        if (state != null
                && emergencyRecoveryManager.isInHashEpoch(
                state.get().getState().getHash(),
                state.get()
                        .getState()
                        .getPlatformState()
                        .getPlatformData()
                        .getEpochHash())) {
            emergencyRecoveryManager.emergencyStateLoaded();
        }

        return state == null ? createNullReservation() : state;
    }

    /**
     * Load the latest state. If the latest state is invalid, try to load the next latest state. Repeat until a valid
     * state is found or there are no more states to try.
     *
     * @param platformContext        the platform context
     * @param currentSoftwareVersion the current software version
     * @param savedStateFiles        the saved states to try
     * @return the loaded state
     */
    private static ReservedSignedState loadLatestState(
            @NonNull final PlatformContext platformContext,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final List<SavedStateInfo> savedStateFiles) {

        logger.info(STARTUP.getMarker(), "Loading latest state from disk.");

        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            try {
                return loadState(platformContext, currentSoftwareVersion, savedStateFile);
            } catch (final IOException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Failed to load saved state from file: {}",
                        savedStateFile.stateFile(),
                        e);
            }
        }

        return createNullReservation();
    }

    /**
     * Load the requested state.
     *
     * @param platformContext        the platform context
     * @param currentSoftwareVersion the current software version
     * @param savedStateFile         the state to load
     * @return the loaded state, will be fully hashed
     */
    @NonNull
    private static ReservedSignedState loadState(
            @NonNull final PlatformContext platformContext,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @NonNull final SavedStateInfo savedStateFile)
            throws IOException {

        logger.info(STARTUP.getMarker(), "Loading signed state from disk: {}", savedStateFile.stateFile());
        final DeserializedSignedState deserializedSignedState =
                readStateFile(platformContext, savedStateFile.stateFile());
        final State state = deserializedSignedState.reservedSignedState().get().getState();

        final Hash oldHash = deserializedSignedState.originalHash();
        final Hash newHash = rehashTree(state);

        final SoftwareVersion loadedVersion = deserializedSignedState
                .reservedSignedState()
                .get()
                .getState()
                .getPlatformState()
                .getPlatformData()
                .getCreationSoftwareVersion();

        if (!oldHash.equals(newHash)) {
            if (loadedVersion.equals(currentSoftwareVersion)) {
                logger.warn(
                        STARTUP.getMarker(),
                        "The saved state file {} was created with the current version of the software, "
                                + "but the state hash has changed. Unless the state was intentionally modified, "
                                + "this good indicator that there may be a bug.",
                        savedStateFile.stateFile());
            } else {
                logger.warn(
                        STARTUP.getMarker(),
                        "The saved state file {} was created with version {}, which is different than the "
                                + "current version {}. The hash of the loaded state is different than the hash of the "
                                + "state when it was first created, which is not abnormal if there have been data "
                                + "migrations.",
                        savedStateFile.stateFile(),
                        loadedVersion,
                        currentSoftwareVersion);
            }
        }

        return deserializedSignedState.reservedSignedState();
    }

    /**
     * When we load a state from disk, it is illegal to have states with a higher round number on disk. Clean up those
     * states.
     *
     * @param savedStateFiles the states that were found on disk
     * @param loadedRound     the round number of the state that was loaded, or -1 if no state was loaded
     * @return true if states were recycled, false if no states were recycled
     */
    private static boolean cleanupUnusedStates(
            @NonNull final RecycleBin recycleBin,
            @NonNull final List<SavedStateInfo> savedStateFiles,
            final long loadedRound) {

        boolean statesRecycled = false;
        for (final SavedStateInfo savedStateFile : savedStateFiles) {
            if (savedStateFile.metadata().round() > loadedRound) {
                logger.warn(
                        STARTUP.getMarker(),
                        "Recycling state file {} since it from round {}, "
                                + "which is later than the round of the state being loaded ({}).",
                        savedStateFile.stateFile(),
                        savedStateFile.metadata().round(),
                        loadedRound);

                try {
                    statesRecycled = true;
                    recycleBin.recycle(savedStateFile.getDirectory());
                } catch (final IOException e) {
                    throw new UncheckedIOException("unable to recycle state file", e);
                }
            }
        }
        return statesRecycled;
    }
}
