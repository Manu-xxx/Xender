/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.leaky;

import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadSingleInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThree;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.resetToDefault;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.ACCOUNT_INFO;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.ACCOUNT_INFO_AFTER_CALL;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CALL_TX;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CALL_TX_REC;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACTS_MAX_GAS_PER_SEC;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.CONTRACT_FROM;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.DEFAULT_MAX_AUTO_RENEW_PERIOD;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.DEPOSIT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.PAY_RECEIVABLE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.SIMPLE_UPDATE_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.TRANSFERRING_CONTRACT;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCallSuite.TRANSFER_TO_CALLER;
import static com.hedera.services.bdd.suites.contract.hapi.ContractCreateSuite.EMPTY_CONSTRUCTOR_CONTRACT;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.ADMIN_KEY;
import static com.hedera.services.bdd.suites.ethereum.EthereumSuite.GAS_LIMIT;
import static com.hedera.services.bdd.suites.token.TokenTransactSpecs.TRANSFER_TXN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.fee.FeeBuilder;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

public class LeakyContractTestsSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(LeakyContractTestsSuite.class);
    public static final String CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1 =
            "contracts.maxRefundPercentOfGasLimit";
    public static final String CREATE_TX = "createTX";
    public static final String CREATE_TX_REC = "createTXRec";

    public static void main(String... args) {
        new LeakyContractTestsSuite().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(
                transferToCaller(),
                resultSizeAffectsFees(),
                propagatesNestedCreations(),
                temporarySStoreRefundTest(),
                canCallPendingContractSafely(),
                transferZeroHbarsToCaller(),
                deletedContractsCannotBeUpdated(),
                autoAssociationSlotsAppearsInInfo(),
                gasLimitOverMaxGasLimitFailsPrecheck(),
                createMinChargeIsTXGasUsedByContractCreate(),
                createGasLimitOverMaxGasLimitFailsPrecheck(),
                contractCreationStoragePriceMatchesFinalExpiry(),
                maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller(),
                accountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation(),
                createMaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller());
    }

    HapiSpec accountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation() {
        final String ACCOUNT = "account";
        return defaultHapiSpec(
                        "ETX_026_accountWithoutAliasCanMakeEthTxnsDueToAutomaticAliasCreation")
                .given(
                        overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, FALSE_VALUE),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(ACCOUNT).key(SECP_256K1_SOURCE_KEY).balance(ONE_HUNDRED_HBARS))
                .when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(ACCOUNT)
                                .maxGasAllowance(FIVE_HBARS)
                                .nonce(0)
                                .gasLimit(GAS_LIMIT)
                                .hasKnownStatus(INVALID_ACCOUNT_ID))
                .then(overriding(CRYPTO_CREATE_WITH_ALIAS_ENABLED, "true"));
    }

    private HapiSpec transferToCaller() {
        final var transferTxn = TRANSFER_TXN;
        return defaultHapiSpec(TRANSFER_TO_CALLER)
                .given(
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
                        getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                .savingSnapshot(ACCOUNT_INFO)
                                .payingWith(GENESIS))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_CALLER,
                                                            BigInteger.valueOf(10))
                                                    .payingWith(DEFAULT_CONTRACT_SENDER)
                                                    .via(transferTxn)
                                                    .logged();

                                    var saveTxnRecord =
                                            getTxnRecord(transferTxn)
                                                    .saveTxnRecordToRegistry("txn")
                                                    .payingWith(GENESIS);
                                    var saveAccountInfoAfterCall =
                                            getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                                    .savingSnapshot(ACCOUNT_INFO_AFTER_CALL)
                                                    .payingWith(GENESIS);
                                    var saveContractInfo =
                                            getContractInfo(TRANSFERRING_CONTRACT)
                                                    .saveToRegistry(CONTRACT_FROM);

                                    allRunFor(
                                            spec,
                                            transferCall,
                                            saveTxnRecord,
                                            saveAccountInfoAfterCall,
                                            saveContractInfo);
                                }))
                .then(
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var fee =
                                            spec.registry()
                                                    .getTransactionRecord("txn")
                                                    .getTransactionFee();
                                    final var accountBalanceBeforeCall =
                                            spec.registry()
                                                    .getAccountInfo(ACCOUNT_INFO)
                                                    .getBalance();
                                    final var accountBalanceAfterCall =
                                            spec.registry()
                                                    .getAccountInfo(ACCOUNT_INFO_AFTER_CALL)
                                                    .getBalance();
                                    assertEquals(
                                            accountBalanceAfterCall,
                                            accountBalanceBeforeCall - fee + 10L);
                                }),
                        sourcing(
                                () ->
                                        getContractInfo(TRANSFERRING_CONTRACT)
                                                .has(contractWith().balance(10_000L - 10L))));
    }

    private HapiSpec maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
        return defaultHapiSpec("MaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, "5"),
                        uploadInitCode(SIMPLE_UPDATE_CONTRACT))
                .when(
                        contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
                        contractCall(
                                        SIMPLE_UPDATE_CONTRACT,
                                        "set",
                                        BigInteger.valueOf(5),
                                        BigInteger.valueOf(42))
                                .gas(300_000L)
                                .via(CALL_TX))
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop01 =
                                            getTxnRecord(CALL_TX)
                                                    .saveTxnRecordToRegistry(CALL_TX_REC);
                                    allRunFor(spec, subop01);

                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(CALL_TX_REC)
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    assertEquals(285000, gasUsed);
                                }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT));
    }

    @SuppressWarnings("java:S5960")
    private HapiSpec contractCreationStoragePriceMatchesFinalExpiry() {
        final var toyMaker = "ToyMaker";
        final var createIndirectly = "CreateIndirectly";
        final var normalPayer = "normalPayer";
        final var longLivedPayer = "longLivedPayer";
        final var longLifetime = 100 * 7776000L;
        final AtomicLong normalPayerGasUsed = new AtomicLong();
        final AtomicLong longLivedPayerGasUsed = new AtomicLong();
        final AtomicReference<String> toyMakerMirror = new AtomicReference<>();

        return defaultHapiSpec("ContractCreationStoragePriceMatchesFinalExpiry")
                .given(
                        overriding(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION, "" + longLifetime),
                        cryptoCreate(normalPayer),
                        cryptoCreate(longLivedPayer).autoRenewSecs(longLifetime),
                        uploadInitCode(toyMaker, createIndirectly),
                        contractCreate(toyMaker)
                                .exposingNumTo(
                                        num ->
                                                toyMakerMirror.set(
                                                        asHexedSolidityAddress(0, 0, num))),
                        sourcing(
                                () ->
                                        contractCreate(createIndirectly)
                                                .autoRenewSecs(longLifetime)
                                                .payingWith(GENESIS)))
                .when(
                        contractCall(toyMaker, "make")
                                .payingWith(normalPayer)
                                .exposingGasTo(
                                        (status, gasUsed) -> normalPayerGasUsed.set(gasUsed)),
                        contractCall(toyMaker, "make")
                                .payingWith(longLivedPayer)
                                .exposingGasTo(
                                        (status, gasUsed) -> longLivedPayerGasUsed.set(gasUsed)),
                        assertionsHold(
                                (spec, opLog) ->
                                        assertEquals(
                                                normalPayerGasUsed.get(),
                                                longLivedPayerGasUsed.get(),
                                                "Payer expiry should not affect create storage"
                                                        + " cost")),
                        // Verify that we are still charged a "typical" amount despite the payer and
                        // the original sender contract having extremely long expiry dates
                        sourcing(
                                () ->
                                        contractCall(
                                                        createIndirectly,
                                                        "makeOpaquely",
                                                        asHeadlongAddress(toyMakerMirror.get()))
                                                .payingWith(longLivedPayer)))
                .then(
                        overriding(
                                LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION,
                                "" + DEFAULT_MAX_AUTO_RENEW_PERIOD));
    }

    private HapiSpec gasLimitOverMaxGasLimitFailsPrecheck() {
        return defaultHapiSpec("GasLimitOverMaxGasLimitFailsPrecheck")
                .given(
                        uploadInitCode(SIMPLE_UPDATE_CONTRACT),
                        contractCreate(SIMPLE_UPDATE_CONTRACT).gas(300_000L),
                        overriding(CONTRACTS_MAX_GAS_PER_SEC, "100"))
                .when()
                .then(
                        contractCall(
                                        SIMPLE_UPDATE_CONTRACT,
                                        "set",
                                        BigInteger.valueOf(5),
                                        BigInteger.valueOf(42))
                                .gas(101L)
                                .hasPrecheck(MAX_GAS_LIMIT_EXCEEDED),
                        resetToDefault(CONTRACTS_MAX_GAS_PER_SEC));
    }

    private HapiSpec createGasLimitOverMaxGasLimitFailsPrecheck() {
        return defaultHapiSpec("CreateGasLimitOverMaxGasLimitFailsPrecheck")
                .given(
                        overriding("contracts.maxGasPerSec", "100"),
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when()
                .then(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .gas(101L)
                                .hasPrecheck(MAX_GAS_LIMIT_EXCEEDED),
                        UtilVerbs.resetToDefault("contracts.maxGasPerSec"));
    }

    private HapiSpec transferZeroHbarsToCaller() {
        final var transferTxn = TRANSFER_TXN;
        return defaultHapiSpec("transferZeroHbarsToCaller")
                .given(
                        uploadInitCode(TRANSFERRING_CONTRACT),
                        contractCreate(TRANSFERRING_CONTRACT).balance(10_000L),
                        getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                .savingSnapshot(ACCOUNT_INFO)
                                .payingWith(GENESIS))
                .when(
                        withOpContext(
                                (spec, log) -> {
                                    var transferCall =
                                            contractCall(
                                                            TRANSFERRING_CONTRACT,
                                                            TRANSFER_TO_CALLER,
                                                            BigInteger.ZERO)
                                                    .payingWith(DEFAULT_CONTRACT_SENDER)
                                                    .via(transferTxn)
                                                    .logged();

                                    var saveTxnRecord =
                                            getTxnRecord(transferTxn)
                                                    .saveTxnRecordToRegistry("txn_registry")
                                                    .payingWith(GENESIS);
                                    var saveAccountInfoAfterCall =
                                            getAccountInfo(DEFAULT_CONTRACT_SENDER)
                                                    .savingSnapshot(ACCOUNT_INFO_AFTER_CALL)
                                                    .payingWith(GENESIS);
                                    var saveContractInfo =
                                            getContractInfo(TRANSFERRING_CONTRACT)
                                                    .saveToRegistry(CONTRACT_FROM);

                                    allRunFor(
                                            spec,
                                            transferCall,
                                            saveTxnRecord,
                                            saveAccountInfoAfterCall,
                                            saveContractInfo);
                                }))
                .then(
                        assertionsHold(
                                (spec, opLog) -> {
                                    final var fee =
                                            spec.registry()
                                                    .getTransactionRecord("txn_registry")
                                                    .getTransactionFee();
                                    final var accountBalanceBeforeCall =
                                            spec.registry()
                                                    .getAccountInfo(ACCOUNT_INFO)
                                                    .getBalance();
                                    final var accountBalanceAfterCall =
                                            spec.registry()
                                                    .getAccountInfo(ACCOUNT_INFO_AFTER_CALL)
                                                    .getBalance();
                                    final var contractBalanceAfterCall =
                                            spec.registry()
                                                    .getContractInfo(CONTRACT_FROM)
                                                    .getBalance();

                                    assertEquals(
                                            accountBalanceAfterCall,
                                            accountBalanceBeforeCall - fee);
                                    assertEquals(contractBalanceAfterCall, 10_000L);
                                }));
    }

    private HapiSpec resultSizeAffectsFees() {
        final var contract = "VerboseDeposit";
        final var TRANSFER_AMOUNT = 1_000L;
        BiConsumer<TransactionRecord, Logger> resultSizeFormatter =
                (rcd, txnLog) -> {
                    final var result = rcd.getContractCallResult();
                    txnLog.info(
                            "Contract call result FeeBuilder size = {}, fee = {}, result is"
                                    + " [self-reported size = {}, '{}']",
                            () -> FeeBuilder.getContractFunctionSize(result),
                            rcd::getTransactionFee,
                            result.getContractCallResult()::size,
                            result::getContractCallResult);
                    txnLog.info("  Literally :: {}", result);
                };

        return defaultHapiSpec("ResultSizeAffectsFees")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, "100"),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        contractCall(
                                        contract,
                                        DEPOSIT,
                                        TRANSFER_AMOUNT,
                                        0L,
                                        "So we out-danced thought...")
                                .via("noLogsCallTxn")
                                .sending(TRANSFER_AMOUNT),
                        contractCall(
                                        contract,
                                        DEPOSIT,
                                        TRANSFER_AMOUNT,
                                        5L,
                                        "So we out-danced thought...")
                                .via("loggedCallTxn")
                                .sending(TRANSFER_AMOUNT))
                .then(
                        assertionsHold(
                                (spec, assertLog) -> {
                                    HapiGetTxnRecord noLogsLookup =
                                            QueryVerbs.getTxnRecord("noLogsCallTxn")
                                                    .loggedWith(resultSizeFormatter);
                                    HapiGetTxnRecord logsLookup =
                                            QueryVerbs.getTxnRecord("loggedCallTxn")
                                                    .loggedWith(resultSizeFormatter);
                                    allRunFor(spec, noLogsLookup, logsLookup);
                                    final var unloggedRecord =
                                            noLogsLookup
                                                    .getResponse()
                                                    .getTransactionGetRecord()
                                                    .getTransactionRecord();
                                    final var loggedRecord =
                                            logsLookup
                                                    .getResponse()
                                                    .getTransactionGetRecord()
                                                    .getTransactionRecord();
                                    assertLog.info(
                                            "Fee for logged record   = {}",
                                            loggedRecord::getTransactionFee);
                                    assertLog.info(
                                            "Fee for unlogged record = {}",
                                            unloggedRecord::getTransactionFee);
                                    Assertions.assertNotEquals(
                                            unloggedRecord.getTransactionFee(),
                                            loggedRecord.getTransactionFee(),
                                            "Result size should change the txn fee!");
                                }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT));
    }

    private HapiSpec autoAssociationSlotsAppearsInInfo() {
        final int maxAutoAssociations = 100;
        final int ADVENTUROUS_NETWORK = 1_000;
        final String CONTRACT = "Multipurpose";
        final String associationsLimitProperty = "entities.limitTokenAssociations";
        final String defaultAssociationsLimit =
                HapiSpecSetup.getDefaultNodeProps().get(associationsLimitProperty);

        return defaultHapiSpec("autoAssociationSlotsAppearsInInfo")
                .given(
                        overridingThree(
                                "entities.limitTokenAssociations", "true",
                                "tokens.maxPerAccount", "" + 1,
                                "contracts.allowAutoAssociations", "true"))
                .when()
                .then(
                        newKeyNamed(ADMIN_KEY),
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT)
                                .adminKey(ADMIN_KEY)
                                .maxAutomaticTokenAssociations(maxAutoAssociations)
                                .hasPrecheck(
                                        REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT),

                        // Default is NOT to limit associations for entities
                        overriding(associationsLimitProperty, defaultAssociationsLimit),
                        contractCreate(CONTRACT)
                                .adminKey(ADMIN_KEY)
                                .maxAutomaticTokenAssociations(maxAutoAssociations),
                        getContractInfo(CONTRACT)
                                .has(
                                        ContractInfoAsserts.contractWith()
                                                .maxAutoAssociations(maxAutoAssociations))
                                .logged(),
                        // Restore default
                        overriding("tokens.maxPerAccount", "" + ADVENTUROUS_NETWORK));
    }

    private HapiSpec createMaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
        return defaultHapiSpec("CreateMaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1, "5"),
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(300_000L).via(CREATE_TX))
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop01 =
                                            getTxnRecord(CREATE_TX)
                                                    .saveTxnRecordToRegistry(CREATE_TX_REC);
                                    allRunFor(spec, subop01);

                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(CREATE_TX_REC)
                                                    .getContractCreateResult()
                                                    .getGasUsed();
                                    assertEquals(285_000L, gasUsed);
                                }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1));
    }

    private HapiSpec createMinChargeIsTXGasUsedByContractCreate() {
        return defaultHapiSpec("CreateMinChargeIsTXGasUsedByContractCreate")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1, "100"),
                        uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(contractCreate(EMPTY_CONSTRUCTOR_CONTRACT).gas(300_000L).via(CREATE_TX))
                .then(
                        withOpContext(
                                (spec, ignore) -> {
                                    final var subop01 =
                                            getTxnRecord(CREATE_TX)
                                                    .saveTxnRecordToRegistry(CREATE_TX_REC);
                                    allRunFor(spec, subop01);

                                    final var gasUsed =
                                            spec.registry()
                                                    .getTransactionRecord(CREATE_TX_REC)
                                                    .getContractCreateResult()
                                                    .getGasUsed();
                                    assertTrue(gasUsed > 0L);
                                }),
                        resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1));
    }

    private HapiSpec propagatesNestedCreations() {
        final var call = "callTxn";
        final var creation = "createTxn";
        final var contract = "NestedCreations";

        final var adminKey = "adminKey";
        final var entityMemo = "JUST DO IT";
        final var customAutoRenew = 7776001L;
        final AtomicReference<String> firstLiteralId = new AtomicReference<>();
        final AtomicReference<String> secondLiteralId = new AtomicReference<>();
        final AtomicReference<ByteString> expectedFirstAddress = new AtomicReference<>();
        final AtomicReference<ByteString> expectedSecondAddress = new AtomicReference<>();

        return defaultHapiSpec("PropagatesNestedCreations")
                .given(
                        newKeyNamed(adminKey),
                        uploadInitCode(contract),
                        contractCreate(contract)
                                .stakedNodeId(0)
                                .adminKey(adminKey)
                                .entityMemo(entityMemo)
                                .autoRenewSecs(customAutoRenew)
                                .via(creation))
                .when(contractCall(contract, "propagate").gas(4_000_000L).via(call))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var parentNum = spec.registry().getContractId(contract);
                                    final var firstId =
                                            ContractID.newBuilder()
                                                    .setContractNum(parentNum.getContractNum() + 1L)
                                                    .build();
                                    firstLiteralId.set(
                                            HapiPropertySource.asContractString(firstId));
                                    expectedFirstAddress.set(
                                            ByteString.copyFrom(asSolidityAddress(firstId)));
                                    final var secondId =
                                            ContractID.newBuilder()
                                                    .setContractNum(parentNum.getContractNum() + 2L)
                                                    .build();
                                    secondLiteralId.set(
                                            HapiPropertySource.asContractString(secondId));
                                    expectedSecondAddress.set(
                                            ByteString.copyFrom(asSolidityAddress(secondId)));
                                }),
                        sourcing(
                                () ->
                                        childRecordsCheck(
                                                call,
                                                ResponseCodeEnum.SUCCESS,
                                                recordWith()
                                                        .contractCreateResult(
                                                                resultWith()
                                                                        .evmAddress(
                                                                                expectedFirstAddress
                                                                                        .get()))
                                                        .status(ResponseCodeEnum.SUCCESS),
                                                recordWith()
                                                        .contractCreateResult(
                                                                resultWith()
                                                                        .evmAddress(
                                                                                expectedSecondAddress
                                                                                        .get()))
                                                        .status(ResponseCodeEnum.SUCCESS))),
                        sourcing(
                                () ->
                                        getContractInfo(firstLiteralId.get())
                                                .has(
                                                        contractWith()
                                                                .propertiesInheritedFrom(
                                                                        contract))));
    }

    HapiSpec temporarySStoreRefundTest() {
        final var contract = "TemporarySStoreRefund";
        return defaultHapiSpec("TemporarySStoreRefundTest")
                .given(
                        overriding(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1, "100"),
                        uploadInitCode(contract),
                        contractCreate(contract))
                .when(
                        contractCall(contract, "holdTemporary", BigInteger.valueOf(10))
                                .via("tempHoldTx"),
                        contractCall(contract, "holdPermanently", BigInteger.valueOf(10))
                                .via("permHoldTx"))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final var subop01 =
                                            getTxnRecord("tempHoldTx")
                                                    .saveTxnRecordToRegistry("tempHoldTxRec")
                                                    .logged();
                                    final var subop02 =
                                            getTxnRecord("permHoldTx")
                                                    .saveTxnRecordToRegistry("permHoldTxRec")
                                                    .logged();

                                    CustomSpecAssert.allRunFor(spec, subop01, subop02);

                                    final var gasUsedForTemporaryHoldTx =
                                            spec.registry()
                                                    .getTransactionRecord("tempHoldTxRec")
                                                    .getContractCallResult()
                                                    .getGasUsed();
                                    final var gasUsedForPermanentHoldTx =
                                            spec.registry()
                                                    .getTransactionRecord("permHoldTxRec")
                                                    .getContractCallResult()
                                                    .getGasUsed();

                                    Assertions.assertTrue(gasUsedForTemporaryHoldTx < 23535L);
                                    Assertions.assertTrue(gasUsedForPermanentHoldTx > 20000L);
                                }),
                        UtilVerbs.resetToDefault(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT1));
    }

    private HapiSpec deletedContractsCannotBeUpdated() {
        final var contract = "SelfDestructCallable";

        return defaultHapiSpec("DeletedContractsCannotBeUpdated")
                .given(uploadInitCode(contract), contractCreate(contract).gas(300_000))
                .when(contractCall(contract, "destroy").deferStatusResolution())
                .then(
                        contractUpdate(contract)
                                .newMemo("Hi there!")
                                .hasKnownStatus(INVALID_CONTRACT_ID));
    }

    private HapiSpec canCallPendingContractSafely() {
        final var numSlots = 64L;
        final var createBurstSize = 500;
        final long[] targets = {19, 24};
        final AtomicLong createdFileNum = new AtomicLong();
        final var callTxn = "callTxn";
        final var contract = "FibonacciPlus";
        final var expiry = Instant.now().getEpochSecond() + 7776000;

        return defaultHapiSpec("CanCallPendingContractSafely")
                .given(
                        uploadSingleInitCode(contract, expiry, GENESIS, createdFileNum::set),
                        inParallel(
                                IntStream.range(0, createBurstSize)
                                        .mapToObj(
                                                i ->
                                                        contractCustomCreate(
                                                                        contract,
                                                                        String.valueOf(i),
                                                                        numSlots)
                                                                .fee(ONE_HUNDRED_HBARS)
                                                                .gas(300_000L)
                                                                .payingWith(GENESIS)
                                                                .noLogging()
                                                                .deferStatusResolution()
                                                                .bytecode(contract)
                                                                .adminKey(THRESHOLD))
                                        .toArray(HapiSpecOperation[]::new)))
                .when()
                .then(
                        sourcing(
                                () ->
                                        contractCallWithFunctionAbi(
                                                        "0.0."
                                                                + (createdFileNum.get()
                                                                        + createBurstSize),
                                                        getABIFor(FUNCTION, "addNthFib", contract),
                                                        targets,
                                                        12L)
                                                .payingWith(GENESIS)
                                                .gas(300_000L)
                                                .via(callTxn)));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
