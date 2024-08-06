package com.hedera.services.bdd.utils;

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.orderedRecordFilesFrom;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.parseRecordFileConsensusTime;
import static com.hedera.node.app.hapi.utils.forensics.DifferingEntries.FirstEncounteredDifference.CONSENSUS_TIME_MISMATCH;
import static com.hedera.node.app.hapi.utils.forensics.DifferingEntries.FirstEncounteredDifference.TRANSACTION_MISMATCH;
import static com.hedera.node.app.hapi.utils.forensics.DifferingEntries.FirstEncounteredDifference.TRANSACTION_RECORD_MISMATCH;
import static com.hedera.node.app.hapi.utils.forensics.OrderedComparison.findDifferencesBetweenV6;
import static com.hedera.node.app.hapi.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotModeOp.exactMatch;

import com.hedera.node.app.hapi.utils.forensics.DifferingEntries;
import com.hedera.node.app.hapi.utils.forensics.OrderedComparison;
import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class RcDiff implements Callable<Integer> {
    private static final OrderedComparison.RecordDiffSummarizer DEFAULT_SUMMARIZER = (a, b) -> {
        try {
            exactMatch(a, b, () -> "");
        } catch (Throwable t) {
            return t.getMessage();
        }
        throw new AssertionError("No difference to summarize");
    };

    private final Long maxDiffsToExport;

    private final Long lenOfDiffSecs;

    private final List<RecordStreamEntry> expectedStreams;
    private final List<RecordStreamEntry> actualStreams;

    private final String diffsLoc;

    private final BoundaryTimes expectedBoundaries;
    private final BoundaryTimes actualBoundaries;

    private PrintStream out = System.out;

    public RcDiff(final long maxDiffsToExport, final long lenOfDiffSecs, @NonNull final List<RecordStreamEntry> expectedStreams, @NonNull final List<RecordStreamEntry> actualStreams, @Nullable final String diffsLoc, @Nullable final PrintStream out) {
        this(maxDiffsToExport, lenOfDiffSecs, expectedStreams, actualStreams, diffsLoc, boundaryTimesFor(expectedStreams), boundaryTimesFor(actualStreams), out);
    }

    public RcDiff(final long maxDiffsToExport, final long lenOfDiffSecs,
            @NonNull final String expectedStreamsLoc, @NonNull final String actualStreamsLoc,
            @Nullable final String diffsLoc, @Nullable final PrintStream out) throws IOException {
        this(maxDiffsToExport, lenOfDiffSecs, parseV6RecordStreamEntriesIn(expectedStreamsLoc), parseV6RecordStreamEntriesIn(actualStreamsLoc), diffsLoc, boundaryTimesFor(expectedStreamsLoc), boundaryTimesFor(actualStreamsLoc), out);
    }

    public RcDiff(final long maxDiffsToExport, final long lenOfDiffSecs,
            @NonNull final String expectedStreamsLoc, @NonNull final String actualStreamsLoc,
            @NonNull final String diffsLoc) throws IOException {
        this(maxDiffsToExport, lenOfDiffSecs, expectedStreamsLoc, actualStreamsLoc, diffsLoc, null);
    }

    private RcDiff(final long maxDiffsToExport, final long lenOfDiffSecs,
            @NonNull final List<RecordStreamEntry> expectedStreams, @NonNull final List<RecordStreamEntry> actualStreams,
            @Nullable final String diffsLoc, @NonNull final BoundaryTimes expectedBoundaries, @NonNull final BoundaryTimes actualBoundaries, @Nullable final PrintStream out) {
        this.maxDiffsToExport = maxDiffsToExport;
        this.lenOfDiffSecs = lenOfDiffSecs;
        this.expectedStreams = expectedStreams;
        this.actualStreams = actualStreams;
        this.diffsLoc = diffsLoc;
        this.expectedBoundaries = expectedBoundaries;
        this.actualBoundaries = actualBoundaries;
        if (out != null) {
            this.out = out;
        }

        throwOnInvalidInput();
    }

    /**
     * todo
     * @return
     */
    public List<DifferingEntries> summarizeDiffs() {
        return diffsGiven(DEFAULT_SUMMARIZER);
    }

    /**
     * todo
     * @param diffs
     * @return
     */
    public List<String> buildDiffOutput(@NonNull final List<DifferingEntries> diffs) {
        return diffs.stream()
                .map(this::readableDiff)
                .limit(maxDiffsToExport)
                .toList();
    }

    /**
     * todo
     * @return
     * @throws Exception
     */
    @Override
    public Integer call() throws Exception {
        final var diffs = summarizeDiffs();
        if (diffs.isEmpty()) {
            out.println("These streams are identical ☺️");
            return 0;
        } else {
            out.println("These streams differed " + diffs.size() + " times 😞");
            dumpToFile(diffs);
            return 1;
        }
    }

    private List<DifferingEntries> diffsGiven(
            @NonNull final OrderedComparison.RecordDiffSummarizer recordDiffSummarizer) {
        final var first = Collections.min(List.of(actualBoundaries.first, expectedBoundaries.first));
        final var last = Collections.max(List.of(actualBoundaries.last, expectedBoundaries.last));
        final List<DifferingEntries> diffs = new ArrayList<>();
        for (Instant i = first; !i.isAfter(last); i = i.plusSeconds(lenOfDiffSecs)) {
            final var start = i;
            final var end = i.plusSeconds(lenOfDiffSecs);
            // Include files in the range [start, end)
            final Predicate<RecordStreamEntry> inclusionTest = e -> {
                final var consensusTime = e.consensusTime();
                return !consensusTime.isBefore(start) && consensusTime.isBefore(end);
            };
            final var diffsHere = findDifferencesBetweenV6(
                    expectedStreams,
                    actualStreams,
                    recordDiffSummarizer,
                    inclusionTest);

            out.println(" ➡️ Found " + diffsHere.size() + " diffs from " + start + " to " + end);
            diffs.addAll(diffsHere);
        }
        return diffs;
    }

    private record BoundaryTimes(Instant first, Instant last) {}

    private static BoundaryTimes boundaryTimesFor(@NonNull final List<RecordStreamEntry> entries) {
        if (entries.isEmpty()) {
            return new BoundaryTimes(Instant.MAX, Instant.EPOCH);
        }
        return new BoundaryTimes(
                entries.getFirst().consensusTime(),
                entries.getLast().consensusTime());
    }

    private static BoundaryTimes boundaryTimesFor(@NonNull final String loc) {
        try {
            final var orderedFiles = orderedRecordFilesFrom(loc, f -> true);
            if (orderedFiles.isEmpty()) {
                return new BoundaryTimes(Instant.MAX, Instant.EPOCH);
            }
            return new BoundaryTimes(
                    parseRecordFileConsensusTime(orderedFiles.getFirst()),
                    parseRecordFileConsensusTime(orderedFiles.getLast()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void throwOnInvalidInput() {
        if (actualStreams == null) {
            throw new IllegalArgumentException("Please specify a non-empty actual stream");
        }
        if (expectedStreams == null) {
            throw new IllegalArgumentException("Please specify a non-empty expected stream");
        }
        if (lenOfDiffSecs <= 0) {
            throw new IllegalArgumentException(
                    "Please specify a positive length of diff in seconds");
        }
    }

    private void dumpToFile(@NonNull final List<DifferingEntries> diffs) {
        try {
            Files.write(
                    Paths.get(diffsLoc),
                    buildDiffOutput(diffs));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String readableDiff(@NonNull final DifferingEntries diff) {
        final var firstEncounteredDifference = diff.firstEncounteredDifference();
        final var sb = new StringBuilder()
                .append("---- ")
                .append(diff.involvedFunctions())
                .append(" DIFFERED (")
                .append(firstEncounteredDifference)
                .append(") ----\n");
        if (diff.summary() != null) {
            sb.append(diff.summary()).append("\n");
        }
        if (firstEncounteredDifference == CONSENSUS_TIME_MISMATCH) {
            sb.append("➡️  Expected ")
                    .append(Objects.requireNonNull(diff.firstEntry()).consensusTime())
                    .append("\n\n")
                    .append("➡️ but was ")
                    .append(Objects.requireNonNull(diff.secondEntry()).consensusTime());
        } else if (firstEncounteredDifference == TRANSACTION_RECORD_MISMATCH
                || firstEncounteredDifference == TRANSACTION_MISMATCH) {
            sb.append("\nFor body,\n")
                    .append(Objects.requireNonNull(diff.firstEntry()).body());
            sb.append("➡️  Expected Record ")
                    .append(Objects.requireNonNull(diff.firstEntry()).transactionRecord())
                    .append(" but was ")
                    .append(Objects.requireNonNull(diff.secondEntry()).transactionRecord());
        }
        return sb.toString();
    }
}
