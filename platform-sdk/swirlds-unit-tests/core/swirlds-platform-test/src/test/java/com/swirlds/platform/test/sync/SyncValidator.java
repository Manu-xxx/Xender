/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.sync;

import static com.swirlds.platform.test.sync.SyncUtils.printEvents;
import static com.swirlds.platform.test.sync.SyncUtils.printTasks;
import static com.swirlds.platform.test.sync.SyncUtils.printTipSet;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.test.event.IndexedEvent;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;

public class SyncValidator {

    private static boolean enableLogging;

    public static void assertNoEventsReceived(final SyncNode node) {
        assertNoEventsReceived(node.getNodeId().toString(), node);
    }

    public static void assertNoEventsReceived(final String nodeName, final SyncNode node) {
        if (enableLogging) {
            System.out.println(String.format("*** Asserting that node '%s' received no events ***", nodeName));
        }

        assertTrue(
                node.getReceivedEvents().isEmpty(),
                format(
                        "%s received %d events but should have received none.",
                        nodeName, node.getReceivedEvents().size()));
    }

    public static void assertNoEventsTransferred(final SyncNode caller, final SyncNode listener) {
        if (enableLogging) {
            System.out.println("*** Asserting that neither node received any events ***");
        }
        assertNoEventsReceived("caller", caller);
        assertNoEventsReceived("listener", listener);
    }

    public static void assertRequiredEventsTransferred(final SyncNode caller, final SyncNode listener) {
        if (enableLogging) {
            printTipSet("Caller's Tip Set", caller);
            printTipSet("Listener's Tip Set", listener);
            System.out.println("*** Asserting that required events were transferred ***");
        }
        compareEventLists(caller, listener, false);
    }

    public static void assertOnlyRequiredEventsTransferred(final SyncNode caller, final SyncNode listener) {
        if (enableLogging) {
            System.out.println("*** Asserting that only required events were transferred ***");
        }
        compareEventLists(caller, listener, true);
    }

    public static void assertFallenBehindDetection(final boolean fellBehind, final SyncNode... nodes) {
        if (enableLogging) {
            System.out.println("*** Asserting that a fallen behind node was detected ***");
        }
        for (final SyncNode node : nodes) {
            assertEquals(
                    fellBehind,
                    node.getSyncManager().hasFallenBehind(),
                    String.format(
                            "node %s %s have been notified in the sync that it fell behind",
                            node.getNodeId().toString(), fellBehind ? "should" : "should NOT"));
        }
    }

    public static void assertStreamsEmpty(final SyncNode caller, final SyncNode listener) {
        if (enableLogging) {
            System.out.println("*** Asserting streams are empty ***");
        }
        for (final Connection connection : List.of(caller.getConnection(), listener.getConnection())) {
            try {
                assertEquals(0, connection.getDis().available(), "the streams should be empty after a successful sync");
            } catch (final IOException e) {
                fail("Unable to verify the caller or listener stream was empty", e);
            }
        }
    }

    public static void assertExceptionThrown(final SyncNode caller, final SyncNode listener) {
        if (enableLogging) {
            System.out.println("*** Asserting caller and listener threw an exception ***");

            System.out.println("\nCaller Exception: " + caller.getSyncException());
            System.out.println("\nListener Exception: " + listener.getSyncException());
        }
        assertNotNull(caller.getSyncException(), "Expected the caller to have thrown an exception.");
        assertNotNull(listener.getSyncException(), "Expected the listener to have thrown an exception.");
    }

    private static void compareEventLists(final SyncNode caller, final SyncNode listener, final boolean strictCompare) {
        // Determine the unique events for the caller and listener, since they could have added some of the
        // same events from step 2.
        final Collection<IndexedEvent> expectedCallerSendList =
                CollectionUtils.subtract(caller.getGeneratedEvents(), listener.getGeneratedEvents());
        final Collection<IndexedEvent> expectedListenerSendList =
                CollectionUtils.subtract(listener.getGeneratedEvents(), caller.getGeneratedEvents());

        // Remove expired events
        expectedCallerSendList.removeIf(e -> e.getGeneration() < caller.getOldestGeneration());
        expectedListenerSendList.removeIf(e -> e.getGeneration() < listener.getOldestGeneration());

        // Remove events that are ancient for the peer
        expectedCallerSendList.removeIf(
                e -> e.getGeneration() < listener.getConsensus().getMinGenerationNonAncient());
        expectedListenerSendList.removeIf(
                e -> e.getGeneration() < caller.getConsensus().getMinGenerationNonAncient());

        // Get the events each received from the other in the sync
        final List<EventIntakeTask> callerReceivedEvents = caller.getReceivedEvents();
        final List<EventIntakeTask> listenerReceivedEvents = listener.getReceivedEvents();

        if (enableLogging) {
            printEvents("Caller's last added events", caller.getGeneratedEvents());
            printEvents("Listener's last added events", listener.getGeneratedEvents());

            printTipSet("Caller's Tip Set", caller);
            printTipSet("Listener's Tip Set", listener);
            printEvents("expectedCallerSendList", expectedCallerSendList);
            printEvents("expectedListenerSendList", expectedListenerSendList);
            printTasks("caller received", callerReceivedEvents);
            printTasks("listener received", listenerReceivedEvents);
        }

        // Assert that the event each received are the unique events in the other's shadow graph
        compareEventLists("listener", expectedCallerSendList, listener, strictCompare);
        compareEventLists("caller", expectedListenerSendList, caller, strictCompare);
    }

    private static void compareEventLists(
            final String node,
            final Collection<IndexedEvent> expectedList,
            final SyncNode receiver,
            final boolean strictCompare) {

        Collection<EventIntakeTask> actualList = receiver.getReceivedEvents();

        if (expectedList == null && actualList == null) {
            return;
        }

        if (expectedList == null || actualList == null) {
            fail(format(
                    "Expected and actual mismatch for %s. Expected = %s, Actual = %s", node, expectedList, actualList));
        }

        LinkedList<IndexedEvent> expectedAndNotFound = new LinkedList<>();

        if (enableLogging) {
            printEvents(format("%s's expected received list", node), expectedList);
            printTasks(format("%s's actual received list", node), actualList);
        }

        // Compare the two lists to see if there is a matching event in the actual list for each expected event
        for (final IndexedEvent expected : expectedList) {
            boolean foundMatch = false;

            for (final EventIntakeTask actual : actualList) {
                if (actual instanceof GossipEvent) {

                    final GossipEvent actualEvent = (GossipEvent) actual;
                    final BaseEventHashedData actualHashedData = actualEvent.getHashedData();
                    final BaseEventUnhashedData actualUnhashedData = actualEvent.getUnhashedData();

                    if (expected.getBaseEventHashedData().equals(actualHashedData)
                            && expected.getBaseEventUnhashedData().equals(actualUnhashedData)) {
                        foundMatch = true;
                        break;
                    }
                }
            }

            if (!foundMatch) {
                expectedAndNotFound.add(expected);
            }
        }

        // Keep track of the number of events we expected to receive but did not
        int numExpectedAndNotFound = expectedAndNotFound.size();
        expectedAndNotFound.removeIf(event -> receiver.getShadowGraph().isHashInGraph(event.getBaseHash()));

        if (!expectedAndNotFound.isEmpty()) {
            List<String> missingHashes = expectedAndNotFound.stream()
                    .map(EventImpl::getBaseHash)
                    .map(h -> CommonUtils.hex(h.getValue(), 4))
                    .collect(Collectors.toList());
            fail(format(
                    "Actual list is missing %s expected event(s) with hash(es) %s",
                    missingHashes.size(), missingHashes));
        }

        // If we made it this far without failing, there were events we expected to receive but did not because they
        // were added to the receiver's graph by another sync. Therefore, the expected number of events is that many
        // less.
        int adjustedExpectedEventNum = expectedList.size() - numExpectedAndNotFound;

        // Ensure that only the events that needed to be sent were sent
        if (strictCompare && adjustedExpectedEventNum != actualList.size()) {
            fail(format("Expected %s events, actual number was %s", expectedList.size(), actualList.size()));
        }
    }

    public static void enableLogging() {
        enableLogging = true;
    }
}
