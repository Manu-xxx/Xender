package com.swirlds.common.wiring.components;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A quick and dirty simulation of gossip :-). It will generate events like crazy.
 */
public class Gossip {
    private final Executor executor;
    private final EventPool eventPool;
    private final Consumer<Event> toEventVerifier;
    private final AtomicLong eventNumber = new AtomicLong();
    private volatile boolean stopped = false;
    private volatile long checkSum;

    public Gossip(Executor executor, EventPool eventPool, Consumer<Event> toEventVerifier) {
        this.executor = executor;
        this.toEventVerifier = toEventVerifier;
        this.eventPool = eventPool;
    }

    public void start() {
        eventNumber.set(0);
        checkSum = 0;
        executor.execute(this::generateEvents);
    }

    private void generateEvents() {
        while (!stopped) {
            final var event = eventPool.checkout(eventNumber.getAndIncrement());
            toEventVerifier.accept(event);
        }
        long lastNumber = eventNumber.get();
        checkSum = lastNumber * (lastNumber + 1) / 2;
    }

    public void stop() {
        stopped = true;
    }

    public long getCheckSum() {
        return checkSum;
    }
}
