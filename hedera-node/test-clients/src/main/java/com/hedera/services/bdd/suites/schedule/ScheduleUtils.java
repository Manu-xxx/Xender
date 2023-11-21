/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.schedule;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody.Builder;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public final class ScheduleUtils {
    static final String ACCOUNT = "civilian";
    static final String ADMIN = "admin";
    static final String A_SCHEDULE = "validSchedule";
    static final String A_SENDER_TXN = "aSenderTxn";
    static final String A_TOKEN = "token";
    static final String BASIC_XFER = "basicXfer";
    static final String BEFORE = "before";
    static final String BEGIN = "begin";
    static final String CONTINUE = "continue";
    static final String COPYCAT = "copycat";
    static final String CREATE_TX = "createTx";
    static final String CREATE_TXN = "createTx";
    static final String CREATION = "creation";
    static final String DEFERRED_CREATION = "deferredCreation";
    static final String DEFERRED_FALL = "deferredFall";
    static final String DEFERRED_XFER = "deferredXfer";
    static final String DESIGNATING_PAYER = "1.2.3";
    static final String ENTITY_MEMO = "This was Mr. Bleaney's room. He stayed";
    static final String EXTRA_KEY = "extraKey";
    static final String FAILED_XFER = "failedXfer";
    static final String FAILING_TXN = "failingTxn";
    static final String FIRST_PAYER = "firstPayer";
    static final String INSOLVENT_PAYER = "insolventPayer";
    static final String LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS = "ledger.schedule.txExpiryTimeSecs";
    static final String LEDGER_TOKEN_TRANSFERS_MAX_LEN = "ledger.tokenTransfers.maxLen";
    static final String LEDGER_TRANSFERS_MAX_LEN = "ledger.transfers.maxLen";
    static final String LUCKY_RECEIVER = "luckyReceiver";
    static final String NEVER_TO_BE = "neverToBe";
    static final String NEW_SENDER_KEY = "newSKey";
    static final String NEW_SKEY = "newSKey";
    static final String ONLY_BODY = "onlyBody";
    static final String ONLY_BODY_AND_ADMIN_KEY = "onlyBodyAndAdminKey";
    static final String ONLY_BODY_AND_MEMO = "onlyBodyAndMemo";
    static final String ONLY_BODY_AND_PAYER = "onlyBodyAndPayer";
    static final String ORIGINAL = "original";
    static final String OTHER_PAYER = "otherPayer";
    static final String PAYER = "payer";
    static final String PAYER_TXN = "payerTxn";
    static final String PAYING_ACCOUNT = "payingAccount";
    static final String PAYING_ACCOUNT_2 = "payingAccount2";
    static final String PAYING_ACCOUNT_TXN = "payingAccountTxn";
    static final String PAYING_SENDER = "payingSender";
    static final String RANDOM_KEY = "randomKey";
    static final String RANDOM_MSG =
            "Little did they care who danced between / And little she by whom her dance was seen";
    static final String RECEIVER = "receiver";
    static final String RECEIVER_A = "receiverA";
    static final String RECEIVER_B = "receiverB";
    static final String RECEIVER_C = "receiverC";
    static final String SCHEDULE = "schedule";
    static final String SCHEDULE_CREATE_FEE = "scheduleCreateFee";
    static final String SCHEDULE_PAYER = "schedulePayer";
    static final String SCHEDULING_LONG_TERM_ENABLED = "scheduling.longTermEnabled";
    static final String SCHEDULING_MAX_TXN_PER_SECOND = "scheduling.maxTxnPerSecond";
    static final String SCHEDULING_WHITELIST = "scheduling.whitelist";
    static final String SECOND_PAYER = "secondPayer";
    static final String SENDER = "sender";
    static final String SENDER_1 = "sender1";
    static final String SENDER_2 = "sender2";
    static final String SENDER_3 = "sender3";
    static final String SENDER_TXN = "senderTxn";
    static final String SHARED_KEY = "sharedKey";
    static final String SIGN_TX = "signTx";
    static final String SIGN_TXN = "signTx";
    static final String SIMPLE_UPDATE = "SimpleUpdate";
    static final String SIMPLE_XFER_SCHEDULE = "simpleXferSchedule";
    static final String SOMEBODY = "somebody";
    static final String SOMEBODY_PAYER = "somebody";
    static final String STAKING_FEES_NODE_REWARD_PERCENTAGE = "staking.fees.nodeRewardPercentage";
    static final String STAKING_FEES_STAKING_REWARD_PERCENTAGE = "staking.fees.stakingRewardPercentage";
    static final String SUCCESS_TXN = "successTxn";
    static final String SUPPLY_KEY = "supplyKey";
    static final String THREE_SIG_XFER = "threeSigXfer";
    static final String TOKENS_NFTS_ARE_ENABLED = "tokens.nfts.areEnabled";
    static final String TOKENS_NFTS_MAX_BATCH_SIZE_MINT = "tokens.nfts.maxBatchSizeMint";
    static final String TOKEN_A = "tokenA";
    static final String TRANSACTION_NOT_SCHEDULED = "Transaction not scheduled!";
    static final String TREASURY = "treasury";
    static final String TRIGGER = "trigger";
    static final String TRIGGERING_TXN = "triggeringTxn";
    static final String TWO_SIG_XFER = "twoSigXfer";
    static final String UNWILLING_PAYER = "unwillingPayer";
    static final String VALID_SCHEDULE = "validSchedule";
    static final String VALID_SCHEDULED_TXN = "validScheduledTxn";
    static final String WEIRDLY_POPULAR_KEY = "weirdlyPopularKey";
    static final String WEIRDLY_POPULAR_KEY_TXN = "weirdlyPopularKeyTxn";

    static final byte[] ORIG_FILE = "SOMETHING".getBytes();
    static final int SCHEDULE_EXPIRY_TIME_SECS = 10;
    static final int SCHEDULE_EXPIRY_TIME_MS = SCHEDULE_EXPIRY_TIME_SECS * 1000;

    static final String SCHEDULED_TRANSACTION_MUST_NOT_SUCCEED = "Scheduled transaction must not succeed";
    static final String SCHEDULED_TRANSACTION_MUST_SUCCEED = "Scheduled transaction must succeed";
    static final String SCHEDULED_TRANSACTION_SHOULD_NOT_BE_SUCCESSFUL =
            "Scheduled transaction should not be successful!";
    static final String SCHEDULED_TRANSACTION_SHOULD_SUCCEED = "Scheduled transaction should succeed.";
    static final String WRONG_CONSENSUS_TIMESTAMP = "Wrong consensus timestamp!";
    static final String WRONG_RECORD_ACCOUNT_ID = "Wrong record account ID!";
    static final String WRONG_SCHEDULE_ID = "Wrong schedule ID!";
    static final String WRONG_TRANSACTION_VALID_START = "Wrong transaction valid start!";
    static final String WRONG_TRANSFER_LIST = "Wrong transfer list!";

    static final String DEFAULT_LONG_TERM_ENABLED =
            HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_LONG_TERM_ENABLED);
    static final String DEFAULT_MAX_BATCH_SIZE_MINT =
            HapiSpecSetup.getDefaultNodeProps().get(TOKENS_NFTS_MAX_BATCH_SIZE_MINT);
    static final String DEFAULT_MAX_TOKEN_TRANSFER_LEN =
            HapiSpecSetup.getDefaultNodeProps().get(LEDGER_TOKEN_TRANSFERS_MAX_LEN);
    static final String DEFAULT_MAX_TRANSFER_LEN =
            HapiSpecSetup.getDefaultNodeProps().get(LEDGER_TRANSFERS_MAX_LEN);
    static final String DEFAULT_TX_EXPIRY =
            HapiSpecSetup.getDefaultNodeProps().get(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS);
    static final String WHITELIST_DEFAULT = HapiSpecSetup.getDefaultNodeProps().get(SCHEDULING_WHITELIST);

    /**
     * Whitelist containing all of the non-query type transactions so we don't hit whitelist failures
     * everywhere.  Recommended for most specs that override whitelist.
     */
    static final String FULL_WHITELIST =
            """
            ConsensusCreateTopic,ConsensusDeleteTopic,ConsensusSubmitMessage,ConsensusUpdateTopic,\
            ContractAutoRenew,ContractCall,ContractCallLocal,ContractCreate,ContractDelete,\
            ContractUpdate,CreateTransactionRecord,CryptoAccountAutoRenew,CryptoAddLiveHash,\
            CryptoApproveAllowance,CryptoCreate,CryptoDelete,CryptoDeleteAllowance,CryptoDeleteLiveHash,\
            CryptoTransfer,CryptoUpdate,EthereumTransaction,FileAppend,FileCreate,FileDelete,FileUpdate,\
            Freeze,SystemDelete,SystemUndelete,TokenAccountWipe,TokenAssociateToAccount,TokenBurn,\
            TokenCreate,TokenDelete,TokenDissociateFromAccount,TokenFeeScheduleUpdate,TokenFreezeAccount,\
            TokenGrantKycToAccount,TokenMint,TokenPause,TokenRevokeKycFromAccount,TokenUnfreezeAccount,\
            TokenUnpause,TokenUpdate,UtilPrng""";
    /**
     * A very small whitelist containing just the transactions needed for SecheduleExecutionSpecs because
     * that suite has to override the whitelist on every single spec due to some sort of ordering issue.
     */
    static final String WHITELIST_MINIMUM =
            "ConsensusSubmitMessage,ContractCall,CryptoCreate,CryptoTransfer,FileUpdate,SystemDelete,TokenBurn,TokenMint,Freeze";
    /**
     * A whitelist guaranteed to contain every transaction type possible.  Useful for specs that need to test scheduling
     * a transaction that shouldn't work (e.g. a query).
     */
    static final String WHITELIST_ALL = getWhitelistAll();

    private ScheduleUtils() {}

    public static SchedulableTransactionBody fromOrdinary(TransactionBody txn) {
        Builder scheduleBuilder = SchedulableTransactionBody.newBuilder();
        scheduleBuilder.setTransactionFee(txn.getTransactionFee());
        scheduleBuilder.setMemo(txn.getMemo());

        if (txn.hasContractCall()) {
            scheduleBuilder.setContractCall(txn.getContractCall());
        } else if (txn.hasContractCreateInstance()) {
            scheduleBuilder.setContractCreateInstance(txn.getContractCreateInstance());
        } else if (txn.hasContractUpdateInstance()) {
            scheduleBuilder.setContractUpdateInstance(txn.getContractUpdateInstance());
        } else if (txn.hasContractDeleteInstance()) {
            scheduleBuilder.setContractDeleteInstance(txn.getContractDeleteInstance());
        } else if (txn.hasCryptoCreateAccount()) {
            scheduleBuilder.setCryptoCreateAccount(txn.getCryptoCreateAccount());
        } else if (txn.hasCryptoDelete()) {
            scheduleBuilder.setCryptoDelete(txn.getCryptoDelete());
        } else if (txn.hasCryptoTransfer()) {
            scheduleBuilder.setCryptoTransfer(txn.getCryptoTransfer());
        } else if (txn.hasCryptoUpdateAccount()) {
            scheduleBuilder.setCryptoUpdateAccount(txn.getCryptoUpdateAccount());
        } else if (txn.hasFileAppend()) {
            scheduleBuilder.setFileAppend(txn.getFileAppend());
        } else if (txn.hasFileCreate()) {
            scheduleBuilder.setFileCreate(txn.getFileCreate());
        } else if (txn.hasFileDelete()) {
            scheduleBuilder.setFileDelete(txn.getFileDelete());
        } else if (txn.hasFileUpdate()) {
            scheduleBuilder.setFileUpdate(txn.getFileUpdate());
        } else if (txn.hasSystemDelete()) {
            scheduleBuilder.setSystemDelete(txn.getSystemDelete());
        } else if (txn.hasSystemUndelete()) {
            scheduleBuilder.setSystemUndelete(txn.getSystemUndelete());
        } else if (txn.hasFreeze()) {
            scheduleBuilder.setFreeze(txn.getFreeze());
        } else if (txn.hasConsensusCreateTopic()) {
            scheduleBuilder.setConsensusCreateTopic(txn.getConsensusCreateTopic());
        } else if (txn.hasConsensusUpdateTopic()) {
            scheduleBuilder.setConsensusUpdateTopic(txn.getConsensusUpdateTopic());
        } else if (txn.hasConsensusDeleteTopic()) {
            scheduleBuilder.setConsensusDeleteTopic(txn.getConsensusDeleteTopic());
        } else if (txn.hasConsensusSubmitMessage()) {
            scheduleBuilder.setConsensusSubmitMessage(txn.getConsensusSubmitMessage());
        } else if (txn.hasTokenCreation()) {
            scheduleBuilder.setTokenCreation(txn.getTokenCreation());
        } else if (txn.hasTokenFreeze()) {
            scheduleBuilder.setTokenFreeze(txn.getTokenFreeze());
        } else if (txn.hasTokenUnfreeze()) {
            scheduleBuilder.setTokenUnfreeze(txn.getTokenUnfreeze());
        } else if (txn.hasTokenGrantKyc()) {
            scheduleBuilder.setTokenGrantKyc(txn.getTokenGrantKyc());
        } else if (txn.hasTokenRevokeKyc()) {
            scheduleBuilder.setTokenRevokeKyc(txn.getTokenRevokeKyc());
        } else if (txn.hasTokenDeletion()) {
            scheduleBuilder.setTokenDeletion(txn.getTokenDeletion());
        } else if (txn.hasTokenUpdate()) {
            scheduleBuilder.setTokenUpdate(txn.getTokenUpdate());
        } else if (txn.hasTokenMint()) {
            scheduleBuilder.setTokenMint(txn.getTokenMint());
        } else if (txn.hasTokenBurn()) {
            scheduleBuilder.setTokenBurn(txn.getTokenBurn());
        } else if (txn.hasTokenWipe()) {
            scheduleBuilder.setTokenWipe(txn.getTokenWipe());
        } else if (txn.hasTokenAssociate()) {
            scheduleBuilder.setTokenAssociate(txn.getTokenAssociate());
        } else if (txn.hasTokenDissociate()) {
            scheduleBuilder.setTokenDissociate(txn.getTokenDissociate());
        } else if (txn.hasScheduleDelete()) {
            scheduleBuilder.setScheduleDelete(txn.getScheduleDelete());
        } else if (txn.hasCryptoApproveAllowance()) {
            scheduleBuilder.setCryptoApproveAllowance(txn.getCryptoApproveAllowance());
        }
        return scheduleBuilder.build();
    }

    private static String getWhitelistAll() {
        final List<String> whitelistNames = new LinkedList<>();
        for (final HederaFunctionality enumValue : HederaFunctionality.values()) {
            if (enumValue != HederaFunctionality.NONE) whitelistNames.add(enumValue.protoName());
        }
        Collections.sort(whitelistNames); // make things easier to read
        final String whitelistAll = String.join(",", whitelistNames);
        return whitelistAll;
    }
}
