/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.orphan;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.EventConstants;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link OrphanBuffer}
 */
class OrphanBufferTests {
    /**
     * Events that will be "received" from intake
     */
    private List<GossipEvent> intakeEvents;

    /**
     * The maximum generation of any event that has been created
     */
    private long maxGeneration;

    private Random random;

    /**
     * The number of events to be created for testing
     */
    private static final long TEST_EVENT_COUNT = 10000;
    /**
     * Number of possible nodes in the universe
     */
    private static final int NODE_ID_COUNT = 100;

    /**
     * The average number of generations per round.
     */
    private static final long AVG_GEN_PER_ROUND = 2;

    /**
     * A method that returns 1 to advance a node's birth round to achieve the AVG_GEN_PER_ROUND and taking into account
     * the number of nodes in the network indicated by NODE_ID_COUNT
     */
    private static final Function<Random, Long> maybeAdvanceRound =
            random -> (random.nextLong(0L, AVG_GEN_PER_ROUND * NODE_ID_COUNT) == 0L ? 1L : 0L);

    /**
     * The number of most recently created events to consider when choosing an other parent
     */
    private static final int PARENT_SELECTION_WINDOW = 100;

    /**
     * The maximum amount to advance minimumGenerationNonAncient at a time. Average advancement will be half this.
     */
    private static final int MAX_GENERATION_STEP = 10;

    private AtomicLong eventsExitedIntakePipeline;

    /**
     * Create a bootstrap event for a node. This is just a descriptor, and will never be received from intake.
     *
     * @param nodeId           the node to create the bootstrap event for
     * @param parentCandidates the list of events to choose from when selecting an other parent
     * @return the bootstrap event descriptor
     */
    private GossipEvent createBootstrapEvent(
            @NonNull final NodeId nodeId, @NonNull final List<GossipEvent> parentCandidates) {

        final GossipEvent bootstrapEvent =
                new TestingEventBuilder(random).setCreatorId(nodeId).build();
        parentCandidates.add(bootstrapEvent);
        return bootstrapEvent;
    }

    /**
     * Create a random event
     *
     * @param parentCandidates the list of events to choose from when selecting an other parent
     * @param tips             the most recent events from each node
     * @return the random event
     */
    private GossipEvent createRandomEvent(
            @NonNull final List<GossipEvent> parentCandidates, @NonNull final Map<NodeId, GossipEvent> tips) {

        final NodeId eventCreator = new NodeId(random.nextInt(NODE_ID_COUNT));

        final GossipEvent selfParent =
                tips.computeIfAbsent(eventCreator, creator -> createBootstrapEvent(creator, parentCandidates));

        final GossipEvent otherParent = chooseOtherParent(parentCandidates);

        final long maxParentGeneration = Math.max(selfParent.getGeneration(), otherParent.getGeneration());
        final long eventGeneration = maxParentGeneration + 1;
        maxGeneration = Math.max(maxGeneration, eventGeneration);

        return new TestingEventBuilder(random)
                .setCreatorId(eventCreator)
                .setSelfParent(selfParent)
                .setOtherParent(otherParent)
                .build();
    }

    /**
     * Check if an event has been emitted or is ancient
     *
     * @param event       the event to check
     * @param eventWindow the event window defining ancient.
     * @return true if the event has been emitted or is ancient, false otherwise
     */
    private static boolean eventEmittedOrAncient(
            @NonNull final EventDescriptor event,
            @NonNull final EventWindow eventWindow,
            @NonNull final Collection<Hash> emittedEvents) {

        return emittedEvents.contains(event.getHash()) || eventWindow.isAncient(event);
    }

    /**
     * Assert that an event should have been emitted by the orphan buffer, based on its parents being either emitted or
     * ancient.
     *
     * @param event         the event to check
     * @param eventWindow   the event window
     * @param emittedEvents the events that have been emitted so far
     */
    private static void assertValidParents(
            @NonNull final GossipEvent event,
            @NonNull final EventWindow eventWindow,
            @NonNull final Collection<Hash> emittedEvents) {
        for (final EventDescriptor parent : event.getAllParents()) {
            assertTrue(eventEmittedOrAncient(parent, eventWindow, emittedEvents));
        }
    }

    /**
     * Choose an other parent from the given list of candidates. This method chooses from the last
     * PARENT_SELECTION_WINDOW events in the list.
     *
     * @param parentCandidates the list of candidates
     * @return the chosen other parent
     */
    private GossipEvent chooseOtherParent(@NonNull final List<GossipEvent> parentCandidates) {
        final int startIndex = Math.max(0, parentCandidates.size() - PARENT_SELECTION_WINDOW);
        return parentCandidates.get(
                startIndex + random.nextInt(Math.min(PARENT_SELECTION_WINDOW, parentCandidates.size())));
    }

    @BeforeEach
    void setup() {
        random = getRandomPrintSeed();

        final List<GossipEvent> parentCandidates = new ArrayList<>();
        final Map<NodeId, GossipEvent> tips = new HashMap<>();

        intakeEvents = new ArrayList<>();

        for (long i = 0; i < TEST_EVENT_COUNT; i++) {
            final GossipEvent newEvent = createRandomEvent(parentCandidates, tips);

            parentCandidates.add(newEvent);
            intakeEvents.add(newEvent);
        }

        Collections.shuffle(intakeEvents, random);

        eventsExitedIntakePipeline = new AtomicLong(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("Test standard orphan buffer operation")
    void standardOperation(final boolean useBirthRoundForAncient) {

        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    eventsExitedIntakePipeline.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());
        final DefaultOrphanBuffer orphanBuffer = new DefaultOrphanBuffer(
                TestPlatformContextBuilder.create()
                        .withConfiguration(new TestConfigBuilder()
                                .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, useBirthRoundForAncient)
                                .getOrCreateConfig())
                        .build(),
                intakeEventCounter);

        long minimumGenerationNonAncient = 0;
        long latestConsensusRound = ConsensusConstants.ROUND_FIRST;

        // increase minimum generation non-ancient at the approximate rate that event generations are increasing
        // this means that roughly half of the events will be ancient before they are received from intake
        final float averageGenerationAdvancement = (float) maxGeneration / TEST_EVENT_COUNT;

        // events that have been emitted from the orphan buffer
        final Collection<Hash> emittedEvents = new HashSet<>();

        for (final GossipEvent intakeEvent : intakeEvents) {
            final List<GossipEvent> unorphanedEvents = new ArrayList<>();

            unorphanedEvents.addAll(orphanBuffer.handleEvent(intakeEvent));

            // add some randomness to step size, so minimumGenerationNonAncient doesn't always just increase by 1
            final int stepRandomness = Math.round(random.nextFloat() * MAX_GENERATION_STEP);
            if (random.nextFloat() < averageGenerationAdvancement / stepRandomness) {
                minimumGenerationNonAncient += stepRandomness;
            }
            // simulate advancing consensus rounds periodically
            latestConsensusRound += maybeAdvanceRound.apply(random);
            final AncientMode ancientMode =
                    useBirthRoundForAncient ? AncientMode.BIRTH_ROUND_THRESHOLD : AncientMode.GENERATION_THRESHOLD;
            final EventWindow eventWindow = new EventWindow(
                    latestConsensusRound,
                    ancientMode.selectIndicator(
                            minimumGenerationNonAncient, Math.max(1, latestConsensusRound - 26 + 1)),
                    1 /* ignored in this context */,
                    ancientMode);
            unorphanedEvents.addAll(orphanBuffer.setEventWindow(eventWindow));

            for (final GossipEvent unorphanedEvent : unorphanedEvents) {
                assertValidParents(unorphanedEvent, eventWindow, emittedEvents);
                emittedEvents.add(unorphanedEvent.getHash());
            }
        }

        // either events exit the pipeline in the orphan buffer and are never emitted, or they are emitted and exit
        // the pipeline at a later stage
        assertEquals(TEST_EVENT_COUNT, eventsExitedIntakePipeline.get() + emittedEvents.size());
        assertEquals(0, orphanBuffer.getCurrentOrphanCount());
    }

    @Test
    @DisplayName("Test Parent Iterator")
    void testParentIterator() {
        final GossipEvent event = mock(GossipEvent.class);

        final EventDescriptor selfParent =
                new EventDescriptor(new Hash(), new NodeId(0), 0, EventConstants.BIRTH_ROUND_UNDEFINED);
        final EventDescriptor otherParent1 =
                new EventDescriptor(new Hash(), new NodeId(1), 1, EventConstants.BIRTH_ROUND_UNDEFINED);
        final EventDescriptor otherParent2 =
                new EventDescriptor(new Hash(), new NodeId(2), 2, EventConstants.BIRTH_ROUND_UNDEFINED);
        final EventDescriptor otherParent3 =
                new EventDescriptor(new Hash(), new NodeId(3), 3, EventConstants.BIRTH_ROUND_UNDEFINED);
        final List<EventDescriptor> otherParents = new ArrayList<>();
        otherParents.add(otherParent1);
        otherParents.add(otherParent2);
        otherParents.add(otherParent3);

        final BaseEventHashedData eventBase = mock(BaseEventHashedData.class);
        when(eventBase.getSelfParent()).thenReturn(selfParent);
        when(eventBase.getOtherParents()).thenReturn(otherParents);
        when(event.getHashedData()).thenReturn(eventBase);

        final ParentIterator iterator = new ParentIterator(event);

        assertEquals(selfParent, iterator.next(), "The first parent should be the self parent");
        int index = 0;
        while (iterator.hasNext()) {
            assertEquals(otherParents.get(index++), iterator.next(), "The next parent should be the next other parent");
        }
    }
}
