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

package com.hedera.node.app.service.schedule.impl.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.ScheduleID.Builder;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody.DataOneOfType;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A package-private utility class for Schedule Handlers.
 */
final class HandlerUtility {
    private HandlerUtility() {}

    @NonNull
    static TransactionBody childAsOrdinary(@NonNull final Schedule scheduleInState) {
        final TransactionID scheduledTransactionId = transactionIdForScheduled(scheduleInState);
        final SchedulableTransactionBody scheduledTransaction = scheduleInState.scheduledTransaction();
        final TransactionBody.Builder ordinary = TransactionBody.newBuilder();
        if (scheduledTransaction != null) {
            ordinary.transactionFee(scheduledTransaction.transactionFee())
                    .memo(scheduledTransaction.memo())
                    .transactionID(scheduledTransactionId);
            switch (scheduledTransaction.data().kind()) {
                case CONSENSUS_CREATE_TOPIC -> ordinary.consensusCreateTopic(
                        scheduledTransaction.consensusCreateTopicOrThrow());
                case CONSENSUS_UPDATE_TOPIC -> ordinary.consensusUpdateTopic(
                        scheduledTransaction.consensusUpdateTopicOrThrow());
                case CONSENSUS_DELETE_TOPIC -> ordinary.consensusDeleteTopic(
                        scheduledTransaction.consensusDeleteTopicOrThrow());
                case CONSENSUS_SUBMIT_MESSAGE -> ordinary.consensusSubmitMessage(
                        scheduledTransaction.consensusSubmitMessageOrThrow());
                case CRYPTO_CREATE_ACCOUNT -> ordinary.cryptoCreateAccount(
                        scheduledTransaction.cryptoCreateAccountOrThrow());
                case CRYPTO_UPDATE_ACCOUNT -> ordinary.cryptoUpdateAccount(
                        scheduledTransaction.cryptoUpdateAccountOrThrow());
                case CRYPTO_TRANSFER -> ordinary.cryptoTransfer(scheduledTransaction.cryptoTransferOrThrow());
                case CRYPTO_DELETE -> ordinary.cryptoDelete(scheduledTransaction.cryptoDeleteOrThrow());
                case FILE_CREATE -> ordinary.fileCreate(scheduledTransaction.fileCreateOrThrow());
                case FILE_APPEND -> ordinary.fileAppend(scheduledTransaction.fileAppendOrThrow());
                case FILE_UPDATE -> ordinary.fileUpdate(scheduledTransaction.fileUpdateOrThrow());
                case FILE_DELETE -> ordinary.fileDelete(scheduledTransaction.fileDeleteOrThrow());
                case CONTRACT_CREATE_INSTANCE -> ordinary.contractCreateInstance(
                        scheduledTransaction.contractCreateInstanceOrThrow());
                case CONTRACT_UPDATE_INSTANCE -> ordinary.contractUpdateInstance(
                        scheduledTransaction.contractUpdateInstanceOrThrow());
                case CONTRACT_CALL -> ordinary.contractCall(scheduledTransaction.contractCallOrThrow());
                case CONTRACT_DELETE_INSTANCE -> ordinary.contractDeleteInstance(
                        scheduledTransaction.contractDeleteInstanceOrThrow());
                case SYSTEM_DELETE -> ordinary.systemDelete(scheduledTransaction.systemDeleteOrThrow());
                case SYSTEM_UNDELETE -> ordinary.systemUndelete(scheduledTransaction.systemUndeleteOrThrow());
                case FREEZE -> ordinary.freeze(scheduledTransaction.freezeOrThrow());
                case TOKEN_CREATION -> ordinary.tokenCreation(scheduledTransaction.tokenCreationOrThrow());
                case TOKEN_FREEZE -> ordinary.tokenFreeze(scheduledTransaction.tokenFreezeOrThrow());
                case TOKEN_UNFREEZE -> ordinary.tokenUnfreeze(scheduledTransaction.tokenUnfreezeOrThrow());
                case TOKEN_GRANT_KYC -> ordinary.tokenGrantKyc(scheduledTransaction.tokenGrantKycOrThrow());
                case TOKEN_REVOKE_KYC -> ordinary.tokenRevokeKyc(scheduledTransaction.tokenRevokeKycOrThrow());
                case TOKEN_DELETION -> ordinary.tokenDeletion(scheduledTransaction.tokenDeletionOrThrow());
                case TOKEN_UPDATE -> ordinary.tokenUpdate(scheduledTransaction.tokenUpdateOrThrow());
                case TOKEN_MINT -> ordinary.tokenMint(scheduledTransaction.tokenMintOrThrow());
                case TOKEN_BURN -> ordinary.tokenBurn(scheduledTransaction.tokenBurnOrThrow());
                case TOKEN_WIPE -> ordinary.tokenWipe(scheduledTransaction.tokenWipeOrThrow());
                case TOKEN_ASSOCIATE -> ordinary.tokenAssociate(scheduledTransaction.tokenAssociateOrThrow());
                case TOKEN_DISSOCIATE -> ordinary.tokenDissociate(scheduledTransaction.tokenDissociateOrThrow());
                case SCHEDULE_DELETE -> ordinary.scheduleDelete(scheduledTransaction.scheduleDeleteOrThrow());
                case TOKEN_PAUSE -> ordinary.tokenPause(scheduledTransaction.tokenPauseOrThrow());
                case TOKEN_UNPAUSE -> ordinary.tokenUnpause(scheduledTransaction.tokenUnpauseOrThrow());
                case CRYPTO_APPROVE_ALLOWANCE -> ordinary.cryptoApproveAllowance(
                        scheduledTransaction.cryptoApproveAllowanceOrThrow());
                case CRYPTO_DELETE_ALLOWANCE -> ordinary.cryptoDeleteAllowance(
                        scheduledTransaction.cryptoDeleteAllowanceOrThrow());
                case TOKEN_FEE_SCHEDULE_UPDATE -> ordinary.tokenFeeScheduleUpdate(
                        scheduledTransaction.tokenFeeScheduleUpdateOrThrow());
                case UTIL_PRNG -> ordinary.utilPrng(scheduledTransaction.utilPrngOrThrow());
                case UNSET -> throw new HandleException(ResponseCodeEnum.INVALID_TRANSACTION);
            }
        }
        return ordinary.build();
    }

    static HederaFunctionality functionalityForType(final DataOneOfType transactionType) {
        return switch (transactionType) {
            case CONSENSUS_CREATE_TOPIC -> HederaFunctionality.CONSENSUS_CREATE_TOPIC;
            case CONSENSUS_UPDATE_TOPIC -> HederaFunctionality.CONSENSUS_UPDATE_TOPIC;
            case CONSENSUS_DELETE_TOPIC -> HederaFunctionality.CONSENSUS_DELETE_TOPIC;
            case CONSENSUS_SUBMIT_MESSAGE -> HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE;
            case CRYPTO_CREATE_ACCOUNT -> HederaFunctionality.CRYPTO_CREATE;
            case CRYPTO_UPDATE_ACCOUNT -> HederaFunctionality.CRYPTO_UPDATE;
            case CRYPTO_TRANSFER -> HederaFunctionality.CRYPTO_TRANSFER;
            case CRYPTO_DELETE -> HederaFunctionality.CRYPTO_DELETE;
            case FILE_CREATE -> HederaFunctionality.FILE_CREATE;
            case FILE_APPEND -> HederaFunctionality.FILE_APPEND;
            case FILE_UPDATE -> HederaFunctionality.FILE_UPDATE;
            case FILE_DELETE -> HederaFunctionality.FILE_DELETE;
            case SYSTEM_DELETE -> HederaFunctionality.SYSTEM_DELETE;
            case SYSTEM_UNDELETE -> HederaFunctionality.SYSTEM_UNDELETE;
            case CONTRACT_CREATE_INSTANCE -> HederaFunctionality.CONTRACT_CREATE;
            case CONTRACT_UPDATE_INSTANCE -> HederaFunctionality.CONTRACT_UPDATE;
            case CONTRACT_CALL -> HederaFunctionality.CONTRACT_CALL;
            case CONTRACT_DELETE_INSTANCE -> HederaFunctionality.CONTRACT_DELETE;
            case FREEZE -> HederaFunctionality.FREEZE;
            case TOKEN_CREATION -> HederaFunctionality.TOKEN_CREATE;
            case TOKEN_FREEZE -> HederaFunctionality.TOKEN_FREEZE_ACCOUNT;
            case TOKEN_UNFREEZE -> HederaFunctionality.TOKEN_UNFREEZE_ACCOUNT;
            case TOKEN_GRANT_KYC -> HederaFunctionality.TOKEN_GRANT_KYC_TO_ACCOUNT;
            case TOKEN_REVOKE_KYC -> HederaFunctionality.TOKEN_REVOKE_KYC_FROM_ACCOUNT;
            case TOKEN_DELETION -> HederaFunctionality.TOKEN_DELETE;
            case TOKEN_UPDATE -> HederaFunctionality.TOKEN_UPDATE;
            case TOKEN_MINT -> HederaFunctionality.TOKEN_MINT;
            case TOKEN_BURN -> HederaFunctionality.TOKEN_BURN;
            case TOKEN_WIPE -> HederaFunctionality.TOKEN_ACCOUNT_WIPE;
            case TOKEN_ASSOCIATE -> HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT;
            case TOKEN_DISSOCIATE -> HederaFunctionality.TOKEN_DISSOCIATE_FROM_ACCOUNT;
            case SCHEDULE_DELETE -> HederaFunctionality.SCHEDULE_DELETE;
            case TOKEN_PAUSE -> HederaFunctionality.TOKEN_PAUSE;
            case TOKEN_UNPAUSE -> HederaFunctionality.TOKEN_UNPAUSE;
            case CRYPTO_APPROVE_ALLOWANCE -> HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE;
            case CRYPTO_DELETE_ALLOWANCE -> HederaFunctionality.CRYPTO_DELETE_ALLOWANCE;
            case TOKEN_FEE_SCHEDULE_UPDATE -> HederaFunctionality.TOKEN_FEE_SCHEDULE_UPDATE;
            case UTIL_PRNG -> HederaFunctionality.UTIL_PRNG;
            case UNSET -> HederaFunctionality.NONE;
        };
    }

    /**
     * Given a Schedule, return a copy of that schedule with the executed flag and resolution time set.
     * @param schedule a {@link Schedule} to mark executed.
     * @param consensusTime the current consensus time, used to set {@link Schedule#resolutionTime()}.
     * @return a new Schedule which matches the input, except that the execute flag is set and the resolution time
     *     is set to the consensusTime provided.
     */
    @NonNull
    static Schedule markExecuted(@NonNull final Schedule schedule, @NonNull final Instant consensusTime) {
        final Timestamp consensusTimestamp = new Timestamp(consensusTime.getEpochSecond(), consensusTime.getNano());
        return new Schedule(
                schedule.scheduleId(),
                schedule.deleted(),
                true,
                schedule.waitForExpiry(),
                schedule.memo(),
                schedule.schedulerAccountId(),
                schedule.payerAccountId(),
                schedule.adminKey(),
                schedule.scheduleValidStart(),
                schedule.providedExpirationSecond(),
                schedule.calculatedExpirationSecond(),
                consensusTimestamp,
                schedule.scheduledTransaction(),
                schedule.originalCreateTransaction(),
                schedule.signatories());
    }

    @NonNull
    static Schedule replaceSignatories(@NonNull final Schedule schedule, @NonNull final Set<Key> newSignatories) {
        return new Schedule(
                schedule.scheduleId(),
                schedule.deleted(),
                schedule.executed(),
                schedule.waitForExpiry(),
                schedule.memo(),
                schedule.schedulerAccountId(),
                schedule.payerAccountId(),
                schedule.adminKey(),
                schedule.scheduleValidStart(),
                schedule.providedExpirationSecond(),
                schedule.calculatedExpirationSecond(),
                schedule.resolutionTime(),
                schedule.scheduledTransaction(),
                schedule.originalCreateTransaction(),
                List.copyOf(newSignatories));
    }

    @NonNull
    static Schedule replaceSignatoriesAndMarkExecuted(
            @NonNull final Schedule schedule,
            @NonNull final Set<Key> newSignatories,
            @NonNull final Instant consensusTime) {
        final Timestamp consensusTimestamp = new Timestamp(consensusTime.getEpochSecond(), consensusTime.getNano());
        return new Schedule(
                schedule.scheduleId(),
                schedule.deleted(),
                true,
                schedule.waitForExpiry(),
                schedule.memo(),
                schedule.schedulerAccountId(),
                schedule.payerAccountId(),
                schedule.adminKey(),
                schedule.scheduleValidStart(),
                schedule.providedExpirationSecond(),
                schedule.calculatedExpirationSecond(),
                consensusTimestamp,
                schedule.scheduledTransaction(),
                schedule.originalCreateTransaction(),
                List.copyOf(newSignatories));
    }

    /**
     * Create a new Schedule, but without an ID or signatories.
     * This method is used to create a schedule object for processing during a ScheduleCreate, but without the
     * schedule ID, as we still need to complete validation and other processing.  Once all processing is complete,
     * a new ID is allocated and signatories are added immediately prior to storing the new object in state.
     * @param currentTransaction The transaction body of the current Schedule Create transaction.  We assume that
     *     the transaction is a ScheduleCreate, but require the less specific object so that we have access to
     *     the transaction ID via {@link TransactionBody#transactionID()} from the TransactionBody stored in
     *     the {@link Schedule#originalCreateTransaction()} attribute of the Schedule.
     * @param currentConsensusTime The current consensus time for the network.
     * @param maxLifeSeconds The maximum number of seconds a schedule is permitted to exist on the ledger
     *     before it expires.
     * @return a newly created Schedule with a null schedule ID.
     * @throws HandleException if the
     */
    @NonNull
    static Schedule createProvisionalSchedule(
            @NonNull final TransactionBody currentTransaction,
            @NonNull final Instant currentConsensusTime,
            final long maxLifeSeconds)
            throws HandleException {
        // The next three items will never be null, but Sonar is persnickety, so we force NPE if any are null.
        final TransactionID parentTransactionId = currentTransaction.transactionIDOrThrow();
        final ScheduleCreateTransactionBody createTransaction = currentTransaction.scheduleCreateOrThrow();
        final AccountID schedulerAccount = parentTransactionId.accountIDOrThrow();
        final Timestamp providedExpirationTime = createTransaction.expirationTime();
        final long calculatedExpirationTime =
                calculateExpiration(providedExpirationTime, currentConsensusTime, maxLifeSeconds);
        final ScheduleID nullId = null;

        Schedule.Builder builder = Schedule.newBuilder();
        builder.scheduleId(nullId).deleted(false).executed(false);
        builder.waitForExpiry(createTransaction.waitForExpiry());
        builder.adminKey(createTransaction.adminKey()).schedulerAccountId(parentTransactionId.accountID());
        builder.payerAccountId(createTransaction.payerAccountIDOrElse(schedulerAccount));
        builder.schedulerAccountId(schedulerAccount);
        builder.scheduleValidStart(parentTransactionId.transactionValidStart());
        builder.calculatedExpirationSecond(calculatedExpirationTime);
        builder.originalCreateTransaction(currentTransaction);
        builder.memo(createTransaction.memo());
        builder.scheduledTransaction(createTransaction.scheduledTransactionBody());
        return builder.build();
    }

    @NonNull
    static Schedule completeProvisionalSchedule(
            @NonNull final Schedule provisionalSchedule,
            final long newEntityNumber,
            @NonNull final Set<Key> finalSignatories)
            throws HandleException {
        final TransactionBody originalTransaction = provisionalSchedule.originalCreateTransactionOrThrow();
        final TransactionID parentTransactionId = originalTransaction.transactionIDOrThrow();
        final ScheduleID finalId = getNextScheduleID(parentTransactionId, newEntityNumber);

        Schedule.Builder build = Schedule.newBuilder();
        build.scheduleId(finalId).deleted(false).executed(false);
        build.waitForExpiry(provisionalSchedule.waitForExpiry());
        build.adminKey(provisionalSchedule.adminKey()).schedulerAccountId(parentTransactionId.accountID());
        build.payerAccountId(provisionalSchedule.payerAccountId());
        build.schedulerAccountId(provisionalSchedule.schedulerAccountId());
        build.scheduleValidStart(provisionalSchedule.scheduleValidStart());
        build.calculatedExpirationSecond(provisionalSchedule.calculatedExpirationSecond());
        build.originalCreateTransaction(provisionalSchedule.originalCreateTransaction());
        build.memo(provisionalSchedule.memo());
        build.scheduledTransaction(provisionalSchedule.scheduledTransaction());
        build.signatories(List.copyOf(finalSignatories));
        return build.build();
    }

    @NonNull
    static ScheduleID getNextScheduleID(
            @NonNull final TransactionID parentTransactionId, final long newScheduleNumber) {
        final AccountID schedulingAccount = parentTransactionId.accountIDOrThrow();
        final long shardNumber = schedulingAccount.shardNum();
        final long reamlNumber = schedulingAccount.realmNum();
        final Builder builder = ScheduleID.newBuilder().shardNum(shardNumber).realmNum(reamlNumber);
        builder.scheduleNum(newScheduleNumber);
        return builder.build();
    }

    @NonNull
    static TransactionID transactionIdForScheduled(@NonNull Schedule valueInState) {
        // original create transaction and its transaction ID will never be null, but Sonar...
        final TransactionBody originalTransaction = valueInState.originalCreateTransactionOrThrow();
        final TransactionID parentTransactionId = originalTransaction.transactionIDOrThrow();
        // payer on parent transaction ID will also never be null...
        final AccountID payerAccount = valueInState.payerAccountIdOrElse(parentTransactionId.accountIDOrThrow());
        // Scheduled transaction ID is the same as its parent except
        //     if scheduled is set true, payer *might* be modified, and the nonce is incremented.
        final TransactionID.Builder builder = TransactionID.newBuilder().accountID(payerAccount);
        builder.transactionValidStart(parentTransactionId.transactionValidStart());
        builder.scheduled(true).nonce(parentTransactionId.nonce() + 1);
        return builder.build();
    }

    private static long calculateExpiration(
            @Nullable final Timestamp givenExpiration,
            @NonNull final Instant currentConsensusTime,
            final long maxLifeSeconds) {
        if (givenExpiration != null) {
            return givenExpiration.seconds();
        } else {
            final Instant currentPlusMaxLife = currentConsensusTime.plusSeconds(maxLifeSeconds);
            return currentPlusMaxLife.getEpochSecond();
        }
    }
}
