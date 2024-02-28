/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.logging.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LogFileUtlis {

    public static String provideLogFilePath(LoggingImplementation implementation, LoggingHandlingType type) {
        final long pid = ProcessHandle.current().pid();
        final String path = "logging-out/benchmark-" + implementation + "-" + pid + "-" + type + ".log";
        deleteFile(path);
        return path;
    }

    private static void deleteFile(final String logFile) {
        try {
            Files.deleteIfExists(Path.of(logFile));
        } catch (IOException e) {
            throw new RuntimeException("Can not delete old log file", e);
        }
    }
}
