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

package com.swirlds.platform.util;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.throttle.RateLimitedLogger;
import com.swirlds.logging.legacy.LogMarker;
import com.swirlds.platform.config.PathsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Writes marker files with the given filename to disk in the configured directory.  If there is no configured
 * directory, no marker files are written.
 */
public class MarkerFileWriter {

    /**
     * The logger for this class.
     */
    private static final Logger logger = LogManager.getLogger(MarkerFileWriter.class);

    /**
     * Flag to indicate if we still need to log an info message about the marker file directory not being set.
     */
    private static final AtomicBoolean logMarkerFileDirectoryNotSet = new AtomicBoolean(true);

    /**
     * Rate limited logger for failure to write marker files.
     */
    private final RateLimitedLogger failedToWriteMarkerFileLogger;

    /**
     * The directory where the marker files are written.  If null, no marker files are written.
     */
    private final Path markerFileDirectory;

    /**
     * Creates a new {@link MarkerFileWriter} with the given {@link PlatformContext}.  If the marker file writer is
     * enabled, files are written to the configured directory.
     *
     * @param platformContext the platform context containing configuration.
     */
    public MarkerFileWriter(@NonNull final PlatformContext platformContext) {
        final boolean enabled = platformContext
                .getConfiguration()
                .getConfigData(PathsConfig.class)
                .writePlatformMarkerFiles();
        final Path markerFileDirectoryPath = platformContext
                .getConfiguration()
                .getConfigData(PathsConfig.class)
                .getMarkerFilesDir();
        failedToWriteMarkerFileLogger = new RateLimitedLogger(logger, platformContext.getTime(), Duration.ofMinutes(1));
        Path directory = null;
        if (enabled) {
            if (markerFileDirectoryPath != null) {
                try {
                    Files.createDirectories(markerFileDirectoryPath);
                    directory = markerFileDirectoryPath;
                } catch (final IOException e) {
                    directory = null;
                    logger.error(
                            LogMarker.EXCEPTION.getMarker(),
                            "Failed to create marker file directory: {}",
                            markerFileDirectoryPath,
                            e);
                }
            } else {
                logger.error(LogMarker.STARTUP.getMarker(), "Marker file writing is turned on but directory is null.");
            }
        }
        markerFileDirectory = directory;
    }

    /**
     * Writes a marker file with the given filename to the configured directory.
     *
     * @param filename the name of the marker file to write.
     */
    public void writeMarkerFile(@NonNull final String filename) {
        if (markerFileDirectory == null) {
            // Configuration did not set a marker file directory.  No need to write marker file.
            return;
        }
        final Path markerFile = markerFileDirectory.resolve(filename);
        if (Files.exists(markerFile)) {
            // No need to create file when it already exists.
            return;
        }
        try {
            Files.createFile(markerFile);
        } catch (final IOException e) {
            failedToWriteMarkerFileLogger.error(
                    LogMarker.EXCEPTION.getMarker(), "Failed to create marker file: {}", markerFile, e);
        }
    }

    /**
     * Returns the directory where the marker files are written.  May be null.
     *
     * @return the directory where the marker files are written.  This may be null.
     */
    @Nullable
    public Path getMarkerFileDirectory() {
        return markerFileDirectory;
    }
}
