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

package com.swirlds.logging.api.internal.format;

import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.NANO_OF_SECOND;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An epoc millis parser to human-readable String based on pattern: {@code "yyyy-MM-dd HH:mm:ss.SSS"}
 */
public class EpochCachedFormatter {

    /**
     * The formatter for the timestamp.
     */
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(YEAR, 4, 4, SignStyle.EXCEEDS_PAD)
            .appendLiteral('-')
            .appendValue(MONTH_OF_YEAR, 2)
            .appendLiteral('-')
            .appendValue(DAY_OF_MONTH, 2)
            .appendLiteral(' ')
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(SECOND_OF_MINUTE, 2)
            .optionalStart()
            .appendFraction(NANO_OF_SECOND, 3, 3, true)
            .toFormatter()
            .withZone(UTC);

    private final Map<Long, String> exactCache = new LimitedSizeCache<>(LimitedSizeCache.MAX_ENTRIES);
    private final Map<Instant, String> dateCache = new LimitedSizeCache<>(10);
    private final Map<Instant, String> dateHourCache = new LimitedSizeCache<>(10 * 24);
    private final Map<Instant, String> dateHourMinutesCache = new LimitedSizeCache<>(10 * 1440);

    /**
     * Creates a parser and preloads the caches with {@link System#currentTimeMillis()}
     */
    public EpochCachedFormatter() {
        // precompute values for now
        format(System.currentTimeMillis());
    }

    /**
     * Parses the {@code epochMillis} into a String. It uses caches to speed up future so subsequents calls within the
     * day/hour/millisecond are faster. For non cached times it introduces a time-penalization compared to
     * {@link DateTimeFormatter#format(TemporalAccessor)} for updating caches. To minimize this effect at instantiation,
     * it preloads the information for the current time.
     *
     * @param epochMillis epoch millis to convert such as those obtained form {@link System#currentTimeMillis()}
     * @return the human-readable representation of the string based on  {@link DateTimeFormatter#ISO_LOCAL_DATE_TIME}
     */
    public @NonNull String format(final long epochMillis) {

        String stringDate;
        if ((stringDate = exactCache.get(epochMillis)) != null) {
            return stringDate;
        }
        Instant instant = Instant.ofEpochMilli(epochMillis);
        if ((stringDate = getFromMinutes(instant)) != null) {
            exactCache.put(instant.toEpochMilli(), stringDate);
            return stringDate;
        }
        if ((stringDate = getFromHours(instant)) != null) {
            exactCache.put(instant.toEpochMilli(), stringDate);
            return stringDate;
        }
        if ((stringDate = getFromDate(instant)) != null) {
            exactCache.put(instant.toEpochMilli(), stringDate);
            return stringDate;
        }

        stringDate = FORMATTER.format(instant);
        exactCache.put(instant.toEpochMilli(), stringDate);
        dateCache.put(instant.truncatedTo(ChronoUnit.DAYS), stringDate.substring(0, 10));
        dateHourCache.put(instant.truncatedTo(ChronoUnit.HOURS), stringDate.substring(0, 14));
        dateHourMinutesCache.put(instant.truncatedTo(ChronoUnit.MINUTES), stringDate.substring(0, 17));
        return stringDate;
    }

    private @Nullable String getFromMinutes(final @NonNull Instant instant) {
        final String format = dateHourMinutesCache.get(instant.truncatedTo(ChronoUnit.MINUTES));
        if (format == null) {
            return null;
        }
        return format + stringFrom(instant, ChronoUnit.SECONDS);
    }

    private @Nullable String getFromHours(final @NonNull Instant instant) {
        final String format = dateHourCache.get(instant.truncatedTo(ChronoUnit.HOURS));
        if (format == null) {
            return null;
        }
        return format + stringFrom(instant, ChronoUnit.MINUTES);
    }

    private @Nullable String getFromDate(final @NonNull Instant instant) {
        final String format = dateCache.get(instant.truncatedTo(ChronoUnit.DAYS));

        if (format == null) {
            return null;
        }

        return format + stringFrom(instant, ChronoUnit.HOURS);
    }

    /**
     * Constructs a string representation of the given {@link Instant} starting from the specified {@link ChronoUnit}.
     * <p>
     *
     * The method constructs a string representation of the {@link Instant} from the specified {@link ChronoUnit}, including
     * hours, minutes, seconds, and milliseconds.
     * <p>
     * e.g:
     * Given an {@code instant} representing date: {@code "2020-08-26 12:34:56.789"}
     * <ul>
     * <li>{@code stringFrom(instant, ChronoUnit.MICROS)} --> adds to the buffer {@code ""} </li>
     * <li>{@code stringFrom(instant, ChronoUnit.NANOS)} --> adds to the buffer {@code ""} </li>
     * <li>{@code stringFrom(instant, ChronoUnit.MILLIS)} --> adds to the buffer {@code "789"} </li>
     * <li>{@code stringFrom(instant, ChronoUnit.SECONDS)} --> adds to the buffer {@code "56.789"} </li>
     * <li>{@code stringFrom(instant, ChronoUnit.MINUTES)} --> adds to the buffer {@code "34:56.789"} </li>
     * <li>{@code stringFrom(instant, ChronoUnit.HOURS)} --> adds to the buffer {@code " 12:34:56.789"} </li>
     * <li>{@code stringFrom(instant, ChronoUnit.*)} --> adds to the buffer {@code " 12:34:56.789"} </li>
     * </ul>
     *
     * @param instant The Instant to represent as a string.
     * @param unit    The {@link ChronoUnit} from which the string representation should be formatted.
     * @return A {@link StringBuilder} containing the string representation of the Instant up to the specified
     * {@link ChronoUnit}.
     */
    private static @NonNull StringBuilder stringFrom(final @NonNull Instant instant, final @NonNull ChronoUnit unit) {

        final StringBuilder stringBuilder = new StringBuilder();
        if (unit.ordinal() >= ChronoUnit.MILLIS.ordinal()) {
            final int milliseconds = instant.getNano() / 1_000_000;
            appendDigitsReverse(milliseconds, stringBuilder, 3);
        }
        long totalSeconds = instant.getEpochSecond();
        if (unit.ordinal() >= ChronoUnit.SECONDS.ordinal()) {
            stringBuilder.append(".");
            final int second = (int) (totalSeconds % 60);
            appendDigitsReverse(second, stringBuilder, 2);
        }
        if (unit.ordinal() >= ChronoUnit.MINUTES.ordinal()) {
            final int minute = (int) ((totalSeconds / 60) % 60);
            stringBuilder.append(":");
            appendDigitsReverse(minute, stringBuilder, 2);
        }
        if (unit.ordinal() >= ChronoUnit.HOURS.ordinal()) {
            final int hour = (int) ((totalSeconds / 3600) % 24);
            stringBuilder.append(":");
            appendDigitsReverse(hour, stringBuilder, 2);
            stringBuilder.append(" ");
        }

        return stringBuilder.reverse();
    }

    /**
     * Appends the digits of the number into the buffer in reverse order and pads to the right with 0. Examples:
     * <ul>
     * <li>{@code appendDigitsReverse(1, buffer, 1)} --> 1</li>
     * <li>{@code appendDigitsReverse(1, buffer, 2)} --> 10</li>
     * <li>{@code appendDigitsReverse(12, buffer, 1)} --> 2</li>
     * <li>{@code appendDigitsReverse(12, buffer, 2)} --> 21</li>
     * <li>{@code appendDigitsReverse(12, buffer, 3)} --> 210</li>
     * <li>{@code appendDigitsReverse(123, buffer, 3)} --> 321</li>
     * <li>{@code appendDigitsReverse(758, buffer, 4)} --> 8570</li>
     * </ul>
     *
     * @param number        The number to append in reverse order.
     * @param buffer        The buffer to append to.
     * @param desiredLength The maximum length of the number to append.
     */
    private static void appendDigitsReverse(
            final int number, final @NonNull StringBuilder buffer, final int desiredLength) {
        int actualLength = 0;
        int num = number;
        while ((num > 0) && actualLength < desiredLength) {
            int digit = num % 10;
            buffer.append(digit);
            num /= 10;
            actualLength++;
        }
        while (desiredLength > actualLength) {
            buffer.append(0);
            actualLength++;
        }
    }

    /**
     * A {@link LinkedHashMap} with size as removal policy.
     *
     * @param <K> the type for key
     * @param <V> the type for value
     */
    private static class LimitedSizeCache<K, V> extends LinkedHashMap<K, V> {
        private static final int MAX_ENTRIES = 10000;

        public LimitedSizeCache(int entries) {
            super(entries + 1, 1.0f, true);
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > MAX_ENTRIES;
        }
    }
}
