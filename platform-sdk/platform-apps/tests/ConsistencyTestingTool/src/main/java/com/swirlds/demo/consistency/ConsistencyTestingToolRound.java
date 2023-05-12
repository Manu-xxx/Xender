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

package com.swirlds.demo.consistency;

import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;

import com.swirlds.common.system.Round;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a round in the ConsistencyTestingTool
 *
 * @param roundNumber          The number of the round
 * @param currentState         The app state after handling the round
 * @param transactionsContents A list of transactions which were included in the round
 */
public record ConsistencyTestingToolRound(long roundNumber, long currentState, @NonNull List<Long> transactionsContents)
        implements Comparable<ConsistencyTestingToolRound> {

    private static final String ROUND_NUMBER_STRING = "Round Number: ";
    private static final String CURRENT_STATE_STRING = "Current State: ";
    private static final String TRANSACTIONS_STRING = "Transactions: ";
    private static final String FIELD_SEPARATOR = "; ";
    private static final String LIST_ELEMENT_SEPARATOR = ", ";

    /**
     * Construct a {@link ConsistencyTestingToolRound} from a {@link Round}
     *
     * @param round        the round to convert
     * @param currentState the long state value of the application after the round has been applied
     * @return the input round, converted to a {@link ConsistencyTestingToolRound}
     */
    @NonNull
    public static ConsistencyTestingToolRound fromRound(final @NonNull Round round, final long currentState) {
        Objects.requireNonNull(round);

        final List<Long> transactionContents = new ArrayList<>();

        round.forEachTransaction(transaction -> transactionContents.add(byteArrayToLong(transaction.getContents(), 0)));

        return new ConsistencyTestingToolRound(round.getRoundNum(), currentState, transactionContents);
    }

    /**
     * Construct a {@link ConsistencyTestingToolRound} from a string representation
     *
     * @param roundString the string representation of the round
     * @return the new {@link ConsistencyTestingToolRound}, or null if parsing failed
     */
    @Nullable
    public static ConsistencyTestingToolRound fromString(final @NonNull String roundString) {
        Objects.requireNonNull(roundString);

        try {
            final List<String> fields =
                    Arrays.stream(roundString.split(FIELD_SEPARATOR)).toList();

            String field = fields.get(0);
            final long roundNumber = Long.parseLong(field.substring(ROUND_NUMBER_STRING.length()));

            field = fields.get(1);
            final long currentState = Long.parseLong(field.substring(CURRENT_STATE_STRING.length()));

            field = fields.get(2);
            final String transactionsString = field.substring(field.indexOf("[") + 1, field.indexOf("]"));
            final List<Long> transactionsContents = Arrays.stream(transactionsString.split(LIST_ELEMENT_SEPARATOR))
                    .map(Long::parseLong)
                    .toList();

            return new ConsistencyTestingToolRound(roundNumber, currentState, transactionsContents);
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(@NonNull ConsistencyTestingToolRound other) {
        Objects.requireNonNull(other);

        return Long.compare(this.roundNumber, other.roundNumber);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final @Nullable Object other) {
        if (other == null) {
            return false;
        }

        if (other == this) {
            return true;
        }

        if (other instanceof final ConsistencyTestingToolRound otherRound) {
            return roundNumber == otherRound.roundNumber
                    && currentState == otherRound.currentState
                    && transactionsContents.equals(otherRound.transactionsContents);
        }

        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(roundNumber, currentState, transactionsContents);
    }

    /**
     * Produces a string representation of the object that can be parsed by {@link #fromString}.
     * <p>
     * Take care if modifying this method to mirror the change in {@link #fromString}
     *
     * @return a string representation of the object
     */
    @Override
    @NonNull
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append(ROUND_NUMBER_STRING);
        builder.append(roundNumber);
        builder.append(FIELD_SEPARATOR);

        builder.append(CURRENT_STATE_STRING);
        builder.append(currentState);
        builder.append(FIELD_SEPARATOR);

        builder.append(TRANSACTIONS_STRING);
        builder.append("[");
        transactionsContents.forEach(transaction -> {
            builder.append(transaction);
            builder.append(LIST_ELEMENT_SEPARATOR);
        });
        builder.delete(builder.length() - LIST_ELEMENT_SEPARATOR.length(), builder.length());
        builder.append("]\n");

        return builder.toString();
    }
}
