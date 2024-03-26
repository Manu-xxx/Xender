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

package com.swirlds.platform.event.creation.tipset;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.platform.event.creation.tipset.TipsetAdvancementWeight.ZERO_ADVANCEMENT_WEIGHT;
import static com.swirlds.platform.event.creation.tipset.TipsetUtils.getParentDescriptors;
import static com.swirlds.platform.system.events.EventConstants.CREATOR_ID_UNDEFINED;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.Signer;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.platform.components.transaction.TransactionSupplier;
import com.swirlds.platform.consensus.ConsensusConstants;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import com.swirlds.platform.event.AncientMode;
import com.swirlds.platform.event.EventUtils;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.creation.EventCreationConfig;
import com.swirlds.platform.event.creation.EventCreator;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.BaseEventUnhashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for creating new events using the tipset algorithm.
 */
public class TipsetEventCreator implements EventCreator {

    private static final Logger logger = LogManager.getLogger(TipsetEventCreator.class);

    private final Cryptography cryptography;
    private final Time time;
    private final Random random;
    private final Signer signer;
    private final NodeId selfId;
    private final TipsetTracker tipsetTracker;
    private final TipsetWeightCalculator tipsetWeightCalculator;
    private final ChildlessEventTracker childlessOtherEventTracker;
    private final TransactionSupplier transactionSupplier;
    private final SoftwareVersion softwareVersion;
    private NonAncientEventWindow nonAncientEventWindow;

    /**
     * The address book for the current network.
     */
    private final AddressBook addressBook;

    /**
     * The size of the current address book.
     */
    private final int networkSize;

    /**
     * The selfishness score is divided by this number to get the probability of creating an event that reduces the
     * selfishness score. The higher this number is, the lower the probability is that an event will be created that
     * reduces the selfishness score.
     */
    private final double antiSelfishnessFactor;

    /**
     * The metrics for the tipset algorithm.
     */
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
     * Defines the current ancient mode.
     */
    private final AncientMode ancientMode;

    private final RateLimitedLogger zeroAdvancementWeightLogger;
    private final RateLimitedLogger noParentFoundLogger;

    /**
     * Create a new tipset event creator.
     *
     * @param platformContext     the platform context
     * @param random              a source of randomness, does not need to be cryptographically secure
     * @param signer              used for signing things with this node's private key
     * @param addressBook         the current address book
     * @param selfId              this node's ID
     * @param softwareVersion     the current software version of the application
     * @param transactionSupplier provides transactions to be included in new events
     */
    public TipsetEventCreator(
            @NonNull final PlatformContext platformContext,
            @NonNull final Random random,
            @NonNull final Signer signer,
            @NonNull final AddressBook addressBook,
            @NonNull final NodeId selfId,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final TransactionSupplier transactionSupplier) {

        this.time = platformContext.getTime();
        this.random = Objects.requireNonNull(random);
        this.signer = Objects.requireNonNull(signer);
        this.selfId = Objects.requireNonNull(selfId);
        this.transactionSupplier = Objects.requireNonNull(transactionSupplier);
        this.softwareVersion = Objects.requireNonNull(softwareVersion);
        this.addressBook = Objects.requireNonNull(addressBook);

        final EventCreationConfig eventCreationConfig =
                platformContext.getConfiguration().getConfigData(EventCreationConfig.class);

        cryptography = platformContext.getCryptography();
        antiSelfishnessFactor = Math.max(1.0, eventCreationConfig.antiSelfishnessFactor());
        tipsetMetrics = new TipsetMetrics(platformContext, addressBook);
        ancientMode = platformContext
                .getConfiguration()
                .getConfigData(EventConfig.class)
                .getAncientMode();
        tipsetTracker = new TipsetTracker(time, addressBook, ancientMode);
        childlessOtherEventTracker = new ChildlessEventTracker();
        tipsetWeightCalculator = new TipsetWeightCalculator(
                platformContext, addressBook, selfId, tipsetTracker, childlessOtherEventTracker);
        networkSize = addressBook.getSize();

        zeroAdvancementWeightLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));
        noParentFoundLogger = new RateLimitedLogger(logger, time, Duration.ofMinutes(1));

        this.nonAncientEventWindow = NonAncientEventWindow.getGenesisNonAncientEventWindow(ancientMode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerEvent(@NonNull final GossipEvent event) {
        if (nonAncientEventWindow.isAncient(event)) {
            return;
        }

        final NodeId eventCreator = event.getHashedData().getCreatorId();
        if (!addressBook.contains(eventCreator)) {
            return;
        }
        final boolean selfEvent = eventCreator.equals(selfId);

        if (selfEvent) {
            if (lastSelfEvent == null || lastSelfEvent.getGeneration() < event.getGeneration()) {
                // Normally we will ingest self events before we get to this point, but it's possible
                // to learn of self events for the first time here if we are loading from a restart or reconnect.
                lastSelfEvent = event.getDescriptor();
                lastSelfEventCreationTime = event.getHashedData().getTimeCreated();
                lastSelfEventTransactionCount = event.getHashedData().getTransactions() == null
                        ? 0
                        : event.getHashedData().getTransactions().length;
                childlessOtherEventTracker.registerSelfEventParents(
                        event.getHashedData().getOtherParents());
            } else {
                // We already ingested this self event (when it was created),
                return;
            }
        }

        final EventDescriptor descriptor = event.getDescriptor();
        final List<EventDescriptor> parentDescriptors = getParentDescriptors(event);

        tipsetTracker.addEvent(descriptor, parentDescriptors);

        if (!selfEvent) {
            childlessOtherEventTracker.addEvent(descriptor, parentDescriptors);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNonAncientEventWindow(@NonNull final NonAncientEventWindow nonAncientEventWindow) {
        this.nonAncientEventWindow = Objects.requireNonNull(nonAncientEventWindow);
        tipsetTracker.setNonAncientEventWindow(nonAncientEventWindow);
        childlessOtherEventTracker.pruneOldEvents(nonAncientEventWindow);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Nullable
    public GossipEvent maybeCreateEvent() {
        if (networkSize == 1) {
            // Special case: network of size 1.
            // We can always create a new event, no need to run the tipset algorithm.
            return createEventForSizeOneNetwork();
        }

        final long selfishness = tipsetWeightCalculator.getMaxSelfishnessScore();
        tipsetMetrics.getSelfishnessMetric().update(selfishness);

        // Never bother with anti-selfishness techniques if we have a selfishness score of 1.
        // We are pretty much guaranteed to be selfish to ~1/3 of other nodes by a score of 1.
        final double beNiceChance = (selfishness - 1) / antiSelfishnessFactor;

        if (beNiceChance > 0 && random.nextDouble() < beNiceChance) {
            return createEventToReduceSelfishness();
        } else {
            return createEventByOptimizingAdvancementWeight();
        }
    }

    /**
     * Create the next event for a network of size 1 (i.e. where we are the only member). We don't use the tipset
     * algorithm like normal, since we will never have a real other parent.
     *
     * @return the new event
     */
    private GossipEvent createEventForSizeOneNetwork() {
        // There is a quirk in size 1 networks where we can only
        // reach consensus if the self parent is also the other parent.
        // Unexpected, but harmless. So just use the same event
        // as both parents until that issue is resolved.
        return buildAndProcessEvent(lastSelfEvent);
    }

    /**
     * Create an event using the other parent with the best tipset advancement weight.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    private GossipEvent createEventByOptimizingAdvancementWeight() {
        final List<EventDescriptor> possibleOtherParents = childlessOtherEventTracker.getChildlessEvents();
        Collections.shuffle(possibleOtherParents, random);

        EventDescriptor bestOtherParent = null;
        TipsetAdvancementWeight bestAdvancementWeight = ZERO_ADVANCEMENT_WEIGHT;
        for (final EventDescriptor otherParent : possibleOtherParents) {

            final List<EventDescriptor> parents = new ArrayList<>(2);
            parents.add(otherParent);
            if (lastSelfEvent != null) {
                parents.add(lastSelfEvent);
            }

            final TipsetAdvancementWeight advancementWeight =
                    tipsetWeightCalculator.getTheoreticalAdvancementWeight(parents);
            if (advancementWeight.isGreaterThan(bestAdvancementWeight)) {
                bestOtherParent = otherParent;
                bestAdvancementWeight = advancementWeight;
            }
        }

        if (bestOtherParent == null) {
            // If there are no available other parents, it is only legal to create a new event if we are
            // creating a genesis event. In order to create a genesis event, we must have never created
            // an event before and the current non-ancient event window must have never been advanced.
            if (!nonAncientEventWindow.isGenesis() || lastSelfEvent != null) {
                // event creation isn't legal
                return null;
            }

            // we are creating a genesis event, so we can use a null other parent
            return buildAndProcessEvent(null);
        }

        tipsetMetrics.getTipsetParentMetric(bestOtherParent.getCreator()).cycle();
        return buildAndProcessEvent(bestOtherParent);
    }

    /**
     * Create an event that reduces the selfishness score.
     *
     * @return the new event, or null if it is not legal to create a new event
     */
    @Nullable
    private GossipEvent createEventToReduceSelfishness() {
        final List<EventDescriptor> possibleOtherParents = childlessOtherEventTracker.getChildlessEvents();
        final List<EventDescriptor> ignoredNodes = new ArrayList<>(possibleOtherParents.size());

        // Choose a random ignored node, weighted by how much it is currently being ignored.

        // First, figure out who is an ignored node and sum up all selfishness scores.
        int selfishnessSum = 0;
        final List<Integer> selfishnessScores = new ArrayList<>(possibleOtherParents.size());
        for (final EventDescriptor possibleIgnoredNode : possibleOtherParents) {
            final int selfishness = tipsetWeightCalculator.getSelfishnessScoreForNode(possibleIgnoredNode.getCreator());

            final List<EventDescriptor> theoreticalParents = new ArrayList<>(2);
            theoreticalParents.add(possibleIgnoredNode);
            if (lastSelfEvent == null) {
                throw new IllegalStateException("lastSelfEvent is null");
            }
            theoreticalParents.add(lastSelfEvent);

            final TipsetAdvancementWeight advancementWeight =
                    tipsetWeightCalculator.getTheoreticalAdvancementWeight(theoreticalParents);

            if (selfishness > 1) {
                if (advancementWeight.isNonZero()) {
                    ignoredNodes.add(possibleIgnoredNode);
                    selfishnessScores.add(selfishness);
                    selfishnessSum += selfishness;
                } else {
                    // Note: if selfishness score is greater than 1, it is mathematically not possible
                    // for the advancement score to be zero. But in the interest in extreme caution,
                    // we check anyway, since it is very important never to create events with
                    // an advancement score of zero.
                    zeroAdvancementWeightLogger.error(
                            EXCEPTION.getMarker(),
                            "selfishness score is {} but advancement score is zero for {}.\n{}",
                            selfishness,
                            possibleIgnoredNode,
                            this);
                }
            }
        }

        if (ignoredNodes.isEmpty()) {
            // Note: this should be impossible, since we will not enter this method in the first
            // place if there are no ignored nodes. But better to be safe than sorry, and returning null
            // is an acceptable way of saying "I can't create an event right now".
            noParentFoundLogger.error(
                    EXCEPTION.getMarker(), "failed to locate eligible ignored node to use as a parent");
            return null;
        }

        // Choose a random ignored node.
        final int choice = random.nextInt(selfishnessSum);
        int runningSum = 0;
        for (int i = 0; i < ignoredNodes.size(); i++) {
            runningSum += selfishnessScores.get(i);
            if (choice < runningSum) {
                final EventDescriptor ignoredNode = ignoredNodes.get(i);
                tipsetMetrics.getPityParentMetric(ignoredNode.getCreator()).cycle();
                return buildAndProcessEvent(ignoredNode);
            }
        }

        // This should be impossible.
        throw new IllegalStateException("Failed to find an other parent");
    }

    /**
     * Given an other parent, build the next self event and process it.
     *
     * @param otherParent the other parent, or null if there is no other parent
     * @return the new event
     */
    private GossipEvent buildAndProcessEvent(@Nullable final EventDescriptor otherParent) {
        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);
        if (lastSelfEvent != null) {
            parentDescriptors.add(lastSelfEvent);
        }
        if (otherParent != null) {
            parentDescriptors.add(otherParent);
        }

        final GossipEvent event = assembleEventObject(otherParent);

        final EventDescriptor descriptor = event.getDescriptor();
        tipsetTracker.addEvent(descriptor, parentDescriptors);
        final TipsetAdvancementWeight advancementWeight =
                tipsetWeightCalculator.addEventAndGetAdvancementWeight(descriptor);
        final double weightRatio = advancementWeight.advancementWeight()
                / (double) tipsetWeightCalculator.getMaximumPossibleAdvancementWeight();
        tipsetMetrics.getTipsetAdvancementMetric().update(weightRatio);

        if (otherParent != null) {
            childlessOtherEventTracker.registerSelfEventParents(List.of(otherParent));
        }

        lastSelfEvent = descriptor;
        lastSelfEventCreationTime = event.getHashedData().getTimeCreated();
        lastSelfEventTransactionCount = event.getHashedData().getTransactions().length;

        return event;
    }

    /**
     * Given the parents, assemble the event object.
     *
     * @param otherParent the other parent
     * @return the event
     */
    @NonNull
    private GossipEvent assembleEventObject(@Nullable final EventDescriptor otherParent) {
        final NodeId otherParentId = getCreator(otherParent);

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
                lastSelfEvent,
                otherParent == null ? Collections.emptyList() : Collections.singletonList(otherParent),
                nonAncientEventWindow.getAncientMode() == AncientMode.BIRTH_ROUND_THRESHOLD
                        ? nonAncientEventWindow.getPendingConsensusRound()
                        : ConsensusConstants.ROUND_FIRST,
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
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        tipsetTracker.clear();
        childlessOtherEventTracker.clear();
        tipsetWeightCalculator.clear();
        nonAncientEventWindow = NonAncientEventWindow.getGenesisNonAncientEventWindow(ancientMode);
        lastSelfEvent = null;
        lastSelfEventCreationTime = null;
        lastSelfEventTransactionCount = 0;
    }

    @NonNull
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Minimum generation non-ancient: ")
                .append(tipsetTracker.getNonAncientEventWindow())
                .append("\n");
        sb.append("Latest self event: ").append(lastSelfEvent).append("\n");
        sb.append(tipsetWeightCalculator);

        sb.append("Childless events:");
        final List<EventDescriptor> childlessEvents = childlessOtherEventTracker.getChildlessEvents();
        if (childlessEvents.isEmpty()) {
            sb.append(" none\n");
        } else {
            sb.append("\n");
            for (final EventDescriptor event : childlessEvents) {
                final Tipset tipset = tipsetTracker.getTipset(event);
                sb.append("  - ").append(event).append(" ").append(tipset).append("\n");
            }
        }

        return sb.toString();
    }
}
