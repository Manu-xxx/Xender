package com.swirlds.common.wiring;

import static java.util.concurrent.ForkJoinPool.defaultForkJoinWorkerThreadFactory;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.wiring.components.EventPool;
import com.swirlds.common.wiring.components.EventVerifier;
import com.swirlds.common.wiring.components.Gossip;
import com.swirlds.common.wiring.components.TopologicalEventSorter;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;

class WiringBenchmark {

    @Test
    void basicBenchmark() throws InterruptedException {

        // We will use this executor for starting all threads. Maybe we should only use it for temporary threads?
        final ForkJoinPool executor = new ForkJoinPool(
                Runtime.getRuntime().availableProcessors(),
                defaultForkJoinWorkerThreadFactory,
                (t, e) -> {
                    System.out.println("Uncaught exception in thread " + t.getName());
                    e.printStackTrace();
                },
                true
        );

        final EventPool eventPool = new EventPool();

        final TopologicalEventSorter orphanBuffer = new TopologicalEventSorter(eventPool);
        final EventVerifier verifier = new EventVerifier(
                Wire.builder(executor, orphanBuffer)
                        .withConcurrency(false)
                        .build());
        final Gossip gossip = new Gossip(eventPool,
                Wire.builder(executor, verifier)
                        .withConcurrency(true)
                        .build());

        // Create a user thread for running "gossip". It will continue to generate events until explicitly stopped.
        System.out.println("Starting gossip");
        gossip.start();
        SECONDS.sleep(120);
        gossip.stop();

        // Validate that all events have been seen by orphanBuffer
        long timeout = System.currentTimeMillis() + 1000;
        boolean success = false;
        while (System.currentTimeMillis() < timeout) {
            if (orphanBuffer.getCheckSum() == gossip.getCheckSum()) {
                success = true;
                break;
            }
        }
        assertTrue(success);
    }
}
