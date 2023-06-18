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

package com.swirlds.platform.event.tipset;

import static com.swirlds.platform.event.EventConstants.CREATOR_ID_UNDEFINED;
import static com.swirlds.platform.event.EventConstants.GENERATION_UNDEFINED;
import static com.swirlds.platform.event.tipset.TipsetUtils.buildDescriptor;
import static com.swirlds.platform.event.tipset.TipsetUtils.getParentDescriptors;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.event.EventDescriptor;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

// TODO:
//  - metrics
//  - convert all constants to settings
//  - tipset score calculator bug unit test
//  - unit tests
//  - resolve remaining TODOs
//  - figure out to handle event.getUnhashedData().getOtherId()...
//                possibly needs hip to fix (or perhaps we look up the data)
//  - hand test all gossip flavors
//  - PCES compatability
//  - migrating between networks

/**
 * Responsible for creating new events using the tipset algorithm.
 */
public class TipsetEventCreator { // TODO test

    private final Cryptography cryptography;
    private final Time time;
    private final Random random;
    private final Signer signer;
    private final AddressBook addressBook;
    private final NodeId selfId;
    private final TipsetBuilder tipsetBuilder;
    private final TipsetScoreCalculator tipsetScoreCalculator;
    private final ChildlessEventTracker childlessOtherEventTracker;
    private final TransactionSupplier transactionSupplier;
    private final SoftwareVersion softwareVersion;
    private final double antiBullyingFactor;
    private final TipsetMetrics tipsetMetrics;

    /**
     * The last event created by this node.
     */
    private EventDescriptor lastSelfEvent;

    /**
     * The timestamp of the last event created by this node.
     */
    private Instant lastSelfEventCreationTime;

    /**
     * The number of transactions in the last event created by this node.
     */
    private int lastSelfEventTransactionCount;

    /**
     * Create a new tipset event creator.
     *
     * @param platformContext     the platform context
     * @param time                provides wall clock time
     * @param random              a source of randomness, does not need to be cryptographically secure
     * @param signer              used for signing things with this node's private key
     * @param addressBook         the current address book
     * @param selfId              this node's ID
     * @param softwareVersion     the current software version of the application
     * @param transactionSupplier provides transactions to be included in new events
     */
    public TipsetEventCreator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final Random random,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final TransactionSupplier transactionSupplier) {

        this.time = Objects.requireNonNull(time);
        this.random = Objects.requireNonNull(random);
        this.signer = Objects.requireNonNull(signer);
        this.addressBook = Objects.requireNonNull(addressBook);
        this.selfId = Objects.requireNonNull(selfId);
        this.transactionSupplier = Objects.requireNonNull(transactionSupplier);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        cryptography = platformContext.getCryptography();
        antiBullyingFactor = Math.max(1.0, eventCreationConfig.antiBullyingFactor());
        tipsetMetrics = new TipsetMetrics(platformContext);
        tipsetBuilder = new TipsetBuilder(addressBook);
        tipsetScoreCalculator = new TipsetScoreCalculator(addressBook, selfId, tipsetBuilder);
        childlessOtherEventTracker = new ChildlessEventTracker();
    }

    /**
     * Register a new event from event intake.
     *
     * @param event the event to add
     */
    public void registerEvent(@NonNull final EventImpl event) {

        final NodeId eventCreator = event.getHashedData().getCreatorId();
        final boolean selfEvent = eventCreator.equals(selfId);

        // TODO unit test restart workflow vs non-restart workflow

        if (selfEvent) {
            if (lastSelfEvent == null || lastSelfEvent.getGeneration() < event.getGeneration()) {
                // Normally we will ingest self events before we get to this point, but it's possible
                // to learn of self events for the first time here if we are loading from a restart or reconnect.
                lastSelfEvent = buildDescriptor(event);
                lastSelfEventCreationTime = event.getHashedData().getTimeCreated();
                lastSelfEventTransactionCount = event.getTransactions() == null ? 0 : event.getTransactions().length;
            } else {
                // We already ingested this self event (when it was created),
                return;
            }
        }

        final EventDescriptor descriptor = buildDescriptor(event);
        final List<EventDescriptor> parentDescriptors = getParentDescriptors(event);

        tipsetBuilder.addEvent(descriptor, parentDescriptors);

        if (!selfEvent) {
            childlessOtherEventTracker.addEvent(descriptor, parentDescriptors);
        }
    }

    /**
     * Update the minimum generation non-ancient.
     *
     * @param minimumGenerationNonAncient the new minimum generation non-ancient
     */
    public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
        tipsetBuilder.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
        childlessOtherEventTracker.pruneOldEvents(minimumGenerationNonAncient);
    }

    /**
     * Create a new event if it is legal to do so.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    public GossipEvent createNewEvent() {
        final long bullyScore = tipsetScoreCalculator.getBullyScore();
        tipsetMetrics.getBullyScoreMetric().update(bullyScore);

        // Never bother with anti-bullying techniques if we have a bully score of 1. We are pretty much guaranteed
        // to bully ~1/3 of other nodes by a score of 1.
        final double beNiceToNerdChance = (bullyScore - 1) / antiBullyingFactor;

        if (beNiceToNerdChance > 0 && random.nextDouble() < beNiceToNerdChance) {
            return createEventToReduceBullyScore();
        } else {
            return createEventByOptimizingTipsetScore();
        }
    }

    /**
     * Create an event using the other parent with the best tipset score.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    private GossipEvent createEventByOptimizingTipsetScore() {
        final List<EventDescriptor> possibleOtherParents = childlessOtherEventTracker.getChildlessEvents();
        Collections.shuffle(possibleOtherParents, random);

        EventDescriptor bestOtherParent = null;
        long bestScore = 0;
        for (final EventDescriptor otherParent : possibleOtherParents) {
            final long parentScore = tipsetScoreCalculator.getTheoreticalAdvancementScore(List.of(otherParent));
            if (parentScore > bestScore) {
                bestOtherParent = otherParent;
                bestScore = parentScore;
            }
        }

        if (lastSelfEvent != null && bestOtherParent == null) {
            // There exist no parents that can advance consensus, and this is not our first event.
            return null;
        }

        return buildEvent(bestOtherParent);
    }

    /**
     * Create an event that reduces the bully score.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    private GossipEvent createEventToReduceBullyScore() {
        final List<EventDescriptor> possibleOtherParents = childlessOtherEventTracker.getChildlessEvents();
        final List<EventDescriptor> nerds = new ArrayList<>(possibleOtherParents.size());

        // Choose a random nerd, weighted by how much it is currently being bullied.

        // First, sum up all bully scores.
        int bullyScoreSum = 0;
        final List<Integer> bullyScores = new ArrayList<>(possibleOtherParents.size());
        for (final EventDescriptor nerd : possibleOtherParents) {
            final int nodeIndex = addressBook.getIndexOfNodeId(nerd.getCreator());
            final int bullyScore = tipsetScoreCalculator.getBullyScoreForNodeIndex(nodeIndex);

            final long tipsetScore = tipsetScoreCalculator.getTheoreticalAdvancementScore(List.of(nerd));

            if (bullyScore > 1 && tipsetScore > 0) {
                nerds.add(nerd);
                bullyScores.add(bullyScore);
                bullyScoreSum += bullyScore;
            }
        }

        if (nerds.isEmpty()) {
            // No eligible nerds, choose the event with the best tipset score.
            return createEventByOptimizingTipsetScore();
        }

        // Choose a random nerd.
        final int choice = random.nextInt(bullyScoreSum);
        int runningSum = 0;
        for (int i = 0; i < nerds.size(); i++) {
            runningSum += bullyScores.get(i);
            if (choice < runningSum) {
                return buildEvent(nerds.get(i));
            }
        }

        // This should be impossible.
        throw new IllegalStateException("Failed to find an other parent");
    }

    /**
     * Given an other parent, build the next self event.
     *
     * @param otherParent the other parent, or null if there is no other parent
     * @return the new event
     */
    private GossipEvent buildEvent(@Nullable final EventDescriptor otherParent) {
        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);
        if (lastSelfEvent != null) {
            parentDescriptors.add(lastSelfEvent);
        }
        if (otherParent != null) {
            parentDescriptors.add(otherParent);
        }

        final GossipEvent event = buildEventFromParents(otherParent);

        final EventDescriptor descriptor = buildDescriptor(event);
        tipsetBuilder.addEvent(descriptor, parentDescriptors);
        final long score = tipsetScoreCalculator.addEventAndGetAdvancementScore(descriptor);
        final double scoreRatio = score / (double) tipsetScoreCalculator.getMaximumPossibleScore();
        tipsetMetrics.getTipsetScoreMetric().update(scoreRatio);

        lastSelfEvent = descriptor;
        lastSelfEventCreationTime = event.getHashedData().getTimeCreated();
        lastSelfEventTransactionCount = event.getHashedData().getTransactions().length;

        return event;
    }

    /**
     * Get the generation of a descriptor, handle null appropriately.
     */
    private static long getGeneration(@Nullable final EventDescriptor descriptor) {
        if (descriptor == null) {
            return GENERATION_UNDEFINED;
        } else {
            return descriptor.getGeneration();
        }
    }

    /**
     * Get the hash of a descriptor, handle null appropriately.
     */
    @Nullable
    private static Hash getHash(@Nullable final EventDescriptor descriptor) {
        if (descriptor == null) {
            return null;
        } else {
            return descriptor.getHash();
        }
    }

    /**
     * Get the creator of a descriptor, handle null appropriately.
     */
    @Nullable
    private static NodeId getCreator(@Nullable final EventDescriptor descriptor) {
        if (descriptor == null) {
            return CREATOR_ID_UNDEFINED;
        } else {
            return descriptor.getCreator();
        }
    }

    /**
     * Given the parents, build the event object.
     *
     * @param otherParent the other parent
     * @return the event
     */
    @NonNull
    private GossipEvent buildEventFromParents(@Nullable final EventDescriptor otherParent) {

        final long selfParentGeneration = getGeneration(lastSelfEvent);
        final Hash selfParentHash = getHash(lastSelfEvent);

        final long otherParentGeneration = getGeneration(otherParent);
        final Hash otherParentHash = getHash(otherParent);
        final NodeId otherParentId = getCreator(otherParent);

        // TODO specifically unit test creation time
        final Instant now = time.now();
        final Instant timeCreated;
        if (lastSelfEvent == null) {
            timeCreated = now;
        } else {
            timeCreated = EventUtils.calculateNewEventCreationTime(
                    now, lastSelfEventCreationTime, lastSelfEventTransactionCount);
        }

        final BaseEventHashedData hashedData = new BaseEventHashedData(
                softwareVersion,
                selfId,
                selfParentGeneration,
                otherParentGeneration,
                selfParentHash,
                otherParentHash,
                timeCreated,
                transactionSupplier.getTransactions());
        cryptography.digestSync(hashedData);

        final BaseEventUnhashedData unhashedData = new BaseEventUnhashedData(
                otherParentId, signer.sign(hashedData.getHash().getValue()).getSignatureBytes());

        final GossipEvent event = new GossipEvent(hashedData, unhashedData);
        cryptography.digestSync(event);
        event.buildDescriptor();
        return event;
    }
}
