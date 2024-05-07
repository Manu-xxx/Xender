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

package com.swirlds.platform.test.fixtures.turtle;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.wiring.components.Gossip;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Connects {@link SimulatedGossip} peers in a simulated network.
 * <p>
 * This gossip simulation is intentionally simplistic. It does not attempt to mimic any real gossip algorithm in any
 * meaningful way and makes no attempt to reduce the rate of duplicate events.
 */
public class SimulatedNetwork {

    /**
     * The random number generator to use for simulating network delays.
     */
    private final Random random;

    /**
     * Events that have been submitted within the most recent tick. It is safe for multiple nodes to add to their list
     * of submitted events in parallel.
     */
    private final Map<NodeId, List<GossipEvent>> newlySubmittedEvents = new HashMap<>();

    /**
     * Events that are currently in transit between nodes in the network.
     */
    private final Map<NodeId, PriorityQueue<EventInTransit>> eventsInTransit = new HashMap<>();

    /**
     * The gossip "component" for each node in the network.
     */
    private final Map<NodeId, SimulatedGossip> gossipInstances = new HashMap<>();

    /**
     * The average delay for events to travel between nodes, in nanoseconds.
     */
    private final long averageDelayNanos;

    /**
     * The standard deviation of the delay for events to travel between nodes, in nanoseconds.
     */
    private final long standardDeviationDelayNanos;

    /**
     * Constructor.
     *
     * @param random                 the random number generator to use for simulating network delays
     * @param addressBook            the address book of the network
     * @param averageDelay           the average delay for events to travel between nodes
     * @param standardDeviationDelay the standard deviation of the delay for events to travel between nodes
     */
    public SimulatedNetwork(
            @NonNull final Random random,
            @NonNull final AddressBook addressBook,
            @NonNull final Duration averageDelay,
            @NonNull final Duration standardDeviationDelay) {

        this.random = Objects.requireNonNull(random);

        for (final NodeId nodeId : addressBook.getNodeIdSet()) {
            newlySubmittedEvents.put(nodeId, new ArrayList<>());
            eventsInTransit.put(nodeId, new PriorityQueue<>());
            gossipInstances.put(nodeId, new SimulatedGossip(this, nodeId));
        }

        this.averageDelayNanos = averageDelay.toNanos();
        this.standardDeviationDelayNanos = standardDeviationDelay.toNanos();
    }

    /**
     * Get the gossip instance for a given node.
     *
     * @param nodeId the id of the node
     * @return the gossip instance for the node
     */
    public Gossip getGossipInstance(@NonNull final NodeId nodeId) {
        return gossipInstances.get(nodeId);
    }

    /**
     * Submit an event to be gossiped around the network. Safe to be called by multiple nodes in parallel.
     *
     * @param submitterId the id of the node submitting the event
     * @param event       the event to gossip
     */
    public void submitEvent(@NonNull final NodeId submitterId, @NonNull final GossipEvent event) {
        newlySubmittedEvents.get(submitterId).add(event);
    }

    /**
     * Move time forward to the given instant.
     *
     * @param now the new time
     */
    public void tick(@NonNull final Instant now) {
        deliverEvents(now);
        transmitEvents(now);
    }

    /**
     * For each node, deliver all events that are eligible for immediate delivery.
     */
    private void deliverEvents(@NonNull final Instant now) {
        for (final Map.Entry<NodeId, PriorityQueue<EventInTransit>> entry : eventsInTransit.entrySet()) {
            final NodeId nodeId = entry.getKey();
            final PriorityQueue<EventInTransit> events = entry.getValue();

            final Iterator<EventInTransit> iterator = events.iterator();
            while (iterator.hasNext()) {
                final EventInTransit event = iterator.next();
                if (event.arrivalTime().isAfter(now)) {
                    // no more events to deliver
                    break;
                }

                iterator.remove();
                gossipInstances.get(nodeId).receiveEvent(event.event());
            }
        }
    }

    /**
     * For each node, take the events that were submitted within the last tick and "transmit them over the network".
     *
     * @param now the current time
     */
    private void transmitEvents(@NonNull final Instant now) {
        for (final Map.Entry<NodeId, List<GossipEvent>> senderEntry : newlySubmittedEvents.entrySet()) {
            final NodeId sender = senderEntry.getKey();
            final List<GossipEvent> events = senderEntry.getValue();

            for (final GossipEvent event : events) {
                for (final Map.Entry<NodeId, PriorityQueue<EventInTransit>> receiverEntry :
                        eventsInTransit.entrySet()) {
                    final NodeId receiver = receiverEntry.getKey();
                    final PriorityQueue<EventInTransit> receiverEvents = receiverEntry.getValue();

                    if (sender.equals(receiver)) {
                        // Don't gossip to ourselves
                        continue;
                    }

                    final Instant deliveryTime = now.plusNanos(
                            (long) (averageDelayNanos + random.nextGaussian() * standardDeviationDelayNanos));

                    final GossipEvent eventToDeliver = deepCopyEvent(event);
                    eventToDeliver.setSenderId(sender);
                    eventToDeliver.setTimeReceived(deliveryTime);
                    final EventInTransit eventInTransit = new EventInTransit(eventToDeliver, sender, deliveryTime);
                    receiverEvents.add(eventInTransit);
                }
            }
            events.clear();
        }
    }

    /**
     * Create a deep copy of an event. Until events become entirely immutable, this is necessary to prevent nodes from
     * modifying each other's events.
     *
     * @param event the event to copy
     * @return a deep copy of the event
     */
    @NonNull
    private GossipEvent deepCopyEvent(@NonNull final GossipEvent event) {
        try {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SerializableDataOutputStream outputStream = new SerializableDataOutputStream(byteArrayOutputStream);
            outputStream.writeSerializable(event, false);
            final SerializableDataInputStream inputStream =
                    new SerializableDataInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
            final GossipEvent copy = inputStream.readSerializable(false, GossipEvent::new);
            copy.getHashedData().setHash(event.getHashedData().getHash());
            return copy;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
