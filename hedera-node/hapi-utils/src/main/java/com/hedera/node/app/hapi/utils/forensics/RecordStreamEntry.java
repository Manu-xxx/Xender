/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hapi.utils.forensics;

import static com.hedera.node.app.hapi.utils.CommonUtils.timestampToInstant;

import com.hedera.hapi.node.base.FileID;
import com.hedera.node.app.hapi.utils.CommonPbjConverters;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Represents a single {@code (Transaction, TransactionRecord)} entry from a record stream,
 * including the consensus time as a {@link Instant} for convenience.
 *
 * @param parts         the transaction parts
 * @param txnRecord     the resolved record the transaction
 * @param consensusTime the consensus time
 */
public record RecordStreamEntry(TransactionParts parts, TransactionRecord txnRecord, Instant consensusTime)
        implements Comparable<RecordStreamEntry> {
    @Override
    public int compareTo(@NonNull RecordStreamEntry that) {
        return this.consensusTime.compareTo(that.consensusTime);
    }

    /**
     * Constructs a {@link RecordStreamEntry} from a {@link RecordStreamItem}.
     *
     * @param item the item to convert
     * @return the constructed entry
     */
    public static RecordStreamEntry from(@NonNull final RecordStreamItem item) {
        final var itemRecord = item.getRecord();
        return new RecordStreamEntry(
                TransactionParts.from(item.getTransaction()),
                itemRecord,
                timestampToInstant(itemRecord.getConsensusTimestamp()));
    }

    public Transaction submittedTransaction() {
        return parts.wrapper();
    }

    public TransactionBody body() {
        return parts.body();
    }

    public ResponseCodeEnum finalStatus() {
        return txnRecord.getReceipt().getStatus();
    }

    /**
     * Returns the file ID created by the transaction, if any.
     *
     * @return the created file ID
     */
    public FileID createdFileId() {
        return CommonPbjConverters.toPbj(txnRecord.getReceipt().getFileID());
    }

    public HederaFunctionality function() {
        return parts.function();
    }

    public TransactionRecord transactionRecord() {
        return txnRecord;
    }

    @Override
    public String toString() {
        return String.format(
                "RecordStreamEntry{consensusTime=%s, txn=%s, record=%s}", consensusTime, body(), txnRecord);
    }
}
