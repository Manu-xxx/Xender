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

package com.hedera.node.app.blocks;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionStreamBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A {@link SingleTransactionStreamBuilder} that forwards all received calls to an {@link IoBlockItemsBuilder} and a
 * {@link SingleTransactionRecordBuilderImpl}.
 *
 * TODO - implement this class
 */
public class PairedStreamBuilder implements SingleTransactionStreamBuilder {
    private final IoBlockItemsBuilder ioBlockItemsBuilder;
    private final SingleTransactionRecordBuilderImpl recordBuilder;

    public PairedStreamBuilder(
            @NonNull final ReversingBehavior reversingBehavior,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final HandleContext.TransactionCategory category) {
        recordBuilder = new SingleTransactionRecordBuilderImpl(reversingBehavior, customizer, category);
        ioBlockItemsBuilder = new IoBlockItemsBuilder(reversingBehavior, customizer, category);
    }

    @Override
    public SingleTransactionStreamBuilder transaction(@NonNull Transaction transaction) {
        return null;
    }

    @Override
    public Transaction transaction() {
        return null;
    }

    @Override
    public Set<AccountID> explicitRewardSituationIds() {
        return Set.of();
    }

    @Override
    public List<AccountAmount> getPaidStakingRewards() {
        return List.of();
    }

    @Override
    public boolean hasContractResult() {
        return false;
    }

    @Override
    public long getGasUsedForContractTxn() {
        return 0;
    }

    @NonNull
    @Override
    public ResponseCodeEnum status() {
        return null;
    }

    @NonNull
    @Override
    public TransactionBody transactionBody() {
        return null;
    }

    @Override
    public long transactionFee() {
        return 0;
    }

    @Override
    public SingleTransactionStreamBuilder status(@NonNull ResponseCodeEnum status) {
        return null;
    }

    @Override
    public HandleContext.TransactionCategory category() {
        return null;
    }

    @Override
    public ReversingBehavior reversingBehavior() {
        return null;
    }

    @Override
    public void nullOutSideEffectFields() {}

    @Override
    public SingleTransactionStreamBuilder syncBodyIdFromRecordId() {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder memo(@NonNull String memo) {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder consensusTimestamp(@NonNull Instant now) {
        return null;
    }

    @Override
    public TransactionID transactionID() {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder transactionID(@NonNull TransactionID transactionID) {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder parentConsensus(@NonNull Instant parentConsensus) {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder transactionBytes(@NonNull Bytes transactionBytes) {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder exchangeRate(@NonNull ExchangeRateSet exchangeRate) {
        return null;
    }
}
