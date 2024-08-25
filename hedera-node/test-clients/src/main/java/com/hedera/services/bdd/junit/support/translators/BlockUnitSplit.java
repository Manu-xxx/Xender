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

package com.hedera.services.bdd.junit.support.translators;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionalUnit;
import com.hedera.services.bdd.junit.support.translators.inputs.TransactionParts;
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
    private class PendingBlockTransactionParts {
        @Nullable
        private TransactionParts parts;

        @Nullable
        private TransactionResult result;

        @Nullable
        private TransactionOutput output;

        /**
         * Clears the pending parts.
         */
        void clear() {
            parts = null;
            result = null;
            output = null;
        }

        /**
         * Indicates whether the pending parts are complete.
         *
         * @return whether the pending parts are complete
         */
        boolean areComplete() {
            return parts != null && result != null;
        }

        BlockTransactionParts toBlockTransactionParts() {
            requireNonNull(parts);
            requireNonNull(result);
            return output == null
                    ? BlockTransactionParts.sansOutput(parts, result)
                    : BlockTransactionParts.withOutput(parts, result, output);
        }
    }

    public List<BlockTransactionalUnit> split(@NonNull final Block block) {
        final List<BlockTransactionalUnit> units = new ArrayList<>();

        TransactionID unitTxnId = null;
        PendingBlockTransactionParts pendingParts = new PendingBlockTransactionParts();
        final List<BlockTransactionParts> unitParts = new ArrayList<>();
        final List<StateChange> unitStateChanges = new ArrayList<>();
        for (final var item : block.items()) {
            switch (item.item().kind()) {
                case UNSET, RECORD_FILE -> throw new IllegalStateException(
                        "Cannot split block with item of kind " + item.item().kind());
                case BLOCK_HEADER, EVENT_HEADER, ROUND_HEADER, FILTERED_ITEM_HASH, BLOCK_PROOF -> {
                    // No-op
                }
                case EVENT_TRANSACTION -> {
                    final var eventTransaction = item.eventTransactionOrThrow();
                    if (eventTransaction.hasApplicationTransaction()) {
                        final var nextParts = TransactionParts.from(eventTransaction.applicationTransactionOrThrow());
                        final var txnId = nextParts.transactionIdOrThrow();
                        if (pendingParts.areComplete()) {
                            unitParts.add(pendingParts.toBlockTransactionParts());
                        }
                        if (beginsNewUnit(txnId, unitTxnId) && !unitParts.isEmpty()) {
                            completeAndAdd(units, unitParts, unitStateChanges);
                        }
                        pendingParts.clear();
                        unitTxnId = txnId;
                        pendingParts.parts = nextParts;
                    }
                }
                case TRANSACTION_RESULT -> pendingParts.result = item.transactionResultOrThrow();
                case TRANSACTION_OUTPUT -> pendingParts.output = item.transactionOutputOrThrow();
                case STATE_CHANGES -> unitStateChanges.addAll(
                        item.stateChangesOrThrow().stateChanges());
            }
        }
        if (pendingParts.areComplete()) {
            unitParts.add(pendingParts.toBlockTransactionParts());
            completeAndAdd(units, unitParts, unitStateChanges);
        }
        return units;
    }

    private boolean beginsNewUnit(@NonNull final TransactionID nextId, @Nullable final TransactionID unitTxnId) {
        if (unitTxnId == null) {
            return true;
        }
        return nextId.accountIDOrElse(AccountID.DEFAULT).equals(unitTxnId.accountIDOrElse(AccountID.DEFAULT))
                && nextId.transactionValidStartOrElse(Timestamp.DEFAULT)
                        .equals(unitTxnId.transactionValidStartOrElse(Timestamp.DEFAULT));
    }

    private void completeAndAdd(
            @NonNull final List<BlockTransactionalUnit> units,
            @NonNull final List<BlockTransactionParts> unitParts,
            @NonNull final List<StateChange> unitStateChanges) {
        units.add(new BlockTransactionalUnit(new ArrayList<>(unitParts), new LinkedList<>(unitStateChanges)));
        unitParts.clear();
        unitStateChanges.clear();
    }
}
