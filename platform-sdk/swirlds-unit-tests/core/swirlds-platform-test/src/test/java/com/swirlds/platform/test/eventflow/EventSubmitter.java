/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.eventflow;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;

import com.swirlds.common.system.transaction.Transaction;
import com.swirlds.common.threading.framework.Stoppable;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.test.event.emitter.EventEmitter;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Generates and submits events to an event consumer on a worker thread.
 */
public class EventSubmitter {

    /** Used to emit events from a graph. */
    private final EventEmitter<?> eventEmitter;

    /** Determines if a given event should be submitted to the event consumer. */
    private final Predicate<Long> shouldSubmitEvent;

    /** A consumer of events generated by this class. */
    private final Consumer<EventImpl> eventConsumer;

    /** The amount of time to wait between submitting batches of events. */
    private final Duration timeBetweenSubmissions;

    /** The number of system transactions in events submitted */
    private final AtomicInteger numSysTransactions = new AtomicInteger(0);

    /** The number of events to send per batch. */
    private final int eventsPerBatch;

    private final StoppableThread worker;

    private final AtomicInteger eventsSent = new AtomicInteger(0);
    private final AtomicBoolean threadDone = new AtomicBoolean(false);
    private int minEvents = -1;

    public EventSubmitter(
            final EventEmitter<?> eventEmitter,
            final Predicate<Long> shouldSubmitEvent,
            final Consumer<EventImpl> eventConsumer,
            final int eventsPerBatch,
            final Duration timeBetweenSubmissions) {

        this.eventEmitter = eventEmitter;
        this.shouldSubmitEvent = shouldSubmitEvent;
        this.eventConsumer = eventConsumer;
        this.eventsPerBatch = eventsPerBatch;
        this.timeBetweenSubmissions = timeBetweenSubmissions;
        worker = getStaticThreadManager().newStoppableThreadConfiguration()
                .setThreadName("event-submitter")
                .setWork(this::submitEvents)
                .setStopBehavior(Stoppable.StopBehavior.INTERRUPTABLE)
                .build();
    }

    public void start() {
        worker.start();
    }

    /**
     * Starts the worker thread that submits events to the consumer and stops it once {@code minEvents} events have been
     * submitted.
     *
     * @param minEvents
     * 		the minimum number of events to submit to the consumer
     * @throws InterruptedException
     */
    public void submitEventsAndStop(final int minEvents) throws InterruptedException {
        this.minEvents = minEvents;
        start();
        synchronized (threadDone) {
            threadDone.wait();
        }
        stop();
    }

    public void stop() {
        worker.stop();
    }

    /**
     * Sleep for a period of time, then submit some events. If there is a minimum number of events set, the thread will
     * stop as soon as the minimum is reached.
     */
    private void submitEvents() {
        try {
            Thread.sleep(timeBetweenSubmissions.toMillis());
        } catch (final InterruptedException e) {
            // ignored
        }
        IntStream.range(0, eventsPerBatch).forEach(i -> eventConsumer.accept(generateEvent()));
        eventsSent.addAndGet(eventsPerBatch);

        if (minEvents >= 0 && eventsSent.get() >= minEvents) {
            synchronized (threadDone) {
                threadDone.notifyAll();
            }
        }
    }

    public int getNumSysTransactions() {
        return numSysTransactions.get();
    }

    /**
     * Generates an event that passes the {@code shouldSubmitEvent} predicate.
     *
     * @return the event
     */
    private EventImpl generateEvent() {
        EventImpl event = null;
        while (event == null || !shouldSubmitEvent.test(event.getCreatorId())) {
            event = eventEmitter.emitEvent();
        }
        for (final Transaction tx : event.getTransactions()) {
            if (tx.isSystem()) {
                numSysTransactions.incrementAndGet();
            }
        }
        return event;
    }
}
