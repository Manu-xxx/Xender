/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery.emergencyfile;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.Settings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Defines all data related to the emergency recovery file and how it is formatted.
 */
public record EmergencyRecoveryFile(Recovery recovery) {
    private static final String OUTPUT_FILENAME = "emergencyRecovery.yaml";
    private static final String INPUT_FILENAME = Settings.getInstance().getEmergencyRecoveryStateFileName();

    /**
     * Creates a new emergency recovery file with data about a state being written to disk in normal operation.
     *
     * @param round     the round number of the state this file is for
     * @param hash      the hash of the state this file is for
     * @param timestamp the consensus timestamp of the state this file is for
     */
    public EmergencyRecoveryFile(final long round, final Hash hash, final Instant timestamp) {
        this(new Recovery(new State(round, hash, timestamp), null, null, null));
    }

    /**
     * Creates a new emergency recovery file with data about the state resulting from event recovery disk and the
     * consensus time of the bootstrap state used to perform event recovery.
     *
     * @param state         emergency recovery data for the state resulting from the event recovery process
     * @param bootstrapTime the consensus timestamp of the bootstrap state used to start the event recovery process
     */
    public EmergencyRecoveryFile(final State state, final Instant bootstrapTime) {
        this(new Recovery(state, new Bootstrap(bootstrapTime), null, null));
    }

    /**
     * @return the round number of the state this file is for
     */
    public long round() {
        return recovery().state().round();
    }

    /**
     * @return the hash of the state this file is for
     */
    public Hash hash() {
        return recovery().state().hash();
    }

    /**
     * @return the consensus timestamp of the state this file is for
     */
    public Instant timestamp() {
        return recovery().state().timestamp();
    }

    /**
     * @return the structure of the emergency recovery file
     */
    public Recovery recovery() {
        return recovery;
    }

    /**
     * Write the data in this record to a file at the specified directory.
     *
     * @param directory the directory to write to. Must exist and be writable.
     * @throws IOException if an exception occurs creating or writing to the file
     */
    public void write(final Path directory) throws IOException {
        final ObjectMapper mapper =
                new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER));
        mapper.writeValue(directory.resolve(OUTPUT_FILENAME).toFile(), this);
    }

    /**
     * Creates a record with the data contained in the emergency recovery file in the directory specified, or null if
     * the file does not exist.
     *
     * @param directory the directory containing the emergency recovery file. Must exist and be readable.
     * @param failOnMissingFields if true, throw an exception if the file is missing any fields. If false, ignore
     * @return a new record containing the emergency recovery data in the file, or null if no emergency recovery file
     * exists
     * @throws IOException if an exception occurs reading from the file, or the file content is not properly formatted
     */
    public static EmergencyRecoveryFile read(final Path directory, final boolean failOnMissingFields)
            throws IOException {
        final Path fileToRead = directory.resolve(INPUT_FILENAME);
        if (!Files.exists(fileToRead)) {
            return null;
        }
        final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
        if (failOnMissingFields) {
            mapper.configure(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES, true);
            mapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true);
        }
        final EmergencyRecoveryFile file = mapper.readValue(fileToRead.toFile(), EmergencyRecoveryFile.class);
        validate(file);
        return file;
    }

    /**
     * Same as {@link #read(Path, boolean)} but with failOnMissingFields set to false.
     */
    public static EmergencyRecoveryFile read(final Path directory) throws IOException {
        return read(directory, false);
    }

    private static void validate(final EmergencyRecoveryFile file) throws IOException {
        if (file == null) {
            throw new IOException("Failed to read emergency recovery file, object mapper returned null value");
        }

        if (file.hash() == null) {
            throw new IOException("Required field 'hash' is null.");
        }
    }
}
