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

package com.swirlds.platform.consensus;

import com.swirlds.common.config.ConsensusConfig;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.state.MinGenInfo;
import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

/**
 * Utility class for generating "synthetic" snapshots
 */
public final class SyntheticSnapshot {
    /** Utility class, should not be instantiated */
    private SyntheticSnapshot() {
    }

    /**
     * Generate a {@link ConsensusSnapshot} based on the supplied data. This snapshot is not the result of consensus
     * but is instead generated to be used as a starting point for consensus. The snapshot will contain a single
     * judge whose generation will be almost ancient. All events older than the judge will be considered ancient.
     * The judge is the only event needed to continue consensus operations. Once the judge is added to
     * {@link com.swirlds.platform.Consensus}, it will be marked as already having reached consensus beforehand, so it
     * will not reach consensus again.
     *
     * @param round the round of the snapshot
     * @param lastConsensusOrder the last consensus order of all events that have reached consensus
     * @param roundTimestamp the timestamp of the round
     * @param config the consensus configuration
     * @param judge the judge event
     * @return the synthetic snapshot
     */
    public static ConsensusSnapshot generateSyntheticSnapshot(
            final long round,
            final long lastConsensusOrder,
            final Instant roundTimestamp,
            final ConsensusConfig config,
            final GossipEvent judge) {
        final List<MinGenInfo> minGenInfos = LongStream.range(
                        RoundCalculationUtils.getOldestNonAncientRound(config.roundsNonAncient(), round), round + 1)
                .mapToObj(r -> new MinGenInfo(r, judge.getGeneration()))
                .toList();
        return new ConsensusSnapshot(
                round,
                List.of(judge.getHashedData().getHash()),
                minGenInfos,
                lastConsensusOrder + 1,
                ConsensusUtils.calcMinTimestampForNextEvent(roundTimestamp));
    }
}
