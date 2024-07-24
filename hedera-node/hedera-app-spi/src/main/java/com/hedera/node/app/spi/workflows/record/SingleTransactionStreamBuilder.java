/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows.record;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.USER;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Defines API for constructing stream items of a single transaction dispatch.
 * The implementation may produce only records or could produce block items
 */
public interface SingleTransactionStreamBuilder {
    /**
     * Adds state changes to this stream builder.
     * @return this builder
     */
    default SingleTransactionStreamBuilder stateChanges(@NonNull List<StateChange> stateChanges) {
        return this;
    }

    /**
     * Sets the transaction for this stream item builder.
     * @param transaction the transaction
     * @return this builder
     */
    SingleTransactionStreamBuilder transaction(@NonNull Transaction transaction);

    /**
     * Returns the transaction for this stream item builder.
     * @return the transaction
     */
    Transaction transaction();

    /**
     * Returns any ids recorded as having explicit reward situations during the construction of this builder so far.
     * @return the ids with explicit reward situations
     */
    Set<AccountID> explicitRewardSituationIds();

    /**
     * Returns any staking rewards recorded during the construction of this builder so far.
     * @return the staking rewards paid so far
     */
    List<AccountAmount> getPaidStakingRewards();

    /**
     * Returns whether this builder has a contract result.
     * @return true if the builder has a contract result; otherwise false
     */
    boolean hasContractResult();

    /**
     * Returns the gas used already set in construction of this builder.
     * @return the gas used
     */
    long getGasUsedForContractTxn();

    /**
     * Returns the status that is currently set in the record builder.
     *
     * @return the status of the transaction
     */
    @NonNull
    ResponseCodeEnum status();

    /**
     * Returns the parsed transaction body for this record.
     *
     * @return the transaction body
     */
    @NonNull
    TransactionBody transactionBody();

    /**
     * Returns the current transaction fee.
     *
     * @return the current transaction fee
     */
    long transactionFee();

    /**
     * Sets the receipt status.
     *
     * @param status the receipt status
     * @return the builder
     */
    SingleTransactionStreamBuilder status(@NonNull ResponseCodeEnum status);

    /**
     * Returns the {@link TransactionRecord.Builder} of the record. It can be PRECEDING, CHILD, USER or SCHEDULED.
     * @return the {@link TransactionRecord.Builder} of the record
     */
    HandleContext.TransactionCategory category();

    /**
     * The behavior of the record when the parent transaction fails
     * @return the behavior
     */
    ReversingBehavior reversingBehavior();

    /**
     * Removes all the side effects on the record when the parent transaction fails
     */
    void nullOutSideEffectFields();

    /**
     * Sets the transactionID of the record based on the user transaction record.
     * @return the builder
     */
    SingleTransactionStreamBuilder syncBodyIdFromRecordId();

    /**
     * Sets the memo of the record.
     * @param memo the memo
     * @return the builder
     */
    SingleTransactionStreamBuilder memo(@NonNull String memo);

    /**
     * Sets the consensus timestamp of the record.
     * @param now the consensus timestamp
     * @return the builder
     */
    SingleTransactionStreamBuilder consensusTimestamp(@NonNull final Instant now);

    /**
     * Returns the transaction ID of the record.
     * @return the transaction ID
     */
    TransactionID transactionID();

    /**
     * Sets the transaction ID of the record.
     * @param transactionID the transaction ID
     * @return the builder
     */
    SingleTransactionStreamBuilder transactionID(@NonNull TransactionID transactionID);

    /**
     * Sets the parent consensus timestamp of the record.
     * @param parentConsensus the parent consensus timestamp
     * @return the builder
     */
    SingleTransactionStreamBuilder parentConsensus(@NonNull Instant parentConsensus);

    /**
     * Sets the transaction bytes of this builder.
     * @param transactionBytes the transaction bytes
     * @return this builder
     */
    SingleTransactionStreamBuilder transactionBytes(@NonNull Bytes transactionBytes);

    /**
     * Sets the exchange rate of this builder.
     * @param exchangeRate the exchange rate
     * @return this builder
     */
    SingleTransactionStreamBuilder exchangeRate(@NonNull ExchangeRateSet exchangeRate);

    /**
     * Sets the congestion multiplier used for charging the fees for this transaction. This is set if non-zero.
     * @param congestionMultiplier the congestion multiplier
     * @return this builder
     */
    SingleTransactionStreamBuilder congestionMultiplier(long congestionMultiplier);

    /**
     * Convenience method to package as {@link TransactionBody} as a {@link Transaction} .
     *
     * @param body the transaction body
     * @return the transaction
     */
    static Transaction transactionWith(@NonNull TransactionBody body) {
        final var bodyBytes = TransactionBody.PROTOBUF.toBytes(body);
        final var signedTransaction =
                SignedTransaction.newBuilder().bodyBytes(bodyBytes).build();
        final var signedTransactionBytes = SignedTransaction.PROTOBUF.toBytes(signedTransaction);
        return Transaction.newBuilder()
                .signedTransactionBytes(signedTransactionBytes)
                .build();
    }

    /**
     * Returns whether the transaction is a preceding transaction.
     * @return {@code true} if the transaction is a preceding transaction; otherwise {@code false}
     */
    default boolean isPreceding() {
        return category().equals(HandleContext.TransactionCategory.PRECEDING);
    }

    /**
     * Returns whether the transaction is an internal dispatch.
     * @return true if the transaction is an internal dispatch; otherwise false
     */
    default boolean isInternalDispatch() {
        return !category().equals(USER);
    }

    /**
     * Possible behavior of a SingleTransactionRecord when a parent transaction fails,
     * and it is asked to be reverted
     */
    enum ReversingBehavior {
        /**
         * Changes are not committed. The record is kept in the record stream,
         * but the status is set to {@link ResponseCodeEnum#REVERTED_SUCCESS}
         */
        REVERSIBLE,

        /**
         * Changes are not committed and the record is removed from the record stream.
         */
        REMOVABLE,

        /**
         * Changes are committed independent of the user and parent transactions.
         */
        IRREVERSIBLE
    }
}
