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

package com.swirlds.logging.rolling;

import static com.swirlds.logging.utils.ConfigUtils.configValueOrElse;
import static com.swirlds.logging.utils.ConfigUtils.readDataSizeInBytes;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.AbstractSyncedHandler;
import com.swirlds.logging.api.internal.format.FormattedLinePrinter;
import com.swirlds.logging.io.BufferedOutputStream;
import com.swirlds.logging.io.RolloverFileOutputStream;
import com.swirlds.logging.utils.FileUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * A file handler that writes log events to a file with optional rolling based on size or size and date.
 * <p>
 * The rolling behavior of the underlying file is determined by the provided configuration. When enabled, rolling occurs
 * based on size or a combination of size and date.
 * To enable it at least {@code file-rolling.maxFileSize} property needs to be informed.
 * <p>
 * Rolling is implemented with a best effort strategy. Log events are initially written to the file, and rolling occurs
 * if the file size exceeds the configured limit. This approach maintains data coherence and avoids the performance
 * penalties associated with handling files in a highly specific manner. However, it may result in occasional
 * file sizes exceeding the limit, depending on the volume of data being written.
 * <p>
 * The handler can be optionally buffered for improved performance.
 * <p>
 * The handler can be configured with the following properties:
 * <ul>
 *     <li>{@code file} - The {@link Path} of the log file.</li>
 *     <li>{@code append} - Determines whether to append to an existing file or overwrite it.</li>
 *     <li>{@code formatTimestamp} - If set to true, epoch values are formatted as human-readable strings.</li>
 *     <li>{@code file-rolling.maxRollover} - Maximum number of files used for rolling.</li>
 *     <li>{@code file-rolling.maxFileSize} - Maximum size of the file for size-based rolling.</li>
 *     <li>{@code file-rolling.datePattern} - Date pattern for enabling size and date-based rolling.</li>
 * </ul>
 */
public class RollingFileHandler extends AbstractSyncedHandler {

    private static final String FILE_NAME_PROPERTY = ".file";
    private static final String APPEND_PROPERTY = ".append";
    private static final String SIZE_PROPERTY = ".file-rolling.maxFileSize";
    private static final String MAX_ROLLOVER = ".file-rolling.maxRollover";
    private static final String DATE_PATTERN_PROPERTY = ".file-rolling.datePattern";
    private static final String DEFAULT_FILE_NAME = "swirlds-log.log";
    private static final int DEFAULT_MAX_ROLLOVER_FILES = 1;
    private static final int BUFFER_CAPACITY = 8192 * 8;
    private static final int EVENT_LOG_PRINTER_SIZE = 4 * 1024;
    private final OutputStream outputStream;
    private final FormattedLinePrinter format;

    /**
     * Creates a new file handler.
     *
     * @param handlerName   the unique handler name
     * @param configuration the configuration
     * @param buffered      if true a buffer is used in between the file writing
     */
    public RollingFileHandler(
            @NonNull final String handlerName, @NonNull final Configuration configuration, final boolean buffered)
            throws IOException {
        super(handlerName, configuration);
        final String propertyPrefix = PROPERTY_HANDLER.formatted(handlerName);
        this.format = FormattedLinePrinter.createForHandler(handlerName, configuration);
        try {
            final OutputStream fileOutputStream = outputStream(configuration, propertyPrefix);
            this.outputStream =
                    buffered ? new BufferedOutputStream(fileOutputStream, BUFFER_CAPACITY) : fileOutputStream;
        } catch (IOException e) {
            throw new IOException("Could not create rolling handler", e);
        }
    }

    @NonNull
    private static OutputStream outputStream(
            final @NonNull Configuration configuration, final @NonNull String propertyPrefix) throws IOException {

        final Path filePath = configValueOrElse(
                configuration, propertyPrefix + FILE_NAME_PROPERTY, Path.class, Path.of(DEFAULT_FILE_NAME));

        try {
            FileUtils.checkOrCreateParentDirectory(filePath);
            final boolean append =
                    configValueOrElse(configuration, propertyPrefix + APPEND_PROPERTY, Boolean.class, true);
            final Long maxFileSize = readDataSizeInBytes(configuration, propertyPrefix + SIZE_PROPERTY);

            if (maxFileSize == null) {
                return new FileOutputStream(filePath.toFile(), append);
            }

            final Integer maxRollingOver = configValueOrElse(
                    configuration, propertyPrefix + MAX_ROLLOVER, Integer.class, DEFAULT_MAX_ROLLOVER_FILES);
            final String datePattern =
                    configuration.getValue(propertyPrefix + DATE_PATTERN_PROPERTY, String.class, null);
            return new RolloverFileOutputStream(filePath, maxFileSize, append, maxRollingOver, datePattern);
        } catch (Exception e) {
            throw new IOException("Could not create log file " + filePath.toAbsolutePath(), e);
        }
    }

    /**
     * Handles a log event by appending it to the file using the {@link FormattedLinePrinter}.
     *
     * @param event The log event to be printed.
     */
    @Override
    protected void handleEvent(@NonNull final LogEvent event) {
        final StringBuilder writer = new StringBuilder(EVENT_LOG_PRINTER_SIZE);
        format.print(writer, event);
        try {
            this.outputStream.write(writer.toString().getBytes(StandardCharsets.UTF_8));
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to write to file output stream", exception);
            // FORWARDING the event to the emergency logger
            EMERGENCY_LOGGER.log(event);
        }
    }

    /**
     * Stops the handler and no further events are processed
     */
    @Override
    protected void handleStopAndFinalize() {
        super.handleStopAndFinalize();
        try {
            outputStream.close();
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to close file output stream", exception);
        }
    }
}
