package com.hedera.services.utils.forensics;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.utils.forensics.RecordParsers.parseV6RecordStreamEntriesIn;

public class OrderedComparison {
    private OrderedComparison() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static List<DifferingEntries> findDifferencesBetweenV6(
            final String firstStreamDir,
            final String secondStreamDir
    ) throws IOException {
        final var firstEntries = parseV6RecordStreamEntriesIn(firstStreamDir);
        final var secondEntries = parseV6RecordStreamEntriesIn(secondStreamDir);
        final List<DifferingEntries> diffs = new ArrayList<>();
        assert firstEntries.size() == secondEntries.size();
        for (int i = 0, n = firstEntries.size(); i < n; i++) {
            final var firstEntry = firstEntries.get(i);
            final var secondEntry = secondEntries.get(i);
            assert firstEntry.consensusTime().equals(secondEntry.consensusTime());
            assert firstEntry.accessor().getSignedTxnWrapper().equals(secondEntry.accessor().getSignedTxnWrapper());
            if (!firstEntry.txnRecord().equals(secondEntry.txnRecord())) {
                diffs.add(new DifferingEntries(firstEntry, secondEntry));
            }
        }
        return diffs;
    }

    public static Map<HederaFunctionality, Map<ResponseCodeEnum, Integer>> statusHistograms(
            final List<RecordStreamEntry> entries
    ) {
        final Map<HederaFunctionality, Map<ResponseCodeEnum, Integer>> counts =
                new EnumMap<>(HederaFunctionality.class);
        for (final var entry : entries) {
            final var accessor = entry.accessor();
            final var function = accessor.getFunction();
            counts.computeIfAbsent(function, ignore -> new EnumMap<>(ResponseCodeEnum.class))
                    .merge(entry.finalStatus(), 1, Integer::sum);
        }
        return counts;
    }
}
