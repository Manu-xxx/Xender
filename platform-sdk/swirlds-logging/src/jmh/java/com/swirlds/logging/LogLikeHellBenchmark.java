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

package com.swirlds.logging;

import static com.swirlds.logging.util.BenchmarkConstants.FORK_COUNT;
import static com.swirlds.logging.util.BenchmarkConstants.MEASUREMENT_ITERATIONS;
import static com.swirlds.logging.util.BenchmarkConstants.MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION;
import static com.swirlds.logging.util.BenchmarkConstants.PARALLEL_THREAD_COUNT;
import static com.swirlds.logging.util.BenchmarkConstants.WARMUP_ITERATIONS;
import static com.swirlds.logging.util.BenchmarkConstants.WARMUP_TIME_IN_SECONDS_PER_ITERATION;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.benchmark.ConfigureLog;
import com.swirlds.logging.benchmark.LogLikeHell;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
public class LogLikeHellBenchmark {

    @Param({"FILE", "CONSOLE", "FILE_AND_CONSOLE"})
    public String loggingType;

    Logger logger;
    LoggingSystem loggingSystem;
    LogLikeHell logLikeHell;

    @Setup(org.openjdk.jmh.annotations.Level.Iteration)
    public void init() throws Exception {
        Files.deleteIfExists(Path.of("log-like-hell-benchmark.log"));

        if (Objects.equals(loggingType, "FILE")) {
            loggingSystem = ConfigureLog.configureFileLogging();
        } else if (Objects.equals(loggingType, "CONSOLE")) {
            loggingSystem = ConfigureLog.configureConsoleLogging();
        } else if (Objects.equals(loggingType, "FILE_AND_CONSOLE")) {
            loggingSystem = ConfigureLog.configureFileAndConsoleLogging();
        }

        if (loggingSystem != null) {
            logger = loggingSystem.getLogger(LogLikeHellBenchmark.class.getSimpleName());
            logLikeHell = new LogLikeHell(logger);
        } else {
            throw new IllegalStateException("Invalid logging type: " + loggingType);
        }
    }

    @Benchmark
    @Fork(FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME_IN_SECONDS_PER_ITERATION)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION)
    public void runLogLikeHell() {
        logLikeHell.run();
    }

    @Benchmark
    @Fork(FORK_COUNT)
    @Threads(PARALLEL_THREAD_COUNT)
    @BenchmarkMode(Mode.Throughput)
    @Warmup(iterations = WARMUP_ITERATIONS, time = WARMUP_TIME_IN_SECONDS_PER_ITERATION)
    @Measurement(iterations = MEASUREMENT_ITERATIONS, time = MEASUREMENT_TIME_IN_SECONDS_PER_ITERATION)
    public void runSingleSimpleLog() {
        logger.log(Level.INFO, "Hello World");
    }


}
