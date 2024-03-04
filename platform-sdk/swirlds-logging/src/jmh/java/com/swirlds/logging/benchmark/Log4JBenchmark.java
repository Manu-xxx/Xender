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

package com.swirlds.logging.benchmark;

import static com.swirlds.logging.benchmark.config.LoggingHandlingType.CONSOLE_AND_FILE_TYPE;
import static com.swirlds.logging.benchmark.config.LoggingHandlingType.CONSOLE_TYPE;
import static com.swirlds.logging.benchmark.config.LoggingHandlingType.FILE_TYPE;
import static com.swirlds.logging.benchmark.config.BenchmarkConfigConstants.FORK_COUNT;
import static com.swirlds.logging.benchmark.config.BenchmarkConfigConstants.MEASUREMENT_ITERATIONS;
import static com.swirlds.logging.benchmark.config.BenchmarkConfigConstants.MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION;
import static com.swirlds.logging.benchmark.config.BenchmarkConfigConstants.PARALLEL_THREAD_COUNT;
import static com.swirlds.logging.benchmark.config.BenchmarkConfigConstants.WARMUP_ITERATIONS;
import static com.swirlds.logging.benchmark.config.BenchmarkConfigConstants.WARMUP_TIME_IN_SECONDS_PER_ITERATION;

import java.util.Objects;
import org.apache.logging.log4j.Logger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
public class Log4JBenchmark {

    @Param({CONSOLE_TYPE, FILE_TYPE, CONSOLE_AND_FILE_TYPE})
    public String loggingType;

    private Logger logger;
    private LogWithLog4J logRunner;

    @Setup(Level.Trial)
    public void init() {
        if (Objects.equals(loggingType, FILE_TYPE)) {
            logger = ConfigureLog4J.configureFileLogging().getLogger("Log4JBenchmark");
        } else if (Objects.equals(loggingType, CONSOLE_TYPE)) {
            logger = ConfigureLog4J.configureConsoleLogging().getLogger("Log4JBenchmark");
        } else if (Objects.equals(loggingType, CONSOLE_AND_FILE_TYPE)) {
            logger = ConfigureLog4J.configureFileAndConsoleLogging().getLogger("Log4JBenchmark");
        }
        logRunner = new LogWithLog4J(logger);
    }

    @Benchmark
    @Fork(FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME_IN_SECONDS_PER_ITERATION)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION)
    public void log4J() {
        logRunner.run();
    }
}
