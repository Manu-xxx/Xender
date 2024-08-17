/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * This interface represents the platform state and provide access to the state's properties.
 */
public interface PlatformStateAccessor {
    /**
     * The round of the genesis state.
     */
    long GENESIS_ROUND = 0;

    /**
     * Get the software version of the application that created this state.
     *
     * @return the creation version
     */
    @NonNull
    SoftwareVersion getCreationSoftwareVersion();

    /**
     * Set the software version of the application that created this state.
     *
     * @param creationVersion the creation version
     */
    void setCreationSoftwareVersion(@NonNull SoftwareVersion creationVersion);

    /**
     * Get the address book.
     */
    @Nullable
    AddressBook getAddressBook();

    /**
     * Set the address book.
     *
     * @param addressBook an address book
     */
    void setAddressBook(@Nullable AddressBook addressBook);

    /**
     * Get the previous address book.
     */
    @Nullable
    AddressBook getPreviousAddressBook();

    /**
     * Set the previous address book.
     *
     * @param addressBook an address book
     */
    void setPreviousAddressBook(@Nullable AddressBook addressBook);

    /**
     * Set the round when this state was generated.
     *
     * @param round a round number
     */
    void setRound(long round);

    /**
     * Get the round when this state was generated.
     *
     * @return a round number
     */
    long getRound();

    /**
     * Get the legacy running event hash. Used by the consensus event stream.
     *
     * @return a running hash of events
     */
    @Nullable
    Hash getLegacyRunningEventHash();

    /**
     * Set the legacy running event hash. Used by the consensus event stream.
     *
     * @param legacyRunningEventHash a running hash of events
     */
    void setLegacyRunningEventHash(@Nullable Hash legacyRunningEventHash);

    /**
     * Get the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @return a consensus timestamp
     */
    @Nullable
    Instant getConsensusTimestamp();

    /**
     * Set the consensus timestamp for this state, defined as the timestamp of the first transaction that was applied in
     * the round that created the state.
     *
     * @param consensusTimestamp a consensus timestamp
     */
    void setConsensusTimestamp(@NonNull Instant consensusTimestamp);

    /**
     * For the oldest non-ancient round, get the lowest ancient indicator out of all of those round's judges. This is
     * the ancient threshold at the moment after this state's round reached consensus. All events with an ancient
     * indicator that is greater than or equal to this value are non-ancient. All events with an ancient indicator less
     * than this value are ancient.
     *
     * <p>
     * When running in {@link AncientMode#GENERATION_THRESHOLD}, this value is the minimum generation non-ancient. When
     * running in {@link AncientMode#BIRTH_ROUND_THRESHOLD}, this value is the minimum birth round non-ancient.
     * </p>
     *
     * @return the ancient threshold after this round has reached consensus
     */
    long getAncientThreshold();

    /**
     * Sets the number of non-ancient rounds.
     *
     * @param roundsNonAncient the number of non-ancient rounds
     */
    void setRoundsNonAncient(int roundsNonAncient);

    /**
     * Gets the number of non-ancient rounds.
     *
     * @return the number of non-ancient rounds
     */
    int getRoundsNonAncient();

    /**
     * @return the consensus snapshot for this round
     */
    @Nullable
    ConsensusSnapshot getSnapshot();

    /**
     * @param snapshot the consensus snapshot for this round
     */
    void setSnapshot(@NonNull ConsensusSnapshot snapshot);

    /**
     * Gets the time when the next freeze is scheduled to start. If null then there is no freeze scheduled.
     *
     * @return the time when the freeze starts
     */
    @Nullable
    Instant getFreezeTime();

    /**
     * Sets the instant after which the platform will enter FREEZING status. When consensus timestamp of a signed state
     * is after this instant, the platform will stop creating events and accepting transactions. This is used to safely
     * shut down the platform for maintenance.
     *
     * @param freezeTime an Instant in UTC
     */
    void setFreezeTime(@Nullable Instant freezeTime);

    /**
     * Gets the last freezeTime based on which the nodes were frozen. If null then there has never been a freeze.
     *
     * @return the last freezeTime based on which the nodes were frozen
     */
    @Nullable
    Instant getLastFrozenTime();

    /**
     * Sets the last freezeTime based on which the nodes were frozen.
     *
     * @param lastFrozenTime the last freezeTime based on which the nodes were frozen
     */
    void setLastFrozenTime(@Nullable Instant lastFrozenTime);

    /**
     * Get the first software version where the birth round migration happened, or null if birth round migration has not
     * yet happened.
     *
     * @return the first software version where the birth round migration happened
     */
    @Nullable
    SoftwareVersion getFirstVersionInBirthRoundMode();

    /**
     * Set the first software version where the birth round migration happened.
     *
     * @param firstVersionInBirthRoundMode the first software version where the birth round migration happened
     */
    void setFirstVersionInBirthRoundMode(SoftwareVersion firstVersionInBirthRoundMode);

    /**
     * Get the last round before the birth round mode was enabled, or -1 if birth round mode has not yet been enabled.
     *
     * @return the last round before the birth round mode was enabled
     */
    long getLastRoundBeforeBirthRoundMode();

    /**
     * Set the last round before the birth round mode was enabled.
     *
     * @param lastRoundBeforeBirthRoundMode the last round before the birth round mode was enabled
     */
    void setLastRoundBeforeBirthRoundMode(long lastRoundBeforeBirthRoundMode);

    /**
     * Get the lowest judge generation before the birth round mode was enabled, or -1 if birth round mode has not yet
     * been enabled.
     *
     * @return the lowest judge generation before the birth round mode was enabled
     */
    long getLowestJudgeGenerationBeforeBirthRoundMode();

    /**
     * Set the lowest judge generation before the birth round mode was enabled.
     *
     * @param lowestJudgeGenerationBeforeBirthRoundMode the lowest judge generation before the birth round mode was
     *                                                  enabled
     */
    void setLowestJudgeGenerationBeforeBirthRoundMode(long lowestJudgeGenerationBeforeBirthRoundMode);
}
