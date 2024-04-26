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

package com.swirlds.platform.event.stale;

import static com.swirlds.platform.event.AncientMode.BIRTH_ROUND_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StaleEventDetectorTests {

    @Test
    void throwIfInitialEventWindowNotSetTest() {
        final Randotron randotron = Randotron.create();
        final NodeId selfId = new NodeId(randotron.nextPositiveLong());

        final Configuration configuration = new TestConfigBuilder()
                .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                .getOrCreateConfig();
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();
        final StaleEventDetector detector = new DefaultStaleEventDetector(platformContext, selfId);

        final GossipEvent event = new TestingEventBuilder(randotron).build();

        assertThrows(IllegalStateException.class, () -> detector.addSelfEvent(event));
    }

    @Test
    void eventIsStaleBeforeAddedTest() {
        final Randotron randotron = Randotron.create();
        final NodeId selfId = new NodeId(randotron.nextPositiveLong());

        final Configuration configuration = new TestConfigBuilder()
                .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                .getOrCreateConfig();
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();
        final StaleEventDetector detector = new DefaultStaleEventDetector(platformContext, selfId);

        final long ancientThreshold = randotron.nextPositiveLong() + 100;
        final long eventBirthRound = ancientThreshold - randotron.nextLong(100);

        final GossipEvent event = new TestingEventBuilder(randotron)
                .setCreatorId(selfId)
                .setBirthRound(eventBirthRound)
                .build();

        detector.setInitialEventWindow(new EventWindow(
                randotron.nextPositiveInt(), ancientThreshold, randotron.nextPositiveLong(), BIRTH_ROUND_THRESHOLD));

        final List<GossipEvent> staleEvents = detector.addSelfEvent(event);
        assertEquals(1, staleEvents.size());
        assertSame(event, staleEvents.getFirst());
    }

    /**
     * Construct a consensus round.
     *
     * @param randotron        a source of randomness
     * @param events           events that will reach consensus in this round
     * @param ancientThreshold the ancient threshold for this round
     * @return a consensus round
     */
    @NonNull
    private ConsensusRound createConsensusRound(
            @NonNull final Randotron randotron, @NonNull final List<GossipEvent> events, final long ancientThreshold) {
        final List<EventImpl> eventImpls = new ArrayList<>();
        for (final GossipEvent consensusEvent : events) {
            eventImpls.add(new EventImpl(consensusEvent.getHashedData(), consensusEvent.getUnhashedData()));
        }

        final EventWindow eventWindow = new EventWindow(
                randotron.nextPositiveLong(), ancientThreshold, randotron.nextPositiveLong(), BIRTH_ROUND_THRESHOLD);

        return new ConsensusRound(
                mock(AddressBook.class),
                eventImpls,
                mock(EventImpl.class),
                mock(Generations.class),
                eventWindow,
                mock(ConsensusSnapshot.class));
    }

    @Test
    void randomEventsTest() {
        final Randotron randotron = Randotron.create();
        final NodeId selfId = new NodeId(randotron.nextPositiveLong());

        final Configuration configuration = new TestConfigBuilder()
                .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                .getOrCreateConfig();
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();
        final StaleEventDetector detector = new DefaultStaleEventDetector(platformContext, selfId);

        final Set<GossipEvent> detectedStaleEvents = new HashSet<>();
        final Set<GossipEvent> expectedStaleEvents = new HashSet<>();
        final List<GossipEvent> consensusEvents = new ArrayList<>();

        long currentAncientThreshold = randotron.nextLong(100, 1_000);
        detector.setInitialEventWindow(new EventWindow(
                randotron.nextPositiveLong(),
                currentAncientThreshold,
                randotron.nextPositiveLong(),
                BIRTH_ROUND_THRESHOLD));

        for (int i = 0; i < 10_000; i++) {
            final boolean selfEvent = randotron.nextBoolean(0.25);
            final NodeId eventCreator = selfEvent ? selfId : new NodeId(randotron.nextPositiveLong());

            final TestingEventBuilder eventBuilder = new TestingEventBuilder(randotron).setCreatorId(eventCreator);

            final boolean eventIsAncientBeforeAdded = randotron.nextBoolean(0.01);
            if (eventIsAncientBeforeAdded) {
                eventBuilder.setBirthRound(currentAncientThreshold - randotron.nextLong(1, 100));
            } else {
                eventBuilder.setBirthRound(currentAncientThreshold + randotron.nextLong(3));
            }
            final GossipEvent event = eventBuilder.build();

            final boolean willReachConsensus = !eventIsAncientBeforeAdded && randotron.nextBoolean(0.8);

            if (willReachConsensus) {
                consensusEvents.add(event);
            }

            if (selfEvent && (eventIsAncientBeforeAdded || !willReachConsensus)) {
                expectedStaleEvents.add(event);
            }

            if (selfEvent) {
                detectedStaleEvents.addAll(detector.addSelfEvent(event));
            }

            // Once in a while, permit a round to "reach consensus"
            if (randotron.nextBoolean(0.01)) {
                currentAncientThreshold += randotron.nextLong(3);

                final ConsensusRound consensusRound =
                        createConsensusRound(randotron, consensusEvents, currentAncientThreshold);

                detectedStaleEvents.addAll(detector.addConsensusRound(consensusRound));
                consensusEvents.clear();
            }
        }

        // Create a final round with all remaining consensus events. Move ancient threshold far enough forward
        // to flush out all events we expect to eventually become stale.
        currentAncientThreshold += randotron.nextLong(1_000, 10_000);
        final ConsensusRound consensusRound = createConsensusRound(randotron, consensusEvents, currentAncientThreshold);
        detectedStaleEvents.addAll(detector.addConsensusRound(consensusRound));

        assertEquals(expectedStaleEvents.size(), detectedStaleEvents.size());
    }

    @Test
    void clearTest() {
        final Randotron randotron = Randotron.create();
        final NodeId selfId = new NodeId(randotron.nextPositiveLong());

        final Configuration configuration = new TestConfigBuilder()
                .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                .getOrCreateConfig();
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(configuration)
                .build();
        final StaleEventDetector detector = new DefaultStaleEventDetector(platformContext, selfId);

        final long ancientThreshold1 = randotron.nextPositiveInt() + 100;
        final long eventBirthRound1 = ancientThreshold1 + randotron.nextPositiveInt(10);

        final GossipEvent event1 = new TestingEventBuilder(randotron)
                .setCreatorId(selfId)
                .setBirthRound(eventBirthRound1)
                .build();

        detector.setInitialEventWindow(new EventWindow(
                randotron.nextPositiveInt(), ancientThreshold1, randotron.nextPositiveLong(), BIRTH_ROUND_THRESHOLD));

        assertEquals(0, detector.addSelfEvent(event1).size());

        detector.clear();

        // Adding an event again before setting the event window should throw.
        assertThrows(IllegalStateException.class, () -> detector.addSelfEvent(event1));

        // Setting the ancient threshold after the original event should not cause it to come back as stale.
        final long ancientThreshold2 = eventBirthRound1 + randotron.nextPositiveInt();
        detector.setInitialEventWindow(new EventWindow(
                randotron.nextPositiveInt(), ancientThreshold2, randotron.nextPositiveLong(), BIRTH_ROUND_THRESHOLD));

        // Verify that we get otherwise normal behavior after the clear.

        final long eventBirthRound2 = ancientThreshold2 + randotron.nextPositiveInt(10);
        final GossipEvent event2 = new TestingEventBuilder(randotron)
                .setCreatorId(selfId)
                .setBirthRound(eventBirthRound2)
                .build();

        assertEquals(0, detector.addSelfEvent(event2).size());

        final long ancientThreshold3 = eventBirthRound2 + randotron.nextPositiveInt(10);
        final ConsensusRound consensusRound = createConsensusRound(randotron, List.of(), ancientThreshold3);
        final List<GossipEvent> staleEvents = detector.addConsensusRound(consensusRound);
        assertEquals(1, staleEvents.size());
        assertSame(event2, staleEvents.getFirst());
    }
}
