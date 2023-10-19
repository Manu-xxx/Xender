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

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.INVALID_EVENT_ERROR;
import static com.swirlds.platform.consensus.GraphGenerations.FIRST_GENERATION;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.LongAccumulator;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.PublicKey;

/**
 * A collection of static methods for validating events
 */
public class EventValidationChecks {
    /**
     * Hidden constructor
     */
    private EventValidationChecks() {}

    /**
     * Determine whether a given event has a valid creation time.
     *
     * @param event             the event to be validated
     * @param logger            a logger for validation errors
     * @param metricAccumulator for counting occurrences of invalid event creation times
     * @return true if the creation time of the event is strictly after the creation time of its self-parent, otherwise false
     */
    public static boolean isValidTimeCreated(
            @NonNull final EventImpl event,
            @NonNull final RateLimitedLogger logger,
            @NonNull final LongAccumulator metricAccumulator) {
        final EventImpl selfParent = event.getSelfParent();

        final boolean validTimeCreated =
                selfParent == null || event.getTimeCreated().isAfter(selfParent.getTimeCreated());

        if (!validTimeCreated) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Event timeCreated is invalid. Event: {}, Time created: {}, Parent created: {}",
                    event.toMediumString(),
                    event.getTimeCreated(),
                    selfParent.getTimeCreated());
            metricAccumulator.update(1);
        }

        return validTimeCreated;
    }

    /**
     * Determine whether a given event has valid parents.
     *
     * @param event             the event to be validated
     * @param singleNodeNetwork true if the network is a single node network, otherwise false
     * @param logger            a logger for validation errors
     * @param metricAccumulator for counting occurrences of invalid event parents
     * @return true if the event has valid parents, otherwise false
     */
    public static boolean areParentsValid(
            @NonNull final EventImpl event,
            final boolean singleNodeNetwork,
            @NonNull final RateLimitedLogger logger,
            @NonNull final LongAccumulator metricAccumulator) {

        final BaseEventHashedData hashedData = event.getHashedData();

        final Hash selfParentHash = hashedData.getSelfParentHash();
        final long selfParentGeneration = hashedData.getSelfParentGen();

        // If a parent hash is missing, then the generation must also be invalid.
        // If a parent hash is not missing, then the generation must be valid.
        if ((selfParentHash == null) != (selfParentGeneration < FIRST_GENERATION)) {
            logger.error(INVALID_EVENT_ERROR.getMarker(), "Self parent hash / generation mismatch: {}", event);
            metricAccumulator.update(1);
            return false;
        }

        final Hash otherParentHash = hashedData.getOtherParentHash();
        final long otherParentGeneration = hashedData.getOtherParentGen();

        if ((otherParentHash == null) != (otherParentGeneration < FIRST_GENERATION)) {
            logger.error(INVALID_EVENT_ERROR.getMarker(), "Other parent hash / generation mismatch: {}", event);
            metricAccumulator.update(1);
            return false;
        }

        if (!singleNodeNetwork && (selfParentHash != null) && selfParentHash.equals(otherParentHash)) {
            logger.error(INVALID_EVENT_ERROR.getMarker(), "Both parents have the same hash: {} ", event);
            metricAccumulator.update(1);
            return false;
        }

        return true;
    }

    /**
     * Determine whether the previous address book or the current address book should be used to verify an event's signature.
     * <p>
     * Logs an error and returns null if an applicable address book cannot be selected
     *
     * @param event                  the event to be validated
     * @param currentSoftwareVersion the current software version
     * @param previousAddressBook    the previous address book
     * @param currentAddressBook     the current address book
     * @param logger                 a logger for validation errors
     * @return the applicable address book, or null if an applicable address book cannot be selected
     */
    @Nullable
    private static AddressBook determineApplicableAddressBook(
            @NonNull final GossipEvent event,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @Nullable final AddressBook previousAddressBook,
            @NonNull final AddressBook currentAddressBook,
            @NonNull final RateLimitedLogger logger) {

        final int softwareComparison =
                currentSoftwareVersion.compareTo(event.getHashedData().getSoftwareVersion());
        if (softwareComparison < 0) {
            // current software version is less than event software version
            logger.error(
                    EXCEPTION.getMarker(),
                    "Cannot validate events for software version {} that is greater than the current software version {}",
                    event.getHashedData().getSoftwareVersion(),
                    currentSoftwareVersion);
            return null;
        } else if (softwareComparison > 0) {
            // current software version is greater than event software version
            if (previousAddressBook == null) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Cannot validate events for software version {} that is less than the current software version {} without a previous address book",
                        event.getHashedData().getSoftwareVersion(),
                        currentSoftwareVersion);
                return null;
            }

            return previousAddressBook;
        } else {
            // current software version is equal to event software version
            return currentAddressBook;
        }
    }

    /**
     * Determine whether a given event has a valid signature.
     *
     * @param event                  the event to be validated
     * @param signatureVerifier      a signature verifier
     * @param currentSoftwareVersion the current software version
     * @param previousAddressBook    the previous address book
     * @param currentAddressBook     the current address book
     * @param logger                 a logger for validation errors
     * @param metricAccumulator      for counting occurrences of invalid event signatures
     * @return true if the event has a valid signature, otherwise false
     */
    public static boolean isSignatureValid(
            @NonNull final GossipEvent event,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final SoftwareVersion currentSoftwareVersion,
            @Nullable final AddressBook previousAddressBook,
            @NonNull final AddressBook currentAddressBook,
            @NonNull final RateLimitedLogger logger,
            @NonNull final LongAccumulator metricAccumulator) {

        final AddressBook applicableAddressBook = determineApplicableAddressBook(
                event, currentSoftwareVersion, previousAddressBook, currentAddressBook, logger);

        if (applicableAddressBook == null) {
            // this occurrence was already logged while attempting to determine the applicable address book
            metricAccumulator.update(1);
            return false;
        }

        if (!applicableAddressBook.contains(event.getHashedData().getCreatorId())) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Node {} doesn't exist in applicable address book. Event: {}",
                    event.getHashedData().getCreatorId(),
                    event);
            metricAccumulator.update(1);
            return false;
        }

        final PublicKey publicKey = applicableAddressBook
                .getAddress(event.getHashedData().getCreatorId())
                .getSigPublicKey();

        if (publicKey == null) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Cannot find publicKey for creator with ID: {}",
                    event.getHashedData().getCreatorId());
            metricAccumulator.update(1);
            return false;
        }

        final boolean isSignatureValid = signatureVerifier.verifySignature(
                event.getHashedData().getHash().getValue(),
                event.getUnhashedData().getSignature(),
                publicKey);

        if (!isSignatureValid) {
            logger.error(
                    INVALID_EVENT_ERROR.getMarker(),
                    "Event failed signature check. Event: {}, Signature: {}, Hash: {}",
                    event,
                    CommonUtils.hex(event.getUnhashedData().getSignature()),
                    event.getHashedData().getHash());
            metricAccumulator.update(1);
        }

        return isSignatureValid;
    }
}
