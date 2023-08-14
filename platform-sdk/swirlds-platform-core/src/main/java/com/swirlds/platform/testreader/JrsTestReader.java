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

package com.swirlds.platform.testreader;

import static com.swirlds.common.formatting.HorizontalAlignment.ALIGNED_RIGHT;
import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static com.swirlds.platform.testreader.JrsTestReportGenerator.generateReport;
import static com.swirlds.platform.testreader.TestStatus.FAIL;
import static com.swirlds.platform.testreader.TestStatus.PASS;
import static com.swirlds.platform.testreader.TestStatus.UNKNOWN;

import com.swirlds.common.utility.CompareTo;
import com.swirlds.platform.util.CommandResult;
import com.swirlds.platform.util.VirtualTerminal;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Utilities for reading JRS test results and creating a report.
 */
public final class JrsTestReader {

    private JrsTestReader() {}

    /**
     * Test directories contain a timestamp in the format "20230630-053633-ignoredArbitraryString". Attempt to parse the
     * timestamp from this string, and return it if found.
     *
     * @return the parsed timestamp, or null if no timestamp could be parsed.
     */
    @Nullable
    public static Instant parseTimestampFromDirectory(@NonNull final String timestampString) {
        final String[] parts = timestampString.split("-");

        if (parts.length < 2 || parts[0].length() != 8 || parts[1].length() != 6) {
            return null;
        }

        try {
            final int year = Integer.parseInt(parts[0].substring(0, 4));
            final int month = Integer.parseInt(parts[0].substring(4, 6));
            final int day = Integer.parseInt(parts[0].substring(6, 8));

            final int hour = Integer.parseInt(parts[1].substring(0, 2));
            final int minute = Integer.parseInt(parts[1].substring(2, 4));
            final int second = Integer.parseInt(parts[1].substring(4, 6));

            // A bit of a hack, but there isn't a clean API to convert date info to an instant.

            final String instantString = year + "-" + ALIGNED_RIGHT.pad(Integer.toString(month), '0', 2)
                    + "-" + ALIGNED_RIGHT.pad(Integer.toString(day), '0', 2)
                    + "T" + ALIGNED_RIGHT.pad(Integer.toString(hour), '0', 2)
                    + ":" + ALIGNED_RIGHT.pad(Integer.toString(minute), '0', 2)
                    + ":" + ALIGNED_RIGHT.pad(Integer.toString(second), '0', 2)
                    + "Z";

            return Instant.parse(instantString);
        } catch (final NumberFormatException | DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Test paths contain a timestamp in the format ".../20230630-053633-ignoredArbitraryString/...". Attempt to parse
     * the timestamp from this string, and return it if found.
     *
     * @return the parsed timestamp, or null if no timestamp could be parsed.
     */
    @Nullable
    public static Instant parseTimestampFromPath(@NonNull final String pathString) {
        final String[] directories = pathString.split("/");
        for (final String directory : directories) {
            final Instant instant = parseTimestampFromDirectory(directory);
            if (instant != null) {
                return instant;
            }
        }
        return null;
    }

    /**
     * Get the contents of a remote directory.
     *
     * @param terminal  the virtual terminal
     * @param remoteDir the remote directory to look into
     * @return a list of fully qualified directories in the remote directory
     */
    @NonNull
    public static List<String> lsRemoteDir(@NonNull final VirtualTerminal terminal, @NonNull final String remoteDir) {

        final CommandResult result = terminal.run("gsutil", "ls", remoteDir);

        if (!result.isSuccessful()) {
            throw new RuntimeException("Failed to list remote directory: " + remoteDir);
        }

        final String[] directories = result.out().split("\n");
        return List.of(directories);
    }

    /**
     * Descend into a directory tree and return all nested test panel directories. Test panel directories contain a
     * timestamp (as parsed by {@link #parseTimestampFromDirectory(String)}). The subtrees beneath timestamp directories
     * are not explored by this method.
     *
     * @param terminal        the virtual terminal
     * @param executorService the executor service to use
     * @param rootDirectory   the root of the directory tree to explore
     * @param now             the current time
     * @param maximumAge      the maximum age of tests to consider
     * @return a list of directories that contain a timestamp, excluding directories with timestamps less than
     * {@code minimumTimestamp}
     */
    public static List<String> findTestPanelDirectories(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant now,
            @NonNull final Duration maximumAge) {

        terminal.getProgressIndicator().writeMessage("Searching for panel directories.");

        final Queue<String> directoriesToExplore = new LinkedBlockingQueue<>();
        directoriesToExplore.add(rootDirectory);

        final Queue<String> directoriesWithTimestamps = new LinkedBlockingQueue<>();

        while (!directoriesToExplore.isEmpty()) {

            final CountDownLatch latch = new CountDownLatch(directoriesToExplore.size());

            // Handle the next bach of work in parallel
            while (!directoriesToExplore.isEmpty()) {
                final String next = directoriesToExplore.remove();
                executorService.submit(() -> {
                    final Instant timestamp = parseTimestampFromPath(next);

                    if (timestamp == null) {
                        final List<String> subDirectories = lsRemoteDir(terminal, next);
                        directoriesToExplore.addAll(subDirectories);
                    } else {
                        final Duration age = Duration.between(timestamp, now);
                        if (CompareTo.isGreaterThan(age, maximumAge)) {
                            // Test is too old, ignore it
                            latch.countDown();
                            return;
                        }

                        directoriesWithTimestamps.add(next);
                    }
                    latch.countDown();
                });
            }

            abortAndThrowIfInterrupted(latch::await, "interrupted while waiting for directory search to complete");
        }

        terminal.getProgressIndicator()
                .writeMessage("Found " + directoriesWithTimestamps.size() + " panel directories.");

        final List<String> dirList = new ArrayList<>(directoriesWithTimestamps.size());
        dirList.addAll(directoriesWithTimestamps);
        return dirList;
    }

    /**
     * Descend into a directory tree and return all nested test directories.
     *
     * @param terminal        the virtual terminal
     * @param executorService the executor service to use
     * @param rootDirectory   the root of the directory tree to explore
     * @param now             the current time
     * @param maximumAge      the maximum age of tests to consider
     * @return a list of directories that contain a timestamp, excluding directories with timestamps less than
     * {@code minimumTimestamp}
     */
    public static List<String> findTestDirectories(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant now,
            @NonNull final Duration maximumAge) {

        final List<String> panelDirectories =
                findTestPanelDirectories(terminal, executorService, rootDirectory, now, maximumAge);

        terminal.getProgressIndicator().writeMessage("Searching for test directories.");

        final Queue<String> testDirectories = new LinkedBlockingDeque<>();
        final CountDownLatch latch = new CountDownLatch(panelDirectories.size());

        for (final String panelDirectory : panelDirectories) {
            executorService.submit(() -> {
                final List<String> panelContents = lsRemoteDir(terminal, panelDirectory);

                for (final String potentialTestDirectory : panelContents) {
                    if (potentialTestDirectory.endsWith(".log")) {
                        continue;
                    }
                    testDirectories.add(potentialTestDirectory);
                }
                latch.countDown();
            });
        }

        abortAndThrowIfInterrupted(latch::await, "interrupted while waiting for directory search to complete");

        terminal.getProgressIndicator().writeMessage("Found " + testDirectories.size() + " test directories.");

        final List<String> dirList = new ArrayList<>(testDirectories.size());
        dirList.addAll(testDirectories);

        return dirList;
    }

    /**
     * Get a list of test results.
     *
     * @param terminal        the virtual terminal
     * @param executorService the executor service to use
     * @param rootDirectory   the root of the directory tree to explore
     * @param now             the current time
     * @param maximumAge      the maximum age of tests to consider
     * @return a list of test results
     */
    @NonNull
    public static List<JrsTestResult> findTestResults(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executorService,
            @NonNull final String rootDirectory,
            @NonNull final Instant now,
            @NonNull final Duration maximumAge) {

        final List<String> testDirectories =
                findTestDirectories(terminal, executorService, rootDirectory, now, maximumAge);

        terminal.getProgressIndicator().writeMessage("Scanning tests for data.");

        final Queue<JrsTestResult> testResults = new LinkedBlockingQueue<>();

        final CountDownLatch latch = new CountDownLatch(testDirectories.size());

        for (final String testDirectory : testDirectories) {
            final Runnable task = () -> {
                final List<String> testFiles = lsRemoteDir(terminal, testDirectory);

                TestStatus status = UNKNOWN;

                for (final String testFile : testFiles) {
                    if (testFile.endsWith("test-passed")) {
                        status = PASS;
                        break;
                    } else if (testFile.endsWith("test-failed")) {
                        status = FAIL;
                        break;
                    }
                }

                final String[] parts = testDirectory.split("/");

                if (parts.length < 3) {
                    System.out.println("Invalid test directory structure");
                    latch.countDown();
                    return;
                }

                final String timestampString = parts[parts.length - 2];
                final Instant timestamp = parseTimestampFromDirectory(timestampString);
                if (timestamp == null) {
                    System.out.println("Unable to parse timestamp from string: " + testDirectory);
                    latch.countDown();
                    return;
                }

                final String testName = parts[parts.length - 1];
                final String panelName = parts[parts.length - 3];

                final JrsTestIdentifier id = new JrsTestIdentifier(panelName, testName);
                final JrsTestResult result = new JrsTestResult(id, status, timestamp, testDirectory);

                testResults.add(result);
                latch.countDown();
            };

            executorService.submit(task);
        }
        abortAndThrowIfInterrupted(latch::await, "interrupted while waiting for test search to complete");

        terminal.getProgressIndicator().writeMessage("Found results for " + testResults.size() + " tests.");

        return new ArrayList<>(testResults);
    }

    /**
     * Parse the metadata file. A notes file is a CSV (commas) with three columns: panel, test name, and a
     * URL.
     *
     * @param notesFile the path to the notes file
     * @return a map of test identifiers to note URLs
     */
    @NonNull
    public static Map<JrsTestIdentifier, JrsTestMetadata> parseMetadataFile(@Nullable final Path notesFile) {
        final Map<JrsTestIdentifier, JrsTestMetadata> metadata = new HashMap<>();
        if (notesFile == null) {
            return metadata;
        }

        try (final BufferedReader reader = Files.newBufferedReader(notesFile)) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isEmpty()) {
                    continue;
                }

                if (line.strip().startsWith("#")) {
                    // Comment
                    continue;
                }

                final String[] parts = line.split(",");
                if (parts.length != 4) {
                    System.out.println("Invalid line in notes file: " + line);
                    continue;
                }

                final String panel = parts[0].strip();
                final String testName = parts[1].strip();
                final String testOwner = parts[2].strip();
                final String url = parts[3];

                final JrsTestIdentifier id = new JrsTestIdentifier(panel, testName);
                final JrsTestMetadata previous = metadata.put(id, new JrsTestMetadata(testOwner, url));

                if (previous != null) {
                    System.out.println("Duplicate note URL found for " + id);
                }
            }
        } catch (final IOException e) {
            System.out.println("Unable to parse notes file " + notesFile);
            e.printStackTrace();
        }

        return metadata;
    }

    public static void generateTestReport(
            @NonNull final VirtualTerminal terminal,
            @NonNull final ExecutorService executor,
            @NonNull final String rootDirectory,
            @NonNull final Duration maximumAge,
            @Nullable final Path notesFile,
            @NonNull final Path outputFile) {

        final Instant now = Instant.now();

        final Map<JrsTestIdentifier, JrsTestMetadata> metadata = parseMetadataFile(notesFile);
        final List<JrsTestResult> results = findTestResults(terminal, executor, rootDirectory, now, maximumAge);

        generateReport(results, metadata, now, outputFile);
    }
}
