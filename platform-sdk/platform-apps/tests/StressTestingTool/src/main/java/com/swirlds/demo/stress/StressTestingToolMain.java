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

package com.swirlds.demo.stress;
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.units.UnitConstants.MILLISECONDS_TO_NANOSECONDS;
import static com.swirlds.common.units.UnitConstants.NANOSECONDS_TO_MICROSECONDS;
import static com.swirlds.common.units.UnitConstants.NANOSECONDS_TO_SECONDS;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.platform.Browser;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A testing tool which generates a number of transactions per second, and simulates handling them
 */
public class StressTestingToolMain implements SwirldMain {
    private static final Logger logger = LogManager.getLogger(StressTestingToolMain.class);
    private static final BasicSoftwareVersion SOFTWARE_VERSION = new BasicSoftwareVersion(1);
    /**
     * the time of the last measurement of TPS
     */
    long lastTPSMeasureTime = 0;
    /**
     * number of events needed to be created (the non-integer leftover from last preEvent call
     */
    double toCreate = 0;
    /**
     * the app is run by this
     */
    private Platform platform;

    private TransactionPool transactionPool;

    private final StoppableThread transactionGenerator;

    private static final SpeedometerMetric.Config TRAN_SUBMIT_TPS_SPEED_CONFIG =
            new SpeedometerMetric.Config("Debug.info", "tranSubTPS").withDescription("Transaction submitted TPS");

    private SpeedometerMetric transactionSubmitSpeedometer;

    /**
     * The number of milliseconds of the window period to measure TPS over
     */
    private int tps_measure_window_milliseconds = 200;

    /**
     * The expected TPS for the network
     */
    private double expectedTPS = 0;

    /** The constant used to calculate the window size, the higher the expected TPS, the smaller the window */
    private static final long WINDOW_CALCULATION_CONST = 125000;

    /**
     * The number of seconds to ramp up the TPS to the expected value
     */
    private static final int TPS_RAMP_UP_WINDOW_MILLISECONDS = 20_000;

    /**
     * The timestamp when the ramp up started
     */
    private long rampUpStartTimeMilliSeconds = 0;

    /** App configuration */
    private StressTestingToolConfig config;

    /**
     * This is just for debugging: it allows the app to run in Eclipse. If the config.txt exists and lists a particular
     * SwirldMain class as the one to run, then it can run in Eclipse (with the green triangle icon).
     *
     * @param args these are not used
     */
    public static void main(final String[] args) {
        Browser.parseCommandLineArgsAndLaunch(args);
    }

    public StressTestingToolMain() {
        System.out.println("Main");
        transactionGenerator = new StoppableThreadConfiguration<>(getStaticThreadManager())
                .setComponent("demo")
                .setThreadName("transaction-generator")
                .setMaximumRate(50)
                .setWork(this::generateTransactions)
                .build();
    }

    @Override
    public void updateConfigurationBuilder(@NonNull final ConfigurationBuilder configurationBuilder) {
        configurationBuilder.withConfigDataType(StressTestingToolConfig.class);
    }

    @Override
    public void init(@NonNull final Platform platform, @NonNull final NodeId id) {
        this.platform = platform;
        config = platform.getContext().getConfiguration().getConfigData(StressTestingToolConfig.class);
        expectedTPS = config.transPerSecToCreate()
                / (double) platform.getAddressBook().getSize();

        // the higher the expected TPS, the smaller the window
        tps_measure_window_milliseconds = (int) (WINDOW_CALCULATION_CONST / expectedTPS);

        transactionPool = new TransactionPool(config.transPoolSize(), config.bytesPerTrans());

        final Metrics metrics = platform.getContext().getMetrics();
        transactionSubmitSpeedometer = metrics.getOrCreate(TRAN_SUBMIT_TPS_SPEED_CONFIG);
    }

    @Override
    public void run() {
        final Thread shutdownHook = new ThreadConfiguration(getStaticThreadManager())
                .setDaemon(false)
                .setNodeId(platform.getSelfId())
                .setComponent("app")
                .setThreadName("demo_log_time_pulse")
                .setRunnable(() -> {
                    final Logger logger = LogManager.getLogger(getClass());
                    logger.debug(STARTUP.getMarker(), "Keepalive Event for Regression Timing");
                })
                .build();

        Runtime.getRuntime().addShutdownHook(shutdownHook);

        System.out.println("starting transaction generator");
        transactionGenerator.start();

        while (true) {
            try {
                Thread.sleep(1000);
                generateTransactions();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private synchronized void generateTransactions() {
        if (config == null) {
            // if the app has not been initialized yet, do nothing
            return;
        }

        System.out.println("transaction generator started");
        byte[] transaction;
        final long now = System.nanoTime();
        int numCreated = 0;

        // if it's first time calling this, just set lastEventTime and return
        // so the period from app start to the first time run() is called is ignored
        // to avoid a huge burst of transactions at the start of the test
        if (lastTPSMeasureTime == 0) {
            lastTPSMeasureTime = now;
            rampUpStartTimeMilliSeconds = now / MILLISECONDS_TO_NANOSECONDS;
            System.out.println("configured TPS: " + expectedTPS);
            logger.info(
                    STARTUP.getMarker(),
                    "First time calling generateTransactions() Expected TPS per code is {}",
                    expectedTPS);
            return;
        }

        // ramp up the TPS to the expected value
        long elapsedTime = now / MILLISECONDS_TO_NANOSECONDS - rampUpStartTimeMilliSeconds;
        final double rampUpTPS;
        if (elapsedTime < TPS_RAMP_UP_WINDOW_MILLISECONDS) {
            rampUpTPS = expectedTPS * elapsedTime / ((double) (TPS_RAMP_UP_WINDOW_MILLISECONDS));
        } else {
            rampUpTPS = expectedTPS;
        }

        // for every measure window, re-calculate the toCreate counter
        if (((double) now - lastTPSMeasureTime) * NANOSECONDS_TO_MICROSECONDS > tps_measure_window_milliseconds) {
            toCreate = ((double) now - lastTPSMeasureTime) * NANOSECONDS_TO_SECONDS * rampUpTPS;
            lastTPSMeasureTime = now;
        }

        while (true) {
            if (toCreate < 1) {
                break; // don't create too many transactions per second
            }

            // Retrieve a random signed transaction from the pool
            transaction = transactionPool.transaction();

            if (!platform.createTransaction(transaction)) {
                break; // if the queue is full, the stop adding to it
            }

            numCreated++;
            toCreate--;
        }
        transactionSubmitSpeedometer.update(numCreated);
        // toCreate will now represent any leftover transactions that we
        // failed to create this time, and will create next time
    }

    @Override
    public SwirldState newState() {
        return new StressTestingToolState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSoftwareVersion getSoftwareVersion() {
        return SOFTWARE_VERSION;
    }
}
