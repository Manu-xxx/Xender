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

package com.hedera.node.app.service.schedule.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WritableScheduleStoreImplTests extends ScheduleTestBase {

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        setUpBase();
    }

    @Test
    void verifyDeleteMarksDeletedInState() {
        final ScheduleID idToDelete = scheduleInState.scheduleId();
        Schedule actual = writableById.get(idToDelete);
        assertThat(actual).isNotNull().extracting("deleted").isEqualTo(Boolean.FALSE);
        writableSchedules.delete(idToDelete, testConsensusTime);
        actual = scheduleStore.get(idToDelete);
        assertThat(actual).isNotNull().extracting("deleted").isEqualTo(Boolean.TRUE);
        assertThat(actual.resolutionTime()).isNotNull().isEqualTo(asTimestamp(testConsensusTime));
    }

    private Timestamp asTimestamp(final Instant testConsensusTime) {
        return new Timestamp(testConsensusTime.getEpochSecond(), testConsensusTime.getNano());
    }

    @Test
    void verifyPutModifiesState() {
        final ScheduleID idToDelete = scheduleInState.scheduleId();
        Schedule actual = writableById.getForModify(idToDelete);
        assertThat(actual).isNotNull();
        assertThat(actual.signatories()).containsExactlyInAnyOrderElementsOf(scheduleInState.signatories());
        final Set<Key> modifiedSignatories = Set.of(schedulerKey, payerKey);
        final Schedule modified = replaceSignatoriesAndMarkExecuted(actual, modifiedSignatories, testConsensusTime);
        writableSchedules.put(modified);
        actual = scheduleStore.get(idToDelete);
        assertThat(actual).isNotNull();
        assertThat(actual.executed()).isTrue();
        assertThat(actual.resolutionTime()).isNotNull().isEqualTo(asTimestamp(testConsensusTime));
        assertThat(actual.signatories()).containsExactlyInAnyOrderElementsOf(modifiedSignatories);
    }

    @NonNull
    static Schedule replaceSignatoriesAndMarkExecuted(
            @NonNull final Schedule schedule,
            @NonNull final Set<Key> newSignatories,
            @NonNull final Instant consensusTime) {
        final Timestamp consensusTimestamp = new Timestamp(consensusTime.getEpochSecond(), consensusTime.getNano());
        final Schedule.Builder builder = schedule.copyBuilder().executed(true).resolutionTime(consensusTimestamp);
        return builder.signatories(List.copyOf(newSignatories)).build();
    }
}
