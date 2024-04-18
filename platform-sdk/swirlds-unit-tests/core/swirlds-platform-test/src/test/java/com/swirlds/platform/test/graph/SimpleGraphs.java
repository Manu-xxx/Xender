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

package com.swirlds.platform.test.graph;

import static com.swirlds.platform.test.fixtures.event.EventImplTestUtils.createEventImpl;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.EventStrings;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.fixtures.event.TestingEventBuilder;
import java.time.Instant;
import java.util.List;

public class SimpleGraphs {
    /**
     * Builds graph below:
     *
     * <pre>
     * 3  4
     * | /|
     * 2  |
     * | \|
     * 0  1
     * </pre>
     */
    public static List<GossipEvent> graph5e2n(final Randotron random) {
        final GossipEvent e0 =
                new TestingEventBuilder(random).setCreatorId(new NodeId(1)).build();
        final GossipEvent e1 =
                new TestingEventBuilder(random).setCreatorId(new NodeId(2)).build();
        final GossipEvent e2 = new TestingEventBuilder(random)
                .setCreatorId(new NodeId(1))
                .setSelfParent(e0)
                .setOtherParent(e1)
                .build();
        final GossipEvent e3 = new TestingEventBuilder(random)
                .setCreatorId(new NodeId(1))
                .setSelfParent(e2)
                .build();
        final GossipEvent e4 = new TestingEventBuilder(random)
                .setCreatorId(new NodeId(2))
                .setSelfParent(e1)
                .setOtherParent(e2)
                .build();
        System.out.println("e0 " + EventStrings.toShortString(e0));
        System.out.println("e1 " + EventStrings.toShortString(e1));
        System.out.println("e2 " + EventStrings.toShortString(e2));
        System.out.println("e3 " + EventStrings.toShortString(e3));
        System.out.println("e4 " + EventStrings.toShortString(e4));
        return List.of(e0, e1, e2, e3, e4);
    }

    /**
     * Builds the graph below:
     *
     * <pre>
     *       8
     *     / |
     * 5  6  7
     * | /| /|
     * 3  | |4
     * | \|/ |
     * 0  1  2
     *
     * Consensus events: 0,1
     *
     * </pre>
     */
    public static List<EventImpl> graph9e3n(final Randotron random) {
        // generation 0
        final EventImpl e0 = createEventImpl(
                new TestingEventBuilder(random)
                        .setCreatorId(new NodeId(1))
                        .setTimeCreated(Instant.parse("2020-05-06T13:21:56.680Z")),
                null,
                null);
        e0.setConsensus(true);

        final EventImpl e1 = createEventImpl(
                new TestingEventBuilder(random)
                        .setCreatorId(new NodeId(2))
                        .setTimeCreated(Instant.parse("2020-05-06T13:21:56.681Z")),
                null,
                null);
        e1.setConsensus(true);

        final EventImpl e2 = createEventImpl(
                new TestingEventBuilder(random)
                        .setCreatorId(new NodeId(3))
                        .setTimeCreated(Instant.parse("2020-05-06T13:21:56.682Z")),
                null,
                null);

        // generation 1
        final EventImpl e3 = createEventImpl(
                new TestingEventBuilder(random)
                        .setCreatorId(new NodeId(1))
                        .setTimeCreated(Instant.parse("2020-05-06T13:21:56.683Z")),
                e0,
                e1);

        final EventImpl e4 = createEventImpl(
                new TestingEventBuilder(random)
                        .setCreatorId(new NodeId(3))
                        .setTimeCreated(Instant.parse("2020-05-06T13:21:56.686Z")),
                e2,
                null);

        // generation 2
        final EventImpl e5 = createEventImpl(
                new TestingEventBuilder(random)
                        .setCreatorId(new NodeId(1))
                        .setTimeCreated(Instant.parse("2020-05-06T13:21:56.685Z")),
                e3,
                null);

        final EventImpl e6 = createEventImpl(
                new TestingEventBuilder(random)
                        .setCreatorId(new NodeId(2))
                        .setTimeCreated(Instant.parse("2020-05-06T13:21:56.686Z")),
                e1,
                e3);

        final EventImpl e7 = createEventImpl(
                new TestingEventBuilder(random)
                        .setCreatorId(new NodeId(3))
                        .setTimeCreated(Instant.parse("2020-05-06T13:21:56.690Z")),
                e4,
                e1);

        // generation 3
        final EventImpl e8 = createEventImpl(
                new TestingEventBuilder(random)
                        .setCreatorId(new NodeId(3))
                        .setTimeCreated(Instant.parse("2020-05-06T13:21:56.694Z")),
                e7,
                e6);

        return List.of(e0, e1, e2, e3, e4, e5, e6, e7, e8);
    }
}
