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

package com.swirlds.platform.event.validation;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.platform.system.events.EventConstants.GENERATION_UNDEFINED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.EventConfig_;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Tests for {@link DefaultInternalEventValidator}
 */
class InternalEventValidatorTests {
    private AtomicLong exitedIntakePipelineCount;
    private Random random;
    private InternalEventValidator multinodeValidator;
    private InternalEventValidator singleNodeValidator;

    @BeforeEach
    void setup() {
        random = getRandomPrintSeed();

        exitedIntakePipelineCount = new AtomicLong(0);
        final IntakeEventCounter intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    exitedIntakePipelineCount.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        final Time time = new FakeTime();

        // Adding the configuration to use the birth round as the ancient threshold for testing.
        // The conditions where it is false is covered by the case where it is set to true.
        final PlatformContext platformContext = TestPlatformContextBuilder.create()
                .withConfiguration(new TestConfigBuilder()
                        .withValue(EventConfig_.USE_BIRTH_ROUND_ANCIENT_THRESHOLD, true)
                        .getOrCreateConfig())
                .withTime(time)
                .build();

        multinodeValidator = new DefaultInternalEventValidator(platformContext, false, intakeEventCounter);
        singleNodeValidator = new DefaultInternalEventValidator(platformContext, true, intakeEventCounter);
    }

    private static GossipEvent generateEvent(
            @NonNull final EventDescriptor self,
            @Nullable final EventDescriptor selfParent,
            @Nullable final EventDescriptor otherParent,
            final int totalTransactionBytes) {

        final ConsensusTransactionImpl[] transactions = new ConsensusTransactionImpl[100];
        for (int index = 0; index < transactions.length; index++) {
            transactions[index] = mock(ConsensusTransactionImpl.class);
            when(transactions[index].getSerializedLength()).thenReturn(totalTransactionBytes / transactions.length);
        }

        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getSelfParentHash()).thenReturn(selfParent == null ? null : selfParent.getHash());
        when(hashedData.getOtherParentHash()).thenReturn(otherParent == null ? null : otherParent.getHash());
        when(hashedData.getSelfParentGen())
                .thenReturn(selfParent == null ? GENERATION_UNDEFINED : selfParent.getGeneration());
        when(hashedData.getOtherParentGen())
                .thenReturn(otherParent == null ? GENERATION_UNDEFINED : otherParent.getGeneration());
        when(hashedData.getTransactions()).thenReturn(transactions);
        when(hashedData.getBirthRound()).thenReturn(self.getBirthRound());
        when(hashedData.getGeneration()).thenReturn(self.getGeneration());
        when(hashedData.getSelfParent()).thenReturn(selfParent);
        // FUTURE WORK: Extend to support multiple other parents.
        when(hashedData.getOtherParents())
                .thenReturn(otherParent == null ? Collections.EMPTY_LIST : Collections.singletonList(otherParent));

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getSignature()).thenReturn(Bytes.EMPTY);
        when(event.getGeneration()).thenReturn(self.getGeneration());
        when(event.getDescriptor()).thenReturn(self);

        return event;
    }

    private static GossipEvent generateGoodEvent(@NonNull final Random random, final int totalTransactionBytes) {
        return generateEvent(
                new EventDescriptor(randomHash(random), new NodeId(0), 7, 1),
                new EventDescriptor(randomHash(random), new NodeId(0), 5, 1),
                new EventDescriptor(randomHash(random), new NodeId(1), 6, 1),
                totalTransactionBytes);
    }

    @Test
    @DisplayName("An event with null signature is invalid")
    void nullSignatureData() {
        final GossipEvent event = Mockito.spy(new TestingEventBuilder(random).build());
        when(event.getSignature()).thenReturn(null);

        assertNull(multinodeValidator.validateEvent(event));
        assertNull(singleNodeValidator.validateEvent(event));

        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with too many transaction bytes is invalid")
    void tooManyTransactionBytes() {
        // default max is 245_760 bytes
        final GossipEvent event = new TestingEventBuilder(random)
                .setTransactionSize(100)
                .setAppTransactionCount(5000)
                .setSystemTransactionCount(0)
                .build();

        assertNull(multinodeValidator.validateEvent(event));
        assertNull(singleNodeValidator.validateEvent(event));

        assertEquals(2, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with parent inconsistency is invalid")
    void inconsistentParents() {
        // self parent has invalid generation.
        final GossipEvent invalidSelfParentGeneration = new TestingEventBuilder(random)
                .setSelfParent(new TestingEventBuilder(random).build())
                .overrideSelfParentGeneration(GENERATION_UNDEFINED).build();

        // other parent has invalid generation.
        final GossipEvent invalidOtherParentGeneration = new TestingEventBuilder(random)
                .setOtherParent(new TestingEventBuilder(random).build())
                .overrideOtherParentGeneration(GENERATION_UNDEFINED).build();

        assertNull(multinodeValidator.validateEvent(invalidSelfParentGeneration));
        assertNull(multinodeValidator.validateEvent(invalidOtherParentGeneration));

        assertNull(singleNodeValidator.validateEvent(invalidSelfParentGeneration));
        assertNull(singleNodeValidator.validateEvent(invalidOtherParentGeneration));

        assertEquals(4, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event with identical parents is only valid in a single node network")
    void identicalParents() {
        final GossipEvent parent = new TestingEventBuilder(random).build();
        final GossipEvent event = new TestingEventBuilder(random)
                .setSelfParent(parent)
                .setOtherParent(parent)
                .build();

        assertNull(multinodeValidator.validateEvent(event));
        assertNotEquals(null, singleNodeValidator.validateEvent(event));

        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("An event must have a birth round greater than or equal to the max of all parent birth rounds.")
    void invalidBirthRound() {
        final GossipEvent selfParent1 = new TestingEventBuilder(random).setCreatorId(new NodeId(0)).setBirthRound(5).build();
        final GossipEvent otherParent1 = new TestingEventBuilder(random).setCreatorId(new NodeId(1)).setBirthRound(7).build();
        final GossipEvent selfParent2 = new TestingEventBuilder(random).setCreatorId(new NodeId(0)).setBirthRound(7).build();
        final GossipEvent otherParent2 = new TestingEventBuilder(random).setCreatorId(new NodeId(1)).setBirthRound(5).build();

        for (final InternalEventValidator validator : List.of(multinodeValidator, singleNodeValidator)) {
            assertNull(validator.validateEvent(new TestingEventBuilder(random).setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent1)
                    .setOtherParent(otherParent1)
                    .setBirthRound(6).build()));
            assertNull(validator.validateEvent(new TestingEventBuilder(random).setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent2)
                    .setOtherParent(otherParent2)
                    .setBirthRound(6).build()));
            assertNull(validator.validateEvent(new TestingEventBuilder(random).setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent1)
                    .setOtherParent(otherParent1)
                    .setBirthRound(4).build()));
            assertNull(validator.validateEvent(new TestingEventBuilder(random).setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent2)
                    .setOtherParent(otherParent2)
                    .setBirthRound(4).build()));
            assertNotNull(validator.validateEvent(new TestingEventBuilder(random).setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent1)
                    .setOtherParent(otherParent1)
                    .setBirthRound(7).build()));
            assertNotNull(validator.validateEvent(new TestingEventBuilder(random).setCreatorId(new NodeId(0))
                    .setSelfParent(selfParent2)
                    .setOtherParent(otherParent2)
                    .setBirthRound(7).build()));
        }

        assertEquals(8, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Test that an event with no issues passes validation")
    void successfulValidation() {
        final GossipEvent normalEvent = new TestingEventBuilder(random)
                .setSelfParent(new TestingEventBuilder(random).build())
                .setOtherParent(new TestingEventBuilder(random).build())
                .build();
        final GossipEvent missingSelfParent = new TestingEventBuilder(random)
                .setSelfParent(null)
                .setOtherParent(new TestingEventBuilder(random).build())
                .build();

        final GossipEvent missingOtherParent = new TestingEventBuilder(random)
                .setSelfParent(new TestingEventBuilder(random).build())
                .setOtherParent(null)
                .build();

        assertNotEquals(null, multinodeValidator.validateEvent(normalEvent));
        assertNotEquals(null, multinodeValidator.validateEvent(missingSelfParent));
        assertNotEquals(null, multinodeValidator.validateEvent(missingOtherParent));

        assertNotEquals(null, singleNodeValidator.validateEvent(normalEvent));
        assertNotEquals(null, singleNodeValidator.validateEvent(missingSelfParent));
        assertNotEquals(null, singleNodeValidator.validateEvent(missingOtherParent));

        assertEquals(0, exitedIntakePipelineCount.get());
    }
}
