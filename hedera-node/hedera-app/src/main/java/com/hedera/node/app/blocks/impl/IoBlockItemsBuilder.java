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

package com.hedera.node.app.blocks.impl;

import static com.hedera.hapi.block.stream.output.StateChangesCause.STATE_CHANGE_CAUSE_TRANSACTION;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.hedera.hapi.util.HapiUtils.functionOf;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.CallContractOutput;
import com.hedera.hapi.block.stream.output.CreateContractOutput;
import com.hedera.hapi.block.stream.output.CreateScheduleOutput;
import com.hedera.hapi.block.stream.output.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.EthereumOutput;
import com.hedera.hapi.block.stream.output.RunningHashVersion;
import com.hedera.hapi.block.stream.output.SignScheduleOutput;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.SubmitMessageOutput;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.block.stream.output.UtilPrngOutput;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.service.addressbook.impl.records.NodeCreateRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCreateRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractDeleteRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractOperationRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractUpdateRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.EthereumTransactionRecordBuilder;
import com.hedera.node.app.service.file.impl.records.CreateFileRecordBuilder;
import com.hedera.node.app.service.schedule.ScheduleRecordBuilder;
import com.hedera.node.app.service.token.api.FeeRecordBuilder;
import com.hedera.node.app.service.token.records.ChildRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoDeleteRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoUpdateRecordBuilder;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.service.token.records.NodeStakeUpdateRecordBuilder;
import com.hedera.node.app.service.token.records.TokenAccountWipeRecordBuilder;
import com.hedera.node.app.service.token.records.TokenBurnRecordBuilder;
import com.hedera.node.app.service.token.records.TokenCreateRecordBuilder;
import com.hedera.node.app.service.token.records.TokenMintRecordBuilder;
import com.hedera.node.app.service.token.records.TokenUpdateRecordBuilder;
import com.hedera.node.app.service.util.impl.records.PrngRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionStreamBuilder;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of {@link IoBlockItemsBuilder} that produces block items for a single user or
 * synthetic transaction; that is, the "input" block item with a {@link Transaction} and "output" block items
 * with a {@link TransactionResult} and, optionally, {@link TransactionOutput}.
 */
public class IoBlockItemsBuilder
        implements SingleTransactionStreamBuilder,
                ConsensusCreateTopicRecordBuilder,
                ConsensusSubmitMessageRecordBuilder,
                CreateFileRecordBuilder,
                CryptoCreateRecordBuilder,
                CryptoTransferRecordBuilder,
                ChildRecordBuilder,
                PrngRecordBuilder,
                ScheduleRecordBuilder,
                TokenMintRecordBuilder,
                TokenBurnRecordBuilder,
                TokenCreateRecordBuilder,
                ContractCreateRecordBuilder,
                ContractCallRecordBuilder,
                ContractUpdateRecordBuilder,
                EthereumTransactionRecordBuilder,
                CryptoDeleteRecordBuilder,
                TokenUpdateRecordBuilder,
                NodeStakeUpdateRecordBuilder,
                FeeRecordBuilder,
                ContractDeleteRecordBuilder,
                GenesisAccountRecordBuilder,
                ContractOperationRecordBuilder,
                TokenAccountWipeRecordBuilder,
                CryptoUpdateRecordBuilder,
                NodeCreateRecordBuilder {
    // base transaction data
    private Transaction transaction;
    private Bytes transactionBytes = Bytes.EMPTY;
    // fields needed for TransactionRecord
    // Mutable because the provisional consensus timestamp assigned on dispatch could
    // change when removable records appear "between" this record and the parent record
    private Instant consensusNow;
    private Instant parentConsensus;
    private TransactionID transactionID;
    private List<TokenTransferList> tokenTransferLists = new LinkedList<>();
    private List<AssessedCustomFee> assessedCustomFees = new LinkedList<>();
    private List<TokenAssociation> automaticTokenAssociations = new LinkedList<>();

    private List<AccountAmount> paidStakingRewards = new LinkedList<>();
    private final TransactionResult.Builder transactionResultBuilder = TransactionResult.newBuilder();
    private TransferList transferList = TransferList.DEFAULT;

    // fields needed for TransactionReceipt
    private ResponseCodeEnum status = ResponseCodeEnum.OK;
    private ExchangeRateSet exchangeRate = ExchangeRateSet.DEFAULT;
    private List<Long> serialNumbers = new LinkedList<>();
    private long newTotalSupply = 0L;
    private final TransactionOutput.Builder transactionOutputBuilder = TransactionOutput.newBuilder();
    // Sidecar data, booleans are the migration flag
    private List<AbstractMap.SimpleEntry<ContractStateChanges, Boolean>> contractStateChanges = new LinkedList<>();
    private List<AbstractMap.SimpleEntry<ContractActions, Boolean>> contractActions = new LinkedList<>();
    private List<AbstractMap.SimpleEntry<ContractBytecode, Boolean>> contractBytecodes = new LinkedList<>();

    // Fields that are not in TransactionRecord, but are needed for computing staking rewards
    // These are not persisted to the record file
    private final Map<AccountID, AccountID> deletedAccountBeneficiaries = new HashMap<>();

    // A set of ids that should be explicitly considered as in a "reward situation",
    // despite the canonical definition of a reward situation; needed for mono-service
    // fidelity only
    @Nullable
    private Set<AccountID> explicitRewardReceiverIds;

    // While the fee is sent to the underlying builder all the time, it is also cached here because, as of today,
    // there is no way to get the transaction fee from the PBJ object.
    private long transactionFee;
    private ContractFunctionResult contractFunctionResult;

    // Used for some child records builders.
    private final ReversingBehavior reversingBehavior;

    // Category of the record
    private final HandleContext.TransactionCategory category;

    private List<StateChange> stateChanges = new ArrayList<>();

    // Used to customize the externalized form of a dispatched child transaction, right before
    // its record stream item is built; lets the contract service externalize certain dispatched
    // CryptoCreate transactions as ContractCreate synthetic transactions
    private final ExternalizedRecordCustomizer customizer;

    private TokenID tokenID;
    private TokenType tokenType;
    private Bytes ethereumHash;
    private TransactionID scheduledTransactionID;

    public IoBlockItemsBuilder(
            @NonNull final ReversingBehavior reversingBehavior,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final HandleContext.TransactionCategory category) {
        this.reversingBehavior = reversingBehavior;
        this.customizer = customizer;
        this.category = category;
    }

    @Override
    public SingleTransactionStreamBuilder stateChanges(@NonNull List<StateChange> stateChanges) {
        this.stateChanges.addAll(stateChanges);
        return this;
    }

    /**
     * Builds the list of block items.
     * @return the list of block items
     */
    public List<BlockItem> build() {
        final var blockItems = new ArrayList<BlockItem>();

        final var transactionBlockItem =
                BlockItem.newBuilder().transaction(transaction()).build();
        blockItems.add(transactionBlockItem);

        if (!transactionOutputBuilder.equals(TransactionOutput.newBuilder())) {
            final var outputBlockItem = getTransactionOutputBlockItem();
            blockItems.add(outputBlockItem);
        }
        if (!transactionResultBuilder.equals(TransactionResult.newBuilder())) {
            final var resultBlockItem = getTransactionResultBlockItem();
            blockItems.add(resultBlockItem);
        }
        if (!stateChanges.isEmpty()) {
            final var stateChangesBlockItem = BlockItem.newBuilder()
                    .stateChanges(StateChanges.newBuilder()
                            .cause(STATE_CHANGE_CAUSE_TRANSACTION)
                            .consensusTimestamp(Timestamp.DEFAULT)
                            .stateChanges(stateChanges)
                            .build())
                    .build();
            blockItems.add(stateChangesBlockItem);
        }
        return blockItems;
    }

    @NonNull
    private BlockItem getTransactionResultBlockItem() {
        if (!automaticTokenAssociations.isEmpty()) {
            transactionResultBuilder.automaticTokenAssociations(automaticTokenAssociations);
        }
        final var transactionResultBlockItem = BlockItem.newBuilder()
                .transactionResult(transactionResultBuilder.build())
                .build();
        return transactionResultBlockItem;
    }

    @NonNull
    private BlockItem getTransactionOutputBlockItem() {
        var function = HederaFunctionality.NONE;
        try {
            function = functionOf(transactionBody());
        } catch (Exception e) {
            // No-op
        }
        final var sideCars = getSideCars();
        if (!sideCars.isEmpty()) {
            if (function == HederaFunctionality.CONTRACT_CALL) {
                transactionOutputBuilder.contractCall(CallContractOutput.newBuilder()
                        .contractCallResult(contractFunctionResult)
                        .sidecars(sideCars)
                        .build());
            } else if (function == HederaFunctionality.ETHEREUM_TRANSACTION) {
                transactionOutputBuilder.ethereumCall(EthereumOutput.newBuilder()
                        .ethereumHash(ethereumHash)
                        .sidecars(sideCars)
                        .build());
            } else if (function == HederaFunctionality.CONTRACT_CREATE) {
                transactionOutputBuilder.contractCreate(CreateContractOutput.newBuilder()
                        .sidecars(sideCars)
                        .contractCreateResult(contractFunctionResult)
                        .build());
            }
        }
        if (function == HederaFunctionality.SCHEDULE_CREATE) {
            transactionOutputBuilder.createSchedule(CreateScheduleOutput.newBuilder()
                    .scheduledTransactionId(scheduledTransactionID)
                    .build());
        } else if (function == HederaFunctionality.SCHEDULE_SIGN) {
            transactionOutputBuilder.signSchedule(SignScheduleOutput.newBuilder()
                    .scheduledTransactionId(scheduledTransactionID)
                    .build());
        }
        final var transactionOutputBlockItem = BlockItem.newBuilder()
                .transactionOutput(transactionOutputBuilder.build())
                .build();
        return transactionOutputBlockItem;
    }

    private List<TransactionSidecarRecord> getSideCars() {
        final var timestamp = asTimestamp(consensusNow);
        // create list of sidecar records
        final List<TransactionSidecarRecord> transactionSidecarRecords = new ArrayList<>();
        contractStateChanges.stream()
                .map(pair -> new TransactionSidecarRecord(
                        timestamp,
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.STATE_CHANGES, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        contractActions.stream()
                .map(pair -> new TransactionSidecarRecord(
                        timestamp,
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.ACTIONS, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        contractBytecodes.stream()
                .map(pair -> new TransactionSidecarRecord(
                        timestamp,
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.BYTECODE, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        return transactionSidecarRecords;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public ReversingBehavior reversingBehavior() {
        return reversingBehavior;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // base transaction data
    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder parentConsensus(@NonNull final Instant parentConsensus) {
        this.parentConsensus = requireNonNull(parentConsensus, "parentConsensus must not be null");
        transactionResultBuilder.parentConsensusTimestamp(Timestamp.newBuilder()
                .seconds(parentConsensus.getEpochSecond())
                .nanos(parentConsensus.getNano())
                .build());
        return this;
    }
    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder consensusTimestamp(@NonNull final Instant now) {
        this.consensusNow = requireNonNull(now, "consensus time must not be null");
        transactionResultBuilder.consensusTimestamp(Timestamp.newBuilder()
                .seconds(now.getEpochSecond())
                .nanos(now.getNano())
                .build());
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder transaction(@NonNull final Transaction transaction) {
        this.transaction = requireNonNull(transaction, "transaction must not be null");
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder transactionBytes(@NonNull final Bytes transactionBytes) {
        this.transactionBytes = requireNonNull(transactionBytes, "transactionBytes must not be null");
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public TransactionID transactionID() {
        return transactionID;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder transactionID(@NonNull final TransactionID transactionID) {
        this.transactionID = requireNonNull(transactionID, "transactionID must not be null");
        return this;
    }

    /**{@inheritDoc}*/
    @NonNull
    @Override
    public IoBlockItemsBuilder syncBodyIdFromRecordId() {
        final var newTransactionID = transactionID;
        final var body =
                inProgressBody().copyBuilder().transactionID(newTransactionID).build();
        this.transaction = SingleTransactionStreamBuilder.transactionWith(body);
        this.transactionBytes = transaction.signedTransactionBytes();
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder memo(@NonNull final String memo) {
        // No-op
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionRecord

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public Transaction transaction() {
        return transaction;
    }

    /**{@inheritDoc}*/
    @Override
    public long transactionFee() {
        return transactionFee;
    }

    /**{@inheritDoc}*/
    @NonNull
    @Override
    public IoBlockItemsBuilder transactionFee(final long transactionFee) {
        transactionResultBuilder.transactionFeeCharged(transactionFee);
        this.transactionFee = transactionFee;
        return this;
    }

    @Override
    public void trackExplicitRewardSituation(@NonNull final AccountID accountId) {
        if (explicitRewardReceiverIds == null) {
            explicitRewardReceiverIds = new LinkedHashSet<>();
        }
        explicitRewardReceiverIds.add(accountId);
    }

    @Override
    public Set<AccountID> explicitRewardSituationIds() {
        return explicitRewardReceiverIds != null ? explicitRewardReceiverIds : emptySet();
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder contractCallResult(@Nullable final ContractFunctionResult contractCallResult) {
        transactionOutputBuilder.contractCall(CallContractOutput.newBuilder()
                .contractCallResult(contractCallResult)
                .build());
        this.contractFunctionResult = contractCallResult;
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder contractCreateResult(@Nullable ContractFunctionResult contractCreateResult) {
        transactionOutputBuilder.contractCreate(CreateContractOutput.newBuilder()
                .contractCreateResult(contractCreateResult)
                .build());
        this.contractFunctionResult = contractCreateResult;
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public TransferList transferList() {
        return transferList;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder transferList(@Nullable final TransferList transferList) {
        this.transferList = transferList;
        transactionResultBuilder.transferList(transferList);
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder tokenTransferLists(@NonNull final List<TokenTransferList> tokenTransferLists) {
        requireNonNull(tokenTransferLists, "tokenTransferLists must not be null");
        this.tokenTransferLists = tokenTransferLists;
        transactionResultBuilder.tokenTransferLists(tokenTransferLists);
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    public List<TokenTransferList> tokenTransferLists() {
        return tokenTransferLists;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder tokenType(final @NonNull TokenType tokenType) {
        this.tokenType = requireNonNull(tokenType);
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder scheduleRef(@NonNull final ScheduleID scheduleRef) {
        requireNonNull(scheduleRef, "scheduleRef must not be null");
        transactionResultBuilder.scheduleRef(scheduleRef);
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder assessedCustomFees(@NonNull final List<AssessedCustomFee> assessedCustomFees) {
        requireNonNull(assessedCustomFees, "assessedCustomFees must not be null");
        this.assessedCustomFees = assessedCustomFees;
        transactionOutputBuilder.cryptoTransfer(CryptoTransferOutput.newBuilder()
                .assessedCustomFees(assessedCustomFees)
                .build());
        return this;
    }

    /**{@inheritDoc}*/
    @NonNull
    public IoBlockItemsBuilder addAutomaticTokenAssociation(@NonNull final TokenAssociation automaticTokenAssociation) {
        requireNonNull(automaticTokenAssociation, "automaticTokenAssociation must not be null");
        automaticTokenAssociations.add(automaticTokenAssociation);
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder ethereumHash(@NonNull final Bytes ethereumHash) {
        requireNonNull(ethereumHash, "ethereumHash must not be null");
        transactionOutputBuilder.ethereumCall(
                EthereumOutput.newBuilder().ethereumHash(ethereumHash).build());
        this.ethereumHash = ethereumHash;
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder paidStakingRewards(@NonNull final List<AccountAmount> paidStakingRewards) {
        // These need not be externalized to block streams
        requireNonNull(paidStakingRewards, "paidStakingRewards must not be null");
        this.paidStakingRewards = paidStakingRewards;
        transactionResultBuilder.paidStakingRewards(paidStakingRewards);
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder entropyNumber(final int num) {
        transactionOutputBuilder.utilPrng(
                UtilPrngOutput.newBuilder().prngNumber(num).build());
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder entropyBytes(@NonNull final Bytes prngBytes) {
        requireNonNull(prngBytes, "The argument 'prngBytes' must not be null");
        transactionOutputBuilder.utilPrng(
                UtilPrngOutput.newBuilder().prngBytes(prngBytes).build());
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder evmAddress(@NonNull final Bytes evmAddress) {
        // No-op
        return this;
    }

    @Override
    @NonNull
    public List<AssessedCustomFee> getAssessedCustomFees() {
        return assessedCustomFees;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionReceipt

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder status(@NonNull final ResponseCodeEnum status) {
        this.status = requireNonNull(status, "status must not be null");
        transactionResultBuilder.status(status);
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public ResponseCodeEnum status() {
        return status;
    }

    /**{@inheritDoc}*/
    @Override
    public boolean hasContractResult() {
        return this.contractFunctionResult != null;
    }

    /**{@inheritDoc}*/
    @Override
    public long getGasUsedForContractTxn() {
        return this.contractFunctionResult.gasUsed();
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder accountID(@NonNull final AccountID accountID) {
        // No-op
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder fileID(@NonNull final FileID fileID) {
        // No-op
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder contractID(@Nullable final ContractID contractID) {
        // No-op
        return this;
    }

    /**{@inheritDoc}*/
    @NonNull
    @Override
    public IoBlockItemsBuilder exchangeRate(@NonNull final ExchangeRateSet exchangeRate) {
        requireNonNull(exchangeRate, "exchangeRate must not be null");
        transactionResultBuilder.exchangeRate(exchangeRate);
        this.exchangeRate = exchangeRate;
        return this;
    }

    /**{@inheritDoc}*/
    @NonNull
    @Override
    public IoBlockItemsBuilder congestionMultiplier(long congestionMultiplier) {
        if (congestionMultiplier != 0) {
            transactionResultBuilder.congestionPricingMultiplier(congestionMultiplier);
        }
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder topicID(@NonNull final TopicID topicID) {
        // No-op
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder topicSequenceNumber(final long topicSequenceNumber) {
        // No-op
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder topicRunningHash(@NonNull final Bytes topicRunningHash) {
        // No-op
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder topicRunningHashVersion(final long topicRunningHashVersion) {
        // TOD0: Need to confirm what the value should be
        transactionOutputBuilder.submitMessage(SubmitMessageOutput.newBuilder()
                .topicRunningHashVersion(RunningHashVersion.WITH_FULL_MESSAGE)
                .build());
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder tokenID(@NonNull final TokenID tokenID) {
        requireNonNull(tokenID, "tokenID must not be null");
        this.tokenID = tokenID;
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    public TokenID tokenID() {
        return tokenID;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder nodeID(long nodeId) {
        // No-op
        return this;
    }

    /**{@inheritDoc}*/
    @NonNull
    public IoBlockItemsBuilder newTotalSupply(final long newTotalSupply) {
        this.newTotalSupply = newTotalSupply;
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    public long getNewTotalSupply() {
        return newTotalSupply;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder scheduleID(@NonNull final ScheduleID scheduleID) {
        // No-op
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder scheduledTransactionID(@NonNull final TransactionID scheduledTransactionID) {
        this.scheduledTransactionID = scheduledTransactionID;
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder serialNumbers(@NonNull final List<Long> serialNumbers) {
        requireNonNull(serialNumbers, "serialNumbers must not be null");
        this.serialNumbers = serialNumbers;
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public List<Long> serialNumbers() {
        return serialNumbers;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Sidecar data, booleans are the migration flag
    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder addContractStateChanges(
            @NonNull final ContractStateChanges contractStateChanges, final boolean isMigration) {
        requireNonNull(contractStateChanges, "contractStateChanges must not be null");
        this.contractStateChanges.add(new AbstractMap.SimpleEntry<>(contractStateChanges, isMigration));
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder addContractActions(
            @NonNull final ContractActions contractActions, final boolean isMigration) {
        requireNonNull(contractActions, "contractActions must not be null");
        this.contractActions.add(new AbstractMap.SimpleEntry<>(contractActions, isMigration));
        return this;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public IoBlockItemsBuilder addContractBytecode(
            @NonNull final ContractBytecode contractBytecode, final boolean isMigration) {
        requireNonNull(contractBytecode, "contractBytecode must not be null");
        contractBytecodes.add(new AbstractMap.SimpleEntry<>(contractBytecode, isMigration));
        return this;
    }

    // ------------- Information needed by token service for redirecting staking rewards to appropriate accounts

    /**{@inheritDoc}*/
    @Override
    public void addBeneficiaryForDeletedAccount(
            @NonNull final AccountID deletedAccountID, @NonNull final AccountID beneficiaryForDeletedAccount) {
        requireNonNull(deletedAccountID, "deletedAccountID must not be null");
        requireNonNull(beneficiaryForDeletedAccount, "beneficiaryForDeletedAccount must not be null");
        deletedAccountBeneficiaries.put(deletedAccountID, beneficiaryForDeletedAccount);
    }

    /**{@inheritDoc}*/
    @Override
    public int getNumberOfDeletedAccounts() {
        return deletedAccountBeneficiaries.size();
    }

    /**{@inheritDoc}*/
    @Override
    @Nullable
    public AccountID getDeletedAccountBeneficiaryFor(@NonNull final AccountID deletedAccountID) {
        return deletedAccountBeneficiaries.get(deletedAccountID);
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public ContractFunctionResult contractFunctionResult() {
        return contractFunctionResult;
    }
    /**{@inheritDoc}*/
    @Override
    @NonNull
    public TransactionBody transactionBody() {
        return inProgressBody();
    }

    /**{@inheritDoc}*/
    private TransactionBody inProgressBody() {
        try {
            final var signedTransaction = SignedTransaction.PROTOBUF.parseStrict(
                    transaction.signedTransactionBytes().toReadableSequentialData());
            return TransactionBody.PROTOBUF.parse(signedTransaction.bodyBytes().toReadableSequentialData());
        } catch (Exception e) {
            throw new IllegalStateException("Record being built for unparseable transaction", e);
        }
    }

    /**{@inheritDoc}*/
    @NonNull
    @Override
    public List<AccountAmount> getPaidStakingRewards() {
        return paidStakingRewards;
    }

    /**{@inheritDoc}*/
    @Override
    @NonNull
    public HandleContext.TransactionCategory category() {
        return category;
    }

    /**{@inheritDoc}*/
    @Override
    public void nullOutSideEffectFields() {
        serialNumbers.clear();
        tokenTransferLists.clear();
        automaticTokenAssociations.clear();
        transferList = TransferList.DEFAULT;
        paidStakingRewards.clear();
        assessedCustomFees.clear();

        newTotalSupply = 0L;
        transactionFee = 0L;
        contractFunctionResult = null;
        // Note that internal contract creations are removed instead of reversed
        transactionResultBuilder.scheduleRef((ScheduleID) null);
        transactionResultBuilder.automaticTokenAssociations(emptyList());
        transactionResultBuilder.congestionPricingMultiplier(0);
        transactionResultBuilder.status(ResponseCodeEnum.OK);
        transactionResultBuilder.parentConsensusTimestamp(Timestamp.DEFAULT);

        transactionOutputBuilder.submitMessage((SubmitMessageOutput) null);
        transactionOutputBuilder.cryptoTransfer((CryptoTransferOutput) null);
        transactionOutputBuilder.utilPrng((UtilPrngOutput) null);
        transactionOutputBuilder.contractCall((CallContractOutput) null);
        transactionOutputBuilder.ethereumCall((EthereumOutput) null);
        transactionOutputBuilder.contractCreate((CreateContractOutput) null);
        transactionOutputBuilder.createSchedule((CreateScheduleOutput) null);
        transactionOutputBuilder.signSchedule((SignScheduleOutput) null);
    }
}
