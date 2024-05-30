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

package com.hedera.wiring.model;

import static com.hedera.wiring.schedulers.builders.TaskSchedulerBuilder.UNLIMITED_CAPACITY;
import static com.hedera.wiring.schedulers.builders.TaskSchedulerType.CONCURRENT;
import static com.hedera.wiring.schedulers.builders.TaskSchedulerType.DIRECT;
import static com.hedera.wiring.schedulers.builders.TaskSchedulerType.DIRECT_THREADSAFE;
import static com.hedera.wiring.schedulers.builders.TaskSchedulerType.NO_OP;
import static com.hedera.wiring.schedulers.builders.TaskSchedulerType.SEQUENTIAL;
import static com.hedera.wiring.schedulers.builders.TaskSchedulerType.SEQUENTIAL_THREAD;
import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomInstant;
import static com.swirlds.common.utility.NonCryptographicHashing.hash32;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.assertj.core.api.Fail.fail;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.wiring.schedulers.TaskScheduler;
import com.hedera.wiring.wires.input.BindableInputWire;
import com.hedera.wiring.wires.input.InputWire;
import com.hedera.wiring.wires.output.OutputWire;
import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.utility.NonCryptographicHashing;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeterministicModelTests {

    /**
     * Build a handler method for "components" in the wiring generated by generateWiringMesh(). The goal of these
     * handlers is to be extremely race condition prone if executed in parallel or in different serial order.
     *
     * @param random        a random number generator
     * @param discardChance a chance out of 1 that an input is simply discarded
     * @param lock          grab and release this lock each time the handler is called. This is a quick and dirty
     *                      mechanism we can use to check for quiescence in a thread safe manner.
     * @return a new handler
     */
    @NonNull
    private static Function<Long, Long> buildHandler(
            @NonNull final Random random, final double discardChance, final ReentrantLock lock) {

        final AtomicLong innerValue = new AtomicLong();
        return input -> {

            // This is locked by the outside scope when we are attempting
            // to determine if the system is in a quiescent state.
            lock.lock();
            lock.unlock();

            if (discardChance > 0 && (random.nextDouble() < discardChance)) {
                // Discard this input
                return null;
            }

            final long newValue = NonCryptographicHashing.hash64(innerValue.get(), input, random.nextLong());
            innerValue.set(newValue);

            // Sleep half a millisecond, on average.
            final long sleepMicros = Math.abs(newValue) % 1000;
            try {
                MICROSECONDS.sleep(sleepMicros);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            return newValue;
        };
    }

    /**
     * A test wiring mesh for testing deterministic behavior
     *
     * @param inputWire   the input wire for the mesh
     * @param outputValue contains the final result (when isQuiescent() returns true)
     * @param isQuiescent returns true when there is no longer data flowing through the mesh
     */
    private record WiringMesh(
            @NonNull InputWire<Long> inputWire,
            @NonNull AtomicLong outputValue,
            @NonNull BooleanSupplier isQuiescent) {}

    /**
     * Generates a wiring mesh that yields non-deterministic results when data is fed in using the standard wiring
     * model.
     *
     * @param seed            the seed to use for the random number generator
     * @param wiringModel     the wiring model to use
     * @param enableHeartbeat whether to enable a heartbeat scheduler, useful for ensuring that the standard case is
     *                        non-deterministic even without a heartbeat introducing artifacts from the wall clock
     * @return the input wire to feed data into the mesh
     */
    @NonNull
    private static WiringMesh generateWiringMesh(
            final long seed, @NonNull final WiringModel wiringModel, final boolean enableHeartbeat) {

        // - Data is fed into A
        // - Data comes out of J
        // - B is a concurrent scheduler
        // - H and I are direct schedulers
        // - K is a no op scheduler
        // - All other schedulers are sequential or sequential thread
        // - Scheduler D discards 60% of all input. This means that the probability of data cycling an infinite
        //   number of times in the loop asymptotically approaches 0 over time.
        // - All other schedulers discard 1% of their input.

        /*

        A
        |
        v
        B <---------- Heartbeat (5ms)
        |
        v
        C
        |
        v
        D -----> E                 K
        ^        |                 ^
        |        v                 |
        G <----- F -----> H -----> I -----> J

        */

        final Random random = new Random(seed);

        final ReentrantLock lock = new ReentrantLock();

        final TaskScheduler<Long> schedulerA = wiringModel
                .schedulerBuilder("A")
                .withType(SEQUENTIAL)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inA = schedulerA.buildInputWire("inA");
        inA.bind(buildHandler(random, 0.01, lock));
        final OutputWire<Long> outA = schedulerA.getOutputWire();

        final TaskScheduler<Long> schedulerB = wiringModel
                .schedulerBuilder("B")
                .withType(CONCURRENT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inB = schedulerB.buildInputWire("inB");
        inB.bind(buildHandler(random, 0.01, lock));
        final BindableInputWire<Instant, Long> schedulerBHeartbeat = schedulerB.buildInputWire("heartbeatB");
        final Function<Long, Long> heartbeatHandler = buildHandler(random, 0, lock);
        schedulerBHeartbeat.bind(instant -> {
            System.out.println(instant);
            return heartbeatHandler.apply(instant.toEpochMilli());
        });
        final OutputWire<Long> outB = schedulerB.getOutputWire();

        final TaskScheduler<Long> schedulerC = wiringModel
                .schedulerBuilder("C")
                .withType(SEQUENTIAL_THREAD)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inC = schedulerC.buildInputWire("inC");
        inC.bind(buildHandler(random, 0.01, lock));
        final OutputWire<Long> outC = schedulerC.getOutputWire();

        final TaskScheduler<Long> schedulerD = wiringModel
                .schedulerBuilder("D")
                .withType(SEQUENTIAL)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inD = schedulerD.buildInputWire("inD");
        inD.bind(buildHandler(random, 0.6, lock)); // This must be >0.5 else risk infinite loop
        final OutputWire<Long> outD = schedulerD.getOutputWire();

        final TaskScheduler<Long> schedulerE = wiringModel
                .schedulerBuilder("E")
                .withType(SEQUENTIAL)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inE = schedulerE.buildInputWire("inE");
        inE.bind(buildHandler(random, 0.01, lock));
        final OutputWire<Long> outE = schedulerE.getOutputWire();

        final TaskScheduler<Long> schedulerF = wiringModel
                .schedulerBuilder("F")
                .withType(SEQUENTIAL)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inF = schedulerF.buildInputWire("inF");
        inF.bind(buildHandler(random, 0.01, lock));
        final OutputWire<Long> outF = schedulerF.getOutputWire();

        final TaskScheduler<Long> schedulerG = wiringModel
                .schedulerBuilder("G")
                .withType(SEQUENTIAL)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inG = schedulerG.buildInputWire("inG");
        inG.bind(buildHandler(random, 0.01, lock));
        final OutputWire<Long> outG = schedulerG.getOutputWire();

        final TaskScheduler<Long> schedulerH = wiringModel
                .schedulerBuilder("H")
                .withType(DIRECT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inH = schedulerH.buildInputWire("inH");
        inH.bind(buildHandler(random, 0.01, lock));
        final OutputWire<Long> outH = schedulerH.getOutputWire();

        final TaskScheduler<Long> schedulerI = wiringModel
                .schedulerBuilder("I")
                .withType(DIRECT_THREADSAFE)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inI = schedulerI.buildInputWire("inI");
        inI.bind(buildHandler(random, 0.01, lock));
        final OutputWire<Long> outI = schedulerI.getOutputWire();

        final TaskScheduler<Long> schedulerJ = wiringModel
                .schedulerBuilder("J")
                .withType(SEQUENTIAL)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inJ = schedulerJ.buildInputWire("inJ");
        inJ.bind(buildHandler(random, 0.01, lock));
        final OutputWire<Long> outJ = schedulerJ.getOutputWire();

        final TaskScheduler<Long> schedulerK = wiringModel
                .schedulerBuilder("K")
                .withType(NO_OP)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .withFlushingEnabled(true)
                .build()
                .cast();
        final BindableInputWire<Long, Long> inK = schedulerK.buildInputWire("inK");
        inK.bind(buildHandler(random, 0.01, lock));

        outA.solderTo(inB);
        if (enableHeartbeat) {
            wiringModel.buildHeartbeatWire(Duration.ofMillis(5)).solderTo(schedulerBHeartbeat);
        }
        outB.solderTo(inC);
        outC.solderTo(inD);
        outD.solderTo(inE);
        outE.solderTo(inF);
        outF.solderTo(inG);
        outG.solderTo(inD);
        outF.solderTo(inH);
        outH.solderTo(inI);
        outI.solderTo(inJ);
        outI.solderTo(inK);

        // Write the final output to the atomic long provided
        final AtomicLong outputValue = new AtomicLong();
        outJ.solderTo("finalOutput", "data", outputValue::set);

        final BooleanSupplier isQuiescent = () -> {
            lock.lock();
            try {
                return schedulerA.getUnprocessedTaskCount() == 0
                        && schedulerB.getUnprocessedTaskCount() == 0
                        && schedulerC.getUnprocessedTaskCount() == 0
                        && schedulerD.getUnprocessedTaskCount() == 0
                        && schedulerE.getUnprocessedTaskCount() == 0
                        && schedulerF.getUnprocessedTaskCount() == 0
                        && schedulerG.getUnprocessedTaskCount() == 0
                        && schedulerH.getUnprocessedTaskCount() == 0
                        && schedulerI.getUnprocessedTaskCount() == 0
                        && schedulerJ.getUnprocessedTaskCount() == 0
                        && schedulerK.getUnprocessedTaskCount() == 0;
            } finally {
                lock.unlock();
            }
        };

        wiringModel.start();

        // Enable this and copy it into mermaid.live to visualize the wiring in this test
        // final String diagram = wiringModel.generateWiringDiagram(List.of(), List.of(), List.of(), false);
        // System.out.println(diagram);

        return new WiringMesh(inA, outputValue, isQuiescent);
    }

    /**
     * Feed a bunch of data into the mesh and return the value once all data has been processed.
     *
     * @param seed             the seed to use for the random number generator
     * @param mesh             the wiring mesh to evaluate
     * @param waitForNextCycle a runnable that will be called each time the mesh is checked for quiescence
     */
    private static long evaluateMesh(
            @NonNull final long seed, @NonNull final WiringMesh mesh, @NonNull final Runnable waitForNextCycle) {

        final Random random = new Random(seed);

        // Feed in data
        for (long i = 0; i < 100; i++) {
            final long next = random.nextLong();
            mesh.inputWire.put(next);
        }

        int maxIterations = 10_000;
        while (!mesh.isQuiescent.getAsBoolean()) {
            waitForNextCycle.run();

            maxIterations--;
            assertTrue(maxIterations > 0, "mesh did not quiesce in time");
        }

        return mesh.outputValue.get();
    }

    /**
     * The purpose of this test is to verify that the test setup is non-deterministic when using the standard wiring
     * model.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void verifyStandardNondeterminism(final boolean enableHeartbeat) {
        final Random random = getRandomPrintSeed();
        final long meshSeed = random.nextLong();
        final long dataSeed = random.nextLong();

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final long value1 = evaluateMesh(
                dataSeed,
                generateWiringMesh(
                        meshSeed,
                        com.hedera.wiring.model.WiringModelBuilder.create(platformContext)
                                .build(),
                        enableHeartbeat),
                () -> {
                    try {
                        MILLISECONDS.sleep(1);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });

        final long value2 = evaluateMesh(
                dataSeed,
                generateWiringMesh(
                        meshSeed,
                        com.hedera.wiring.model.WiringModelBuilder.create(platformContext)
                                .build(),
                        enableHeartbeat),
                () -> {
                    try {
                        MILLISECONDS.sleep(1);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                });

        assertNotEquals(value1, value2);
    }

    @Test
    void verifyDeterministicModel() {
        final Random random = getRandomPrintSeed();
        final long meshSeed = random.nextLong();
        final long dataSeed = random.nextLong();

        final FakeTime time = new FakeTime(randomInstant(random), Duration.ZERO);
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().withTime(time).build();

        final com.hedera.wiring.model.DeterministicWiringModel deterministicWiringModel1 =
                com.hedera.wiring.model.WiringModelBuilder.create(platformContext)
                        .withDeterministicModeEnabled(true)
                        .build();
        final long value1 =
                evaluateMesh(dataSeed, generateWiringMesh(meshSeed, deterministicWiringModel1, true), () -> {
                    time.tick(Duration.ofMillis(1));
                    deterministicWiringModel1.tick();
                });

        time.reset();
        final com.hedera.wiring.model.DeterministicWiringModel deterministicWiringModel2 =
                com.hedera.wiring.model.WiringModelBuilder.create(platformContext)
                        .withDeterministicModeEnabled(true)
                        .build();
        final long value2 =
                evaluateMesh(dataSeed, generateWiringMesh(meshSeed, deterministicWiringModel2, true), () -> {
                    time.tick(Duration.ofMillis(1));
                    deterministicWiringModel2.tick();
                });

        assertEquals(value1, value2);
    }

    /**
     * Test a scenario where there is a circular data flow formed by wires.
     * <p>
     * In this test, all data is passed from A to B to C to D. All data that is a multiple of 7 is passed from D to A as
     * a negative value, but is not passed around the loop again.
     *
     * <pre>
     * A -------> B
     * ^          |
     * |          |
     * |          V
     * D <------- C
     * </pre>
     */
    @Test
    void circularDataFlowTest() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final DeterministicWiringModel model = WiringModelBuilder.create(platformContext)
                .withDeterministicModeEnabled(true)
                .build();

        final AtomicInteger countA = new AtomicInteger();
        final AtomicInteger negativeCountA = new AtomicInteger();
        final AtomicInteger countB = new AtomicInteger();
        final AtomicInteger countC = new AtomicInteger();
        final AtomicInteger countD = new AtomicInteger();

        final TaskScheduler<Integer> taskSchedulerToA = model.schedulerBuilder("wireToA")
                .withType(SEQUENTIAL)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final TaskScheduler<Integer> taskSchedulerToB = model.schedulerBuilder("wireToB")
                .withType(SEQUENTIAL_THREAD)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final TaskScheduler<Integer> taskSchedulerToC = model.schedulerBuilder("wireToC")
                .withType(CONCURRENT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();
        final TaskScheduler<Integer> taskSchedulerToD = model.schedulerBuilder("wireToD")
                .withType(DIRECT)
                .withUnhandledTaskCapacity(UNLIMITED_CAPACITY)
                .build()
                .cast();

        final BindableInputWire<Integer, Integer> channelToA = taskSchedulerToA.buildInputWire("channelToA");
        final BindableInputWire<Integer, Integer> channelToB = taskSchedulerToB.buildInputWire("channelToB");
        final BindableInputWire<Integer, Integer> channelToC = taskSchedulerToC.buildInputWire("channelToC");
        final BindableInputWire<Integer, Integer> channelToD = taskSchedulerToD.buildInputWire("channelToD");

        final Function<Integer, Integer> handlerA = x -> {
            if (x > 0) {
                countA.set(hash32(x, countA.get()));
                return x;
            } else {
                negativeCountA.set(hash32(x, negativeCountA.get()));
                // negative values are values that have been passed around the loop
                // Don't pass them on again or else we will get an infinite loop
                return null;
            }
        };

        final Function<Integer, Integer> handlerB = x -> {
            countB.set(hash32(x, countB.get()));
            return x;
        };

        final Function<Integer, Integer> handlerC = x -> {
            countC.set(hash32(x, countC.get()));
            return x;
        };

        final Function<Integer, Integer> handlerD = x -> {
            countD.set(hash32(x, countD.get()));
            if (x % 7 == 0) {
                return -x;
            } else {
                return null;
            }
        };

        taskSchedulerToA.getOutputWire().solderTo(channelToB);
        taskSchedulerToB.getOutputWire().solderTo(channelToC);
        taskSchedulerToC.getOutputWire().solderTo(channelToD);
        taskSchedulerToD.getOutputWire().solderTo(channelToA);

        channelToA.bind(handlerA);
        channelToB.bind(handlerB);
        channelToC.bind(handlerC);
        channelToD.bind(handlerD);

        model.start();

        int expectedCountA = 0;
        int expectedNegativeCountA = 0;
        int expectedCountB = 0;
        int expectedCountC = 0;
        int expectedCountD = 0;

        for (int i = 1; i < 1000; i++) {
            channelToA.put(i);

            expectedCountA = hash32(i, expectedCountA);
            expectedCountB = hash32(i, expectedCountB);
            expectedCountC = hash32(i, expectedCountC);
            expectedCountD = hash32(i, expectedCountD);

            if (i % 7 == 0) {
                expectedNegativeCountA = hash32(-i, expectedNegativeCountA);
            }
        }

        int maxTicks = 10_000;
        while (true) {
            if (maxTicks-- == 0) {
                fail("Model did not quiesce in time");
            }
            model.tick();
            if (expectedCountA == countA.get()
                    && expectedNegativeCountA == negativeCountA.get()
                    && expectedCountB == countB.get()
                    && expectedCountC == countC.get()
                    && expectedCountD == countD.get()) {
                break;
            }
        }

        model.stop();
    }
}
