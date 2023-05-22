/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.event.preconsensus;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomInstant;
import static com.swirlds.common.test.io.FileManipulation.writeRandomBytes;
import static com.swirlds.platform.event.preconsensus.PreConsensusEventFile.EVENT_FILE_SEPARATOR;
import static com.swirlds.platform.event.preconsensus.PreConsensusEventFile.MAXIMUM_GENERATION_PREFIX;
import static com.swirlds.platform.event.preconsensus.PreConsensusEventFile.MINIMUM_GENERATION_PREFIX;
import static com.swirlds.platform.event.preconsensus.PreConsensusEventFile.SEQUENCE_NUMBER_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.test.RandomUtils;
import com.swirlds.platform.event.preconsensus.PreConsensusEventFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("PreConsensusEventFile Tests")
class PreConsensusEventFileTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
        Files.createDirectories(testDirectory);
    }

    @AfterEach
    void afterEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    @Test
    @DisplayName("File Name Test")
    void fileNameTest() {
        final Random random = getRandomPrintSeed();

        final long sequenceNumber = random.nextLong(1000);
        final long minimumGeneration = random.nextLong(1000);
        final long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + 1000);
        final Instant timestamp = RandomUtils.randomInstant(random);
        final boolean discontinuity = random.nextBoolean();

        final String expectedName =
                timestamp.toString().replace(":", "+") + EVENT_FILE_SEPARATOR + SEQUENCE_NUMBER_PREFIX
                        + sequenceNumber + EVENT_FILE_SEPARATOR + MINIMUM_GENERATION_PREFIX
                        + minimumGeneration + EVENT_FILE_SEPARATOR + MAXIMUM_GENERATION_PREFIX
                        + maximumGeneration + ".pces" + (discontinuity ? "D" : "");

        Assertions.assertEquals(
                expectedName,
                PreConsensusEventFile.of(
                                sequenceNumber,
                                minimumGeneration,
                                maximumGeneration,
                                timestamp,
                                Path.of("foo/bar"),
                                discontinuity)
                        .getFileName());
    }

    @Test
    @DisplayName("File Path Test")
    void filePathTest() {
        final Random random = getRandomPrintSeed();

        final long sequenceNumber = random.nextLong(1000);
        final long minimumGeneration = random.nextLong(1000);
        final long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + 1000);
        final Instant timestamp = RandomUtils.randomInstant(random);

        final ZonedDateTime zonedDateTime = timestamp.atZone(ZoneId.systemDefault());
        final int year = zonedDateTime.getYear();
        final int month = zonedDateTime.getMonthValue();
        final int day = zonedDateTime.getDayOfMonth();

        final Path expectedPath = Path.of(
                "foo/bar", String.format("%04d", year), String.format("%02d", month), String.format("%02d", day));

        assertEquals(
                expectedPath,
                PreConsensusEventFile.of(
                                sequenceNumber,
                                minimumGeneration,
                                maximumGeneration,
                                timestamp,
                                Path.of("foo/bar"),
                                random.nextBoolean())
                        .getPath()
                        .getParent());
    }

    @Test
    @DisplayName("Parsing Test")
    void parsingTest() throws IOException {
        final Random random = getRandomPrintSeed(3153206032301550583L);

        final long sequenceNumber = random.nextLong(1000);
        final long minimumGeneration = random.nextLong(1000);
        final long maximumGeneration = random.nextLong(minimumGeneration, minimumGeneration + 1000);
        final Instant timestamp = RandomUtils.randomInstant(random);
        final boolean discontinuity = random.nextBoolean();

        final Path directory = Path.of("foo/bar/baz");

        final PreConsensusEventFile expected = PreConsensusEventFile.of(
                sequenceNumber, minimumGeneration, maximumGeneration, timestamp, directory, discontinuity);

        final PreConsensusEventFile parsed = PreConsensusEventFile.of(expected.getPath());

        assertEquals(expected, parsed);
        assertEquals(sequenceNumber, parsed.getSequenceNumber());
        assertEquals(minimumGeneration, parsed.getMinimumGeneration());
        assertEquals(maximumGeneration, parsed.getMaximumGeneration());
        assertEquals(timestamp, parsed.getTimestamp());
        assertEquals(discontinuity, parsed.marksDiscontinuity());
    }

    @SuppressWarnings("resource")
    @Test
    @DisplayName("Deletion Test")
    void deletionTest() throws IOException {
        final Random random = getRandomPrintSeed();
        final Instant now = Instant.now();

        // When we start out, the test directory should be empty.
        assertEquals(0, Files.list(testDirectory).count());

        final List<Instant> times = new ArrayList<>();
        times.add(now);
        times.add(now.plus(Duration.ofMinutes(1)));
        times.add(now.plus(Duration.ofMinutes(2)));
        times.add(now.plus(Duration.ofMinutes(3)));
        times.add(now.plus(Duration.ofMinutes(4)));
        times.add(now.plus(Duration.ofMinutes(5)));
        times.add(now.plus(Duration.ofHours(1)));
        times.add(now.plus(Duration.ofDays(1)));
        times.add(now.plus(Duration.ofDays(40)));
        times.add(now.plus(Duration.ofDays(400)));

        final List<PreConsensusEventFile> files = new ArrayList<>();
        for (int index = 0; index < times.size(); index++) {
            final Instant timestamp = times.get(index);
            // We don't care about generations for this test
            final PreConsensusEventFile file =
                    PreConsensusEventFile.of(index, 0, 0, timestamp, testDirectory, random.nextBoolean());

            writeRandomBytes(random, file.getPath(), 100);
            files.add(file);
        }

        // Delete the files in a random order.
        Collections.shuffle(files, random);
        final Set<PreConsensusEventFile> deletedFiles = new HashSet<>();
        for (final PreConsensusEventFile file : files) {

            for (final PreConsensusEventFile fileToCheck : files) {
                if (deletedFiles.contains(fileToCheck)) {
                    assertFalse(Files.exists(fileToCheck.getPath()));
                } else {
                    assertTrue(Files.exists(fileToCheck.getPath()));
                }
            }

            file.deleteFile(testDirectory);
            deletedFiles.add(file);
        }

        // After all files have been deleted, the test directory should be empty again.
        assertEquals(0, Files.list(testDirectory).count());
    }

    @Test
    @DisplayName("compareTo() Test")
    void compareToTest() {
        final Random random = getRandomPrintSeed();

        final Path directory = Path.of("foo/bar/baz");

        for (int i = 0; i < 1000; i++) {
            final long sequenceA = random.nextLong(100);
            final long sequenceB = random.nextLong(100);

            final long minimumGenerationA = random.nextLong(100);
            final long minimumGenerationB = random.nextLong(100);

            final long maximumGenerationA = random.nextLong(minimumGenerationA, minimumGenerationA + 100);
            final long maximumGenerationB = random.nextLong(minimumGenerationB, minimumGenerationB + 100);

            final PreConsensusEventFile a = PreConsensusEventFile.of(
                    sequenceA,
                    minimumGenerationA,
                    maximumGenerationA,
                    randomInstant(random),
                    directory,
                    random.nextBoolean());
            final PreConsensusEventFile b = PreConsensusEventFile.of(
                    sequenceB,
                    minimumGenerationB,
                    maximumGenerationB,
                    randomInstant(random),
                    directory,
                    random.nextBoolean());

            assertEquals(Long.compare(sequenceA, sequenceB), a.compareTo(b));
        }
    }

    @Test
    @DisplayName("canContain() Test")
    void canContainTest() {
        final Random random = getRandomPrintSeed();

        final Path directory = Path.of("foo/bar/baz");

        for (int i = 0; i < 1000; i++) {
            final long sequenceNumber = random.nextLong(1000);
            final long minimumGeneration = random.nextLong(1000);
            final long maximumGeneration = random.nextLong(minimumGeneration + 1, minimumGeneration + 1000);
            final Instant timestamp = RandomUtils.randomInstant(random);

            final PreConsensusEventFile file = PreConsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, directory, false);

            // An event with a sequence number that is too small
            assertFalse(file.canContain(minimumGeneration - random.nextLong(1, 100)));

            // An event with a sequence number matching the minimum exactly
            assertTrue(file.canContain(minimumGeneration));

            // An event with a sequence somewhere between the minimum and maximum
            assertTrue(file.canContain(maximumGeneration));

            // An event with a sequence somewhere exactly matching the maximum
            assertTrue(file.canContain(maximumGeneration));

            // An event with a sequence number that is too big
            assertFalse(file.canContain(maximumGeneration + random.nextLong(1, 100)));
        }
    }

    @Test
    @DisplayName("Discontinuity canContain() Test")
    void discontinuityCanContainTest() {
        final Random random = getRandomPrintSeed();

        final Path directory = Path.of("foo/bar/baz");

        for (int i = 0; i < 1000; i++) {
            final long sequenceNumber = random.nextLong(1000);
            final long minimumGeneration = random.nextLong(1000);
            final long maximumGeneration = random.nextLong(minimumGeneration + 1, minimumGeneration + 1000);
            final Instant timestamp = RandomUtils.randomInstant(random);

            final PreConsensusEventFile file = PreConsensusEventFile.of(
                    sequenceNumber, minimumGeneration, maximumGeneration, timestamp, directory, true);

            // An event with a sequence number that is too small
            assertFalse(file.canContain(minimumGeneration - random.nextLong(1, 100)));

            // An event with a sequence number matching the minimum exactly
            assertFalse(file.canContain(minimumGeneration));

            // An event with a sequence somewhere between the minimum and maximum
            assertFalse(file.canContain(maximumGeneration));

            // An event with a sequence somewhere exactly matching the maximum
            assertFalse(file.canContain(maximumGeneration));

            // An event with a sequence number that is too big
            assertFalse(file.canContain(maximumGeneration + random.nextLong(1, 100)));
        }
    }
}
