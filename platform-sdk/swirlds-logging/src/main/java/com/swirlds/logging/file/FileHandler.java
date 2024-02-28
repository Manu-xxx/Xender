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

package com.swirlds.logging.file;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.api.extensions.handler.AbstractSyncedHandler;
import com.swirlds.logging.api.internal.format.LineBasedFormat;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A file handler that writes log events to a file.
 * <p>
 * This handler use a {@link BufferedWriter} to write {@link LogEvent}s to a file. You can configure the following
 * properties:
 * <ul>
 *     <li>{@code file} - the {@link Path} of the file</li>
 *     <li>{@code append} - whether to append to the file or not</li>
 * </ul>
 */
public class FileHandler extends AbstractSyncedHandler {

    private static final String FILE_NAME_PROPERTY = "%s.file";
    private static final String APPEND_PROPERTY = "%s.append";
    private static final String DEFAULT_FILE_NAME = "swirlds-log.log";
    private static final int BUFFER_CAPACITY = 8 * 1024;
    private static final int SMALL_BUFFER = 4 * 1024;
    private OutputStream writer;
    private final ByteBuffer buffer = ByteBuffer.wrap(new byte[BUFFER_CAPACITY]);

    private final Lock bufferLock = new ReentrantLock();

    private final LineBasedFormat format;

    /**
     * Creates a new file handler.
     *
     * @param handlerName   the unique handler name
     * @param configuration the configuration
     */
    public FileHandler(@NonNull final String handlerName, @NonNull final Configuration configuration) {
        super(handlerName, configuration);

        format = LineBasedFormat.createForHandler(handlerName, configuration);

        final String propertyPrefix = PROPERTY_HANDLER.formatted(handlerName);
        final Path filePath = Objects.requireNonNullElse(
                configuration.getValue(FILE_NAME_PROPERTY.formatted(propertyPrefix), Path.class, null),
                Path.of(DEFAULT_FILE_NAME));
        final boolean append = Objects.requireNonNullElse(
                configuration.getValue(APPEND_PROPERTY.formatted(propertyPrefix), Boolean.class, null), true);

        BufferedWriter bufferedWriter = null;
        try {
            if (!Files.exists(filePath) || Files.isWritable(filePath)) {
                writer = new FileOutputStream(filePath.toFile(), append);

//                if (append) {
//                    bufferedWriter = Files.newBufferedWriter(
//                            filePath,
//                            StandardOpenOption.CREATE,
//                            StandardOpenOption.APPEND,
//                            StandardOpenOption.WRITE,
//                            StandardOpenOption.DSYNC);
//                } else {
//                    bufferedWriter = Files.newBufferedWriter(
//                            filePath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.DSYNC);
//                }
            } else {
                EMERGENCY_LOGGER.log(Level.ERROR, "Log file could not be created or written to");
            }
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to create FileHandler", exception);
        }
        //this.writer = bufferedWriter;
    }

    /**
     * Handles a log event by appending it to the file using the {@link LineBasedFormat}.
     *
     * @param event The log event to be printed.
     */
    @Override
    protected void handleEvent(@NonNull final LogEvent event) {
        final StringBuilder msgBuffer = new StringBuilder(SMALL_BUFFER);
        format.print(msgBuffer, event);
        bufferLock.lock();
        try {
            final int available = buffer.capacity() - buffer.position();
            if (available < msgBuffer.length()) {
                try {
                    if (writer != null) {
                        writer.write(buffer.array());
                        buffer.clear();
                    } else {
                        throw new IllegalStateException("BufferedWriter is null");
                    }
                } catch (final Exception exception) {
                    EMERGENCY_LOGGER.log(Level.ERROR, "Failed to write to file", exception);
                }
            }

            buffer.put(msgBuffer.toString().getBytes());
        } finally {
            bufferLock.unlock();
        }
    }

    @Override
    protected void handleStopAndFinalize() {
        super.handleStopAndFinalize();
        try {
            if (writer != null) {
                writer.write(buffer.array());
                writer.flush();
                writer.close();
            }
        } catch (final Exception exception) {
            EMERGENCY_LOGGER.log(Level.ERROR, "Failed to close file output stream", exception);
        }
    }
}
