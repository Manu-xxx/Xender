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

package com.swirlds.platform.event.validation;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.PublicKey;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EventSignatureValidatorTests {
    private Random random;
    private PlatformContext platformContext;
    private FakeTime time;
    private AtomicInteger consumedEventCount;
    private Consumer<GossipEvent> eventConsumer;
    private AtomicLong exitedIntakePipelineCount;
    private IntakeEventCounter intakeEventCounter;

    private AddressBook previousAddressBook;
    private AddressBook currentAddressBook;

    /**
     * A verifier that always returns true.
     */
    private final SignatureVerifier trueVerifier = (data, signature, publicKey) -> true;

    /**
     * A verifier that always returns false.
     */
    private final SignatureVerifier falseVerifier = (data, signature, publicKey) -> false;

    private EventSignatureValidator validatorWithTrueVerifier;
    private EventSignatureValidator validatorWithFalseVerifier;

    private SoftwareVersion defaultVersion;

    /**
     * This address belongs to a node that is placed in the previous address book.
     */
    private Address previousNodeAddress;

    /**
     * This address belongs to a node that is placed in the current address book.
     */
    private Address currentNodeAddress;

    /**
     * Generate a mock address, with enough elements mocked to support the signature validation.
     *
     * @param nodeId the node id to use for the address
     * @return a mock address
     */
    private Address generateMockAddress(final @NonNull NodeId nodeId) {
        final PublicKey publicKey = mock(PublicKey.class);
        final SerializablePublicKey serializablePublicKey = mock(SerializablePublicKey.class);
        when(serializablePublicKey.getPublicKey()).thenReturn(publicKey);

        return new Address(nodeId, "", "", 10, null, 77, null, 88, serializablePublicKey, null, null, "");
    }

    /**
     * Generate a mock event, with enough elements mocked to support the signature validation.
     *
     * @param version   the software version to use for the event
     * @param hash      the hash to use for the event
     * @param creatorId the creator id to use for the event
     * @return a mock event
     */
    final GossipEvent generateMockEvent(
            @NonNull final SoftwareVersion version, @NonNull final Hash hash, @NonNull final NodeId creatorId) {
        final BaseEventHashedData hashedData = mock(BaseEventHashedData.class);
        when(hashedData.getSoftwareVersion()).thenReturn(version);
        when(hashedData.getHash()).thenReturn(hash);
        when(hashedData.getCreatorId()).thenReturn(creatorId);

        final GossipEvent event = mock(GossipEvent.class);
        when(event.getHashedData()).thenReturn(hashedData);
        when(event.getUnhashedData()).thenReturn(mock(BaseEventUnhashedData.class));

        return event;
    }

    /**
     * Assert either a passing or failing validation for a given event
     *
     * @param event      the event to validate
     * @param expectPass whether the event is expected to pass validation
     */
    private void assertValidationResult(
            final boolean useTrueVerifier, @NonNull final GossipEvent event, final boolean expectPass) {
        int expectedConsumedEventCount = consumedEventCount.get();
        long expectedExitedIntakePipelineCount = exitedIntakePipelineCount.get();

        if (expectPass) {
            expectedConsumedEventCount++;
        } else {
            expectedExitedIntakePipelineCount++;
        }

        final EventSignatureValidator validator =
                useTrueVerifier ? validatorWithTrueVerifier : validatorWithFalseVerifier;
        validator.handleEvent(event);

        assertEquals(expectedConsumedEventCount, consumedEventCount.get());
        assertEquals(expectedExitedIntakePipelineCount, exitedIntakePipelineCount.get());
    }

    @BeforeEach
    void setup() {
        random = getRandomPrintSeed();
        platformContext = TestPlatformContextBuilder.create().build();
        time = new FakeTime();

        consumedEventCount = new AtomicInteger(0);
        eventConsumer = event -> {
            consumedEventCount.incrementAndGet();
        };

        exitedIntakePipelineCount = new AtomicLong(0);
        intakeEventCounter = mock(IntakeEventCounter.class);
        doAnswer(invocation -> {
                    exitedIntakePipelineCount.incrementAndGet();
                    return null;
                })
                .when(intakeEventCounter)
                .eventExitedIntakePipeline(any());

        // create two addresses, one for the previous address book and one for the current address book
        previousNodeAddress = generateMockAddress(new NodeId(66));
        currentNodeAddress = generateMockAddress(new NodeId(77));

        previousAddressBook = new AddressBook(List.of(previousNodeAddress));
        currentAddressBook = new AddressBook(List.of(currentNodeAddress));

        defaultVersion = new BasicSoftwareVersion(2);

        validatorWithTrueVerifier = new EventSignatureValidator(
                platformContext,
                time,
                trueVerifier,
                defaultVersion,
                previousAddressBook,
                currentAddressBook,
                eventConsumer,
                intakeEventCounter);

        validatorWithFalseVerifier = new EventSignatureValidator(
                platformContext,
                time,
                falseVerifier,
                defaultVersion,
                previousAddressBook,
                currentAddressBook,
                eventConsumer,
                intakeEventCounter);
    }

    @Test
    @DisplayName("Events with higher version than the app should always fail validation")
    void irreconcilableVersions() {
        final GossipEvent event =
                generateMockEvent(new BasicSoftwareVersion(3), randomHash(random), currentNodeAddress.getNodeId());

        assertValidationResult(true, event, false);
    }

    @Test
    @DisplayName("Lower version event with missing previous address book")
    void versionMismatchWithNullPreviousAddressBook() {
        final EventSignatureValidator signatureValidator = new EventSignatureValidator(
                platformContext,
                time,
                trueVerifier,
                defaultVersion,
                null,
                currentAddressBook,
                eventConsumer,
                intakeEventCounter);

        final GossipEvent event =
                generateMockEvent(new BasicSoftwareVersion(1), randomHash(random), previousNodeAddress.getNodeId());
        signatureValidator.handleEvent(event);

        assertEquals(0, consumedEventCount.get());
        assertEquals(1, exitedIntakePipelineCount.get());
    }

    @Test
    @DisplayName("Node is missing from the applicable address book")
    void applicableAddressBookMissingNode() {
        // this creator isn't in the current address book, so verification will fail
        final GossipEvent event =
                generateMockEvent(defaultVersion, randomHash(random), previousNodeAddress.getNodeId());

        assertValidationResult(true, event, false);
    }

    @Test
    @DisplayName("Node has a null public key")
    void missingPublicKey() {
        final NodeId nodeId = new NodeId(88);
        final SerializablePublicKey serializablePublicKey = mock(SerializablePublicKey.class);
        when(serializablePublicKey.getPublicKey()).thenReturn(null);
        final Address nodeAddress =
                new Address(nodeId, "", "", 10, null, 77, null, 88, serializablePublicKey, null, null, "");

        currentAddressBook.add(nodeAddress);

        final GossipEvent event = generateMockEvent(defaultVersion, randomHash(random), nodeId);

        assertValidationResult(true, event, false);
    }

    @Test
    @DisplayName("Event passes validation if the signature verifies")
    void validSignature() {
        // both the event and the app have the same version, so the currentAddressBook will be selected
        final GossipEvent event1 =
                generateMockEvent(defaultVersion, randomHash(random), currentNodeAddress.getNodeId());
        assertValidationResult(true, event1, true);

        // event2 is from a previous version, so the previous address book will be selected
        final GossipEvent event2 =
                generateMockEvent(new BasicSoftwareVersion(1), randomHash(random), previousNodeAddress.getNodeId());
        assertValidationResult(true, event2, true);
    }

    @Test
    @DisplayName("Event fails validation if the signature does not verify")
    void verificationFails() {
        final GossipEvent event = generateMockEvent(defaultVersion, randomHash(random), currentNodeAddress.getNodeId());

        assertValidationResult(true, event, true);
        assertValidationResult(false, event, false);
    }

    @Test
    @DisplayName("Ancient events are discarded")
    void ancientEvent() {
        final GossipEvent event = generateMockEvent(defaultVersion, randomHash(random), currentNodeAddress.getNodeId());

        assertValidationResult(true, event, true);
        validatorWithTrueVerifier.setMinimumGenerationNonAncient(100L);
        assertValidationResult(true, event, false);
    }
}
