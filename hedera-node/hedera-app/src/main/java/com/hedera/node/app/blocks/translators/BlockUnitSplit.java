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

package com.hedera.node.app.blocks.translators;

import static com.hedera.hapi.node.base.HederaFunctionality.FILE_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_UPDATE;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Splits a block into units for translation.
 */
public class BlockUnitSplit {
    /**
     * Holds the parts of a transaction that are pending processing.
     */
    private static class PendingBlockTransactionParts {
        @Nullable
        private TransactionParts parts;

        @Nullable
        private TransactionResult result;

        @Nullable
        private List<TransactionOutput> outputs;

        /**
         * Clears the pending parts.
         */
        void clear() {
            parts = null;
            result = null;
            outputs = null;
        }

        /**
         * Indicates whether the pending parts are complete.
         *
         * @return whether the pending parts are complete
         */
        boolean areComplete() {
            return parts != null && result != null;
        }

        void addOutput(@NonNull final TransactionOutput output) {
            if (outputs == null) {
                outputs = new ArrayList<>();
            }
            outputs.add(output);
        }

        BlockTransactionParts toBlockTransactionParts() {
            requireNonNull(parts);
            requireNonNull(result);
            return outputs == null
                    ? BlockTransactionParts.sansOutput(parts, result)
                    : BlockTransactionParts.withOutputs(parts, result, outputs.toArray(TransactionOutput[]::new));
        }
    }

    private static class BlockItemProcessor {
        private TxnIdType lastTxnIdType = null;
        private TransactionID unitTxnId = null;
        private final PendingBlockTransactionParts pendingParts = new PendingBlockTransactionParts();
        private final List<BlockTransactionParts> unitParts = new ArrayList<>();
        private final List<StateChange> unitStateChanges = new ArrayList<>();

        public void processBlockItem(BlockItem item, List<BlockTransactionalUnit> units) {
            switch (item.item().kind()) {
                case UNSET, RECORD_FILE -> throw new IllegalStateException(
                        "Cannot split block with item of kind " + item.item().kind());
                case BLOCK_HEADER, EVENT_HEADER, ROUND_HEADER, FILTERED_ITEM_HASH, BLOCK_PROOF -> {
                    // No-op
                }
                case EVENT_TRANSACTION -> processEventTransaction(item, units);
                case TRANSACTION_RESULT -> pendingParts.result = item.transactionResultOrThrow();
                case TRANSACTION_OUTPUT -> pendingParts.addOutput(item.transactionOutputOrThrow());
                case STATE_CHANGES -> unitStateChanges.addAll(
                        item.stateChangesOrThrow().stateChanges());
            }
        }

        private void processEventTransaction(BlockItem item, List<BlockTransactionalUnit> units) {
            final var eventTransaction = item.eventTransactionOrThrow();
            if (eventTransaction.hasApplicationTransaction()) {
                final var nextParts = TransactionParts.from(eventTransaction.applicationTransactionOrThrow());
                final var txnId = nextParts.transactionIdOrThrow();
                if (pendingParts.areComplete()) {
                    unitParts.add(pendingParts.toBlockTransactionParts());
                }
                final var txnIdType = classifyTxnId(txnId, unitTxnId, nextParts, lastTxnIdType);
                if (txnIdType == TxnIdType.NEW_UNIT_BY_ID && !unitParts.isEmpty()) {
                    completeAndAdd(units);
                }
                pendingParts.clear();
                if (txnIdType != TxnIdType.AUTO_SYSFILE_MGMT_ID) {
                    unitTxnId = txnId;
                }
                pendingParts.parts = nextParts;
                lastTxnIdType = txnIdType;
            }
        }

        private TxnIdType classifyTxnId(
                @NonNull final TransactionID nextId,
                @Nullable final TransactionID unitTxnId,
                @NonNull final TransactionParts parts,
                @Nullable final TxnIdType lastTxnIdType) {
            if (isAutoSysFileMgmtTxn(parts)) {
                return TxnIdType.AUTO_SYSFILE_MGMT_ID;
            }
            if (lastTxnIdType == TxnIdType.AUTO_SYSFILE_MGMT_ID) {
                // Automatic system file management transactions never end a transactional unit
                return TxnIdType.SAME_UNIT_BY_ID;
            }
            if (unitTxnId == null) {
                return TxnIdType.NEW_UNIT_BY_ID;
            }
            // Scheduled transactions never begin a new transactional unit and
            final var radicallyDifferent = !nextId.scheduled()
                    && (!nextId.accountIDOrElse(AccountID.DEFAULT).equals(unitTxnId.accountIDOrElse(AccountID.DEFAULT))
                            || !nextId.transactionValidStartOrElse(Timestamp.DEFAULT)
                                    .equals(unitTxnId.transactionValidStartOrElse(Timestamp.DEFAULT)));
            return radicallyDifferent ? TxnIdType.NEW_UNIT_BY_ID : TxnIdType.SAME_UNIT_BY_ID;
        }

        private boolean isAutoSysFileMgmtTxn(@NonNull final TransactionParts parts) {
            return (parts.function() == FILE_CREATE || parts.function() == FILE_UPDATE)
                    && parts.transactionIdOrThrow().nonce() > 0;
        }

        private void completeAndAdd(@NonNull final List<BlockTransactionalUnit> units) {
            units.add(new BlockTransactionalUnit(new ArrayList<>(unitParts), new LinkedList<>(unitStateChanges)));
            unitParts.clear();
            unitStateChanges.clear();
        }

        public void checkComplete(final List<BlockTransactionalUnit> units) {
            if (pendingParts.areComplete()) {
                unitParts.add(pendingParts.toBlockTransactionParts());
                completeAndAdd(units);
            }
        }
    }

    /**
     * Splits the given block into transactional units.
     * @param blockItems the block items to split
     * @return the transactional units
     */
    public List<BlockTransactionalUnit> split(@NonNull final List<BlockItem> blockItems) {
        final List<BlockTransactionalUnit> units = new ArrayList<>();
        final BlockItemProcessor processor = new BlockItemProcessor();

        for (final var item : blockItems) {
            processor.processBlockItem(item, units);
        }
        processor.checkComplete(units);
        return units;
    }

    private enum TxnIdType {
        AUTO_SYSFILE_MGMT_ID,
        SAME_UNIT_BY_ID,
        NEW_UNIT_BY_ID,
    }
}
