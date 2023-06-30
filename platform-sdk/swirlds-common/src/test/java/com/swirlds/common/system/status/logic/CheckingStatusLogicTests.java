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

package com.swirlds.common.system.status.logic;

import static com.swirlds.common.system.status.logic.StatusLogicTestUtils.triggerActionAndAssertException;
import static com.swirlds.common.system.status.logic.StatusLogicTestUtils.triggerActionAndAssertNoTransition;
import static com.swirlds.common.system.status.logic.StatusLogicTestUtils.triggerActionAndAssertTransition;

import com.swirlds.common.system.status.PlatformStatus;
import com.swirlds.common.system.status.PlatformStatusConfig;
import com.swirlds.common.system.status.actions.CatastrophicFailureAction;
import com.swirlds.common.system.status.actions.DoneReplayingEventsAction;
import com.swirlds.common.system.status.actions.FallenBehindAction;
import com.swirlds.common.system.status.actions.FreezePeriodEnteredAction;
import com.swirlds.common.system.status.actions.ReconnectCompleteAction;
import com.swirlds.common.system.status.actions.SelfEventReachedConsensusAction;
import com.swirlds.common.system.status.actions.StartedReplayingEventsAction;
import com.swirlds.common.system.status.actions.StateWrittenToDiskAction;
import com.swirlds.common.system.status.actions.TimeElapsedAction;
import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.test.fixtures.TestConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CheckingStatusLogic}.
 */
class CheckingStatusLogicTests {
    private FakeTime time;
    private CheckingStatusLogic logic;

    @BeforeEach
    void setup() {
        time = new FakeTime();
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        logic = new CheckingStatusLogic(configuration.getConfigData(PlatformStatusConfig.class));
    }

    @Test
    @DisplayName("Go to FREEZING")
    void toFreezing() {
        triggerActionAndAssertTransition(
                logic::processFreezePeriodEnteredAction, new FreezePeriodEnteredAction(0), PlatformStatus.FREEZING);
    }

    @Test
    @DisplayName("Go to BEHIND")
    void toBehind() {
        triggerActionAndAssertTransition(
                logic::processFallenBehindAction, new FallenBehindAction(), PlatformStatus.BEHIND);
    }

    @Test
    @DisplayName("Go to ACTIVE")
    void toActive() {
        triggerActionAndAssertTransition(
                logic::processSelfEventReachedConsensusAction,
                new SelfEventReachedConsensusAction(time.now()),
                PlatformStatus.ACTIVE);
    }

    @Test
    @DisplayName("Go to CATASTROPHIC_FAILURE")
    void toCatastrophicFailure() {
        triggerActionAndAssertTransition(
                logic::processCatastrophicFailureAction,
                new CatastrophicFailureAction(),
                PlatformStatus.CATASTROPHIC_FAILURE);
    }

    @Test
    @DisplayName("Irrelevant actions shouldn't cause transitions")
    void irrelevantActions() {
        triggerActionAndAssertNoTransition(
                logic::processStateWrittenToDiskAction, new StateWrittenToDiskAction(0), logic.getStatus());
        triggerActionAndAssertNoTransition(
                logic::processTimeElapsedAction, new TimeElapsedAction(time.now()), logic.getStatus());
    }

    @Test
    @DisplayName("Unexpected actions should cause exceptions")
    void unexpectedActions() {
        triggerActionAndAssertException(
                logic::processStartedReplayingEventsAction, new StartedReplayingEventsAction(), logic.getStatus());
        triggerActionAndAssertException(
                logic::processDoneReplayingEventsAction, new DoneReplayingEventsAction(time.now()), logic.getStatus());
        triggerActionAndAssertException(
                logic::processReconnectCompleteAction, new ReconnectCompleteAction(0), logic.getStatus());
    }
}
