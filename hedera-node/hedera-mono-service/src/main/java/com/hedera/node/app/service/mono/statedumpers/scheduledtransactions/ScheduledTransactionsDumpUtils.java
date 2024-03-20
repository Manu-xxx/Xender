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

package com.hedera.node.app.service.mono.statedumpers.scheduledtransactions;

import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.utils.FieldBuilder;
import com.hedera.node.app.service.mono.statedumpers.utils.ThingsToStrings;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.swirlds.base.utility.Pair;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ScheduledTransactionsDumpUtils {
    public static void dumpMonoScheduledTransactions(
            @NonNull final Path path,
            @NonNull final MerkleMapLike<EntityNumVirtualKey, ScheduleVirtualValue> scheduledTransactions,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            final var dumpableScheduledTransactions = gatherMonoScheduledTransactions(scheduledTransactions);
            reportOnScheduledTransactions(writer, dumpableScheduledTransactions);
            System.out.printf(
                    "=== mono scheduled transactions report is %d bytes at checkpoint %s%n",
                    writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    private static Map<ScheduledTransactionId, ScheduledTransaction> gatherMonoScheduledTransactions(
            MerkleMapLike<EntityNumVirtualKey, ScheduleVirtualValue> source) {
        final var r = new HashMap<ScheduledTransactionId, ScheduledTransaction>();
        source.forEach((k, v) -> r.put(ScheduledTransactionId.fromMono(k), ScheduledTransaction.fromMono(v)));
        return r;
    }

    private static void reportOnScheduledTransactions(
            @NonNull final Writer writer,
            @NonNull final Map<ScheduledTransactionId, ScheduledTransaction> scheduledTransactions) {
        writer.writeln(formatHeader());
        scheduledTransactions.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> formatScheduledTransaction(writer, e.getValue()));
        writer.writeln("");
    }

    @NonNull
    private static String formatHeader() {
        return fieldFormatters.stream().map(Pair::left).collect(Collectors.joining(FIELD_SEPARATOR));
    }

    static final String FIELD_SEPARATOR = ";";
    static final String SUBFIELD_SEPARATOR = ",";
    static Function<Boolean, String> booleanFormatter = b -> b ? "T" : "";
    static Function<Object, String> csvQuote =
            s -> ThingsToStrings.quoteForCsv(FIELD_SEPARATOR, (s == null) ? "" : s.toString());

    static <T> Function<Optional<T>, String> getOptionalFormatter(@NonNull final Function<T, String> formatter) {
        return ot -> ot.isPresent() ? formatter.apply(ot.get()) : "";
    }

    static <T> Function<T, String> getNullableFormatter(@NonNull final Function<T, String> formatter) {
        return t -> null != t ? formatter.apply(t) : "";
    }

    static <T> Function<List<T>, String> getListFormatter(
            @NonNull final Function<T, String> formatter, @NonNull final String subfieldSeparator) {
        return lt -> {
            if (!lt.isEmpty()) {
                final var sb = new StringBuilder();
                for (@NonNull final var e : lt) {
                    final var v = formatter.apply(e);
                    sb.append(v);
                    sb.append(subfieldSeparator);
                }
                // Remove last subfield separator
                if (sb.length() >= subfieldSeparator.length()) sb.setLength(sb.length() - subfieldSeparator.length());
                return sb.toString();
            } else return "";
        };
    }

    // spotless:off
    @NonNull
    private static final List<Pair<String, BiConsumer<FieldBuilder, ScheduledTransaction>>> fieldFormatters = List.of(
            Pair.of("number", getFieldFormatter(ScheduledTransaction::number, Object::toString)),
            Pair.of(
                    "adminKey",
                    getFieldFormatter(
                            ScheduledTransaction::adminKey, getOptionalFormatter(ThingsToStrings::toStringOfJKey))),
            Pair.of("memo", getFieldFormatter(ScheduledTransaction::memo, csvQuote)),
            Pair.of("isDeleted", getFieldFormatter(ScheduledTransaction::deleted, booleanFormatter)),
            Pair.of("isExecuted", getFieldFormatter(ScheduledTransaction::executed, booleanFormatter)),
            Pair.of(
                    "calculatedWaitForExpiry",
                    getFieldFormatter(ScheduledTransaction::calculatedWaitForExpiry, booleanFormatter)),
            Pair.of(
                    "waitForExpiryProvided",
                    getFieldFormatter(ScheduledTransaction::waitForExpiryProvided, booleanFormatter)),
            Pair.of("payer", getFieldFormatter(ScheduledTransaction::payer, ThingsToStrings::toStringOfEntityId)),
            Pair.of(
                    "schedulingAccount",
                    getFieldFormatter(ScheduledTransaction::schedulingAccount, ThingsToStrings::toStringOfEntityId)),
            Pair.of(
                    "schedulingTXValidStart",
                    getFieldFormatter(
                            ScheduledTransaction::schedulingTXValidStart, ThingsToStrings::toStringOfRichInstant)),
            Pair.of(
                    "expirationTimeProvided",
                    getFieldFormatter(
                            ScheduledTransaction::expirationTimeProvided,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "calculatedExpirationTime",
                    getFieldFormatter(
                            ScheduledTransaction::calculatedExpirationTime,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "resolutionTime",
                    getFieldFormatter(
                            ScheduledTransaction::resolutionTime,
                            getNullableFormatter(ThingsToStrings::toStringOfRichInstant))),
            Pair.of(
                    "bodyBytes",
                    getFieldFormatter(ScheduledTransaction::bodyBytes, ThingsToStrings::toStringOfByteArray)),
            Pair.of("ordinaryScheduledTxn", getFieldFormatter(ScheduledTransaction::ordinaryScheduledTxn, csvQuote)),
            Pair.of("scheduledTxn", getFieldFormatter(ScheduledTransaction::scheduledTxn, csvQuote)),
            Pair.of(
                    "signatories",
                    getFieldFormatter(
                            ScheduledTransaction::signatories,
                            getListFormatter(ThingsToStrings::toStringOfByteArray, SUBFIELD_SEPARATOR))));
    // spotless:on

    @NonNull
    static <T> BiConsumer<FieldBuilder, ScheduledTransaction> getFieldFormatter(
            @NonNull final Function<ScheduledTransaction, T> fun, @NonNull final Function<T, String> formatter) {
        return (fb, u) -> formatField(fb, u, fun, formatter);
    }

    static <T> void formatField(
            @NonNull final FieldBuilder fb,
            @NonNull final ScheduledTransaction scheduledTransaction,
            @NonNull final Function<ScheduledTransaction, T> fun,
            @NonNull final Function<T, String> formatter) {
        fb.append(formatter.apply(fun.apply(scheduledTransaction)));
    }

    private static void formatScheduledTransaction(
            @NonNull final Writer writer, @NonNull final ScheduledTransaction scheduledTransaction) {
        final var fb = new FieldBuilder(FIELD_SEPARATOR);
        fieldFormatters.stream().map(Pair::right).forEach(ff -> ff.accept(fb, scheduledTransaction));
        writer.writeln(fb);
    }
}
