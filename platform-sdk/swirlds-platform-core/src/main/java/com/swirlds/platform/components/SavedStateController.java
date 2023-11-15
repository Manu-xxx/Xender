package com.swirlds.platform.components;

import static com.swirlds.logging.legacy.LogMarker.STATE_TO_DISK;
import static com.swirlds.platform.state.signed.StateToDiskReason.FIRST_ROUND_AFTER_GENESIS;
import static com.swirlds.platform.state.signed.StateToDiskReason.FREEZE_STATE;
import static com.swirlds.platform.state.signed.StateToDiskReason.PERIODIC_SNAPSHOT;
import static com.swirlds.platform.state.signed.StateToDiskReason.RECONNECT;

import com.swirlds.base.function.BooleanFunction;
import com.swirlds.common.config.StateConfig;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.StateToDiskReason;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SavedStateController {
    private static final Logger logger = LogManager.getLogger(SavedStateController.class);
    /**
     * The timestamp of the signed state that was most recently written to disk, or null if no timestamp was recently
     * written to disk.
     */
    private Instant previousSavedStateTimestamp;
    private final StateConfig stateConfig;
    private final BooleanFunction<ReservedSignedState> stateWrite;

    public SavedStateController(
            final StateConfig stateConfig,
            final BooleanFunction<ReservedSignedState> stateWrite) {
        this.stateConfig = stateConfig;
        this.stateWrite = stateWrite;
    }

    /**
     * Determine if a signed state should eventually be written to disk. If the state should eventually be written, the
     * state's {@link SignedState#markAsStateToSave} method will be called, to indicate the reason
     *
     * @param signedState the signed state in question
     */
    public synchronized void maybeSaveState(@NonNull final SignedState signedState) {

        final StateToDiskReason reason = shouldSaveToDisk(signedState, previousSavedStateTimestamp);

        if (reason != null) {
            saveToDisk(signedState.reserve("saving to disk"), reason);
        }
        // if a null reason is returned, then there isn't anything to do, since the state shouldn't be saved
    }

    public synchronized void reconnectStateReceived(@NonNull final SignedState signedState) {
        saveToDisk(signedState.reserve("saving to disk after reconnect"), RECONNECT);
    }

    /**
     * This should be called at boot time when a signed state is read from the disk.
     *
     * @param signedState the signed state that was read from file at boot time
     */
    public synchronized void registerSignedStateFromDisk(final SignedState signedState) {
        previousSavedStateTimestamp = signedState.getConsensusTimestamp();
    }

    private void saveToDisk(@NonNull final ReservedSignedState state, final StateToDiskReason reason) {
        final SignedState signedState = state.get();
        logger.info(
                STATE_TO_DISK.getMarker(),
                "Signed state from round {} created, "
                        + "will eventually be written to disk once sufficient signatures are collected, for reason: {}",
                signedState.getRound(),
                reason);

        previousSavedStateTimestamp = signedState.getConsensusTimestamp();
        signedState.markAsStateToSave(reason);
        final boolean accepted = stateWrite.apply(state);

        if (!accepted) {
            logger.error(
                    STATE_TO_DISK.getMarker(),
                    "Unable to save signed state to disk for round {} due to backlog of "
                            + "operations in the SignedStateManager task queue.",
                    signedState.getRound());

            state.close();
        }
    }


    /**
     * Determines whether a signed state should eventually be written to disk
     * <p>
     * If it is determined that the state should be written to disk, this method returns the reason why
     * <p>
     * If it is determined that the state shouldn't be written to disk, then this method returns null
     *
     * @param signedState       the state in question
     * @param previousTimestamp the timestamp of the previous state that was saved to disk, or null if no previous state
     *                          was saved to disk
     * @return the reason why the state should be written to disk, or null if it shouldn't be written to disk
     */
    @Nullable
    private StateToDiskReason shouldSaveToDisk(
            @NonNull final SignedState signedState,
            @Nullable final Instant previousTimestamp) {

        if (signedState.isFreezeState()) {
            // the state right before a freeze should be written to disk
            return FREEZE_STATE;
        }

        final int saveStatePeriod = stateConfig.saveStatePeriod();
        if (saveStatePeriod <= 0) {
            // periodic state saving is disabled
            return null;
        }

        // FUTURE WORK: writing genesis state to disk is currently disabled if the saveStatePeriod is 0.
        // This is for testing purposes, to have a method of disabling state saving for tests.
        // Once a feature to disable all state saving has been added, this block should be moved in front of the
        // saveStatePeriod <=0 block, so that saveStatePeriod doesn't impact the saving of genesis state.
        if (previousTimestamp == null) {
            // the first round should be saved
            return FIRST_ROUND_AFTER_GENESIS;
        }

        if ((signedState.getConsensusTimestamp().getEpochSecond() / saveStatePeriod)
                > (previousTimestamp.getEpochSecond() / saveStatePeriod)) {
            return PERIODIC_SNAPSHOT;
        } else {
            // the period hasn't yet elapsed
            return null;
        }
    }
}
