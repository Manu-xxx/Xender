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
package com.hedera.services.bdd.suites.contract.traceability;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCustomCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.ethereumContractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.stateChangesToGrpc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.resetToDefault;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asSolidityAddress;
import static com.hedera.services.bdd.suites.contract.Utils.extractBytecodeUnhexed;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;
import static com.hedera.services.bdd.suites.contract.precompile.AssociatePrecompileSuite.getNestedContractAddress;
import static com.hedera.services.stream.proto.ContractActionType.CALL;
import static com.hedera.services.stream.proto.ContractActionType.CREATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SOLIDITY_ADDRESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.assertions.StateChange;
import com.hedera.services.bdd.spec.assertions.StorageChange;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.verification.traceability.ExpectedSidecar;
import com.hedera.services.bdd.spec.verification.traceability.SidecarWatcher;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.contract.Utils.FunctionType;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.stream.proto.CallOperationType;
import com.hedera.services.stream.proto.ContractAction;
import com.hedera.services.stream.proto.ContractActions;
import com.hedera.services.stream.proto.ContractBytecode;
import com.hedera.services.stream.proto.ContractStateChanges;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.ethereum.core.CallTransaction;

public class NewTraceabilitySuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(NewTraceabilitySuite.class);
    private static final String RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY = "recordStream.path";

    private static SidecarWatcher sidecarWatcher;
    private static final ByteString EMPTY = ByteStringUtils.wrapUnsafely(new byte[0]);
    private static final ByteString CALL_CODE_INPUT_SUFFIX =
            ByteStringUtils.wrapUnsafely(new byte[28]);
    private static final String TRACEABILITY = "Traceability";
    private static final String TRACEABILITY_CALLCODE = "TraceabilityCallcode";
    private static final String REVERTING_CONTRACT = "RevertingContract";
    private static final String PAY_RECEIVABLE_CONTRACT = "PayReceivable";
    private static final String FIRST_CREATE_TXN = "FirstCreateTxn";
    private static final String SECOND_CREATE_TXN = "SecondCreateTxn";
    private static final String THIRD_CREATE_TXN = "ThirdCreateTxn";
    private static final String SECOND = "B";
    private static final String THIRD = "C";
    private static final String TRACEABILITY_TXN = "nestedtxn";
    private static final String GET_ZERO_SLOT = "getSlot0";
    private static final String GET_FIRST_SLOT = "getSlot1";
    private static final String GET_SECOND_SLOT = "getSlot2";
    private static final String SET_ZERO_SLOT = "setSlot0";
    private static final String SET_FIRST_SLOT = "setSlot1";
    private static final String SET_SECOND_SLOT = "setSlot2";

    public static void main(String... args) {
        new NewTraceabilitySuite().runSuiteSync();
    }

    @SuppressWarnings("java:S5960")
    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        try {
            initialize();
        } catch (Exception e) {
            log.warn("An exception occurred initializing watch service", e);
            return List.of(
                    defaultHapiSpec("initialize")
                            .given()
                            .when()
                            .then(
                                    assertionsHold(
                                            (spec, opLog) ->
                                                    fail(
                                                            "Watch service couldn't be"
                                                                    + " initialized."))));
        }
        return List.of(
                traceabilityE2EScenario1(),
                traceabilityE2EScenario2(),
                traceabilityE2EScenario3(),
                traceabilityE2EScenario4(),
                traceabilityE2EScenario5(),
                traceabilityE2EScenario6(),
                traceabilityE2EScenario7(),
                traceabilityE2EScenario8(),
                traceabilityE2EScenario9(),
                traceabilityE2EScenario10(),
                traceabilityE2EScenario11(),
                traceabilityE2EScenario13(),
                traceabilityE2EScenario17(),
                traceabilityE2EScenario18(),
                traceabilityE2EScenario20(),
                traceabilityE2EScenario21(),
                vanillaBytecodeSidecar(),
                vanillaBytecodeSidecar2(),
                assertSidecars());
    }

    private HapiApiSpec traceabilityE2EScenario1() {
        return defaultHapiSpec("traceabilityE2EScenario1")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 55, 2, 2).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(68492)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 55, 2, 2),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 12).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(12))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 12),
                        contractCustomCreate(TRACEABILITY, THIRD, 0, 11, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(11)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 0, 11, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario1",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(12),
                                                                formattedAssertionValue(143))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(11),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(33979)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963018)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960236)
                                                                        .setGasUsed(5324)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(952309)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        12)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(949543)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        143)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(946053)
                                                                        .setGasUsed(5778)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(928026)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(939987)
                                                                        .setGasUsed(1501)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(924301)
                                                                        .setGasUsed(423)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(938149)
                                                                        .setGasUsed(3345)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(922684)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        11)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(934470)
                                                                        .setGasUsed(4235)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(918936)
                                                                        .setGasUsed(3224)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario2() {
        return defaultHapiSpec("traceabilityE2EScenario2")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 0, 0, 0).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(8792)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 0, 0, 0),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 99).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(99))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 99),
                        contractCustomCreate(TRACEABILITY, THIRD, 0, 88, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(88)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 0, 88, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario2",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(100)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(99),
                                                                formattedAssertionValue(143))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(70255)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963083)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960302)
                                                                        .setGasUsed(22424)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(937875)
                                                                        .setGasUsed(5811)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(919912)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        99)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(931783)
                                                                        .setGasUsed(4235)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        143)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(916248)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        143)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(927248)
                                                                        .setGasUsed(5819)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressGetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(909474)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(921145)
                                                                        .setGasUsed(21353)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressSetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        100)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(905801)
                                                                        .setGasUsed(20323)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        100)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(899766)
                                                                        .setGasUsed(3387)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressGetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(884859)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(896045)
                                                                        .setGasUsed(1476)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressSetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(881071)
                                                                        .setGasUsed(424)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario3() {
        return defaultHapiSpec("traceabilityE2EScenario3")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 55, 2, 2).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(68492)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 55, 2, 2),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 12).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(12))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 12),
                        contractCustomCreate(TRACEABILITY, THIRD, 0, 11, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(11)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 0, 11, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario3",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55252)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(524))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(54)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(11),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(57011)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario3",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963059)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960277)
                                                                        .setGasUsed(5324)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55252)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(954683)
                                                                        .setGasUsed(5810)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressGetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(936458)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(948592)
                                                                        .setGasUsed(4209)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(932820)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(941399)
                                                                        .setGasUsed(3278)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(925906)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(937474)
                                                                        .setGasUsed(21401)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        54)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(921827)
                                                                        .setGasUsed(20323)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        54)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(915805)
                                                                        .setGasUsed(3345)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(900689)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        11)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(911814)
                                                                        .setGasUsed(4235)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(896634)
                                                                        .setGasUsed(3224)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario4() {
        return defaultHapiSpec("traceabilityE2EScenario4")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 2, 3, 4).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(68492)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 2, 3, 4),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 0).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(8792)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 0),
                        contractCustomCreate(TRACEABILITY, THIRD, 0, 0, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(8792)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 0, 0, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario4",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(4))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(23913)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario4",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963038)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960256)
                                                                        .setGasUsed(3223)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        3)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(956871)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        3)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(954049)
                                                                        .setGasUsed(3224)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(950522)
                                                                        .setGasUsed(5810)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressGetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(932362)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(944118)
                                                                        .setGasUsed(3953)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressSetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(925954)
                                                                        .setGasUsed(423)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario5() {
        return defaultHapiSpec("traceabilityE2EScenario5")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 55, 2, 2).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(68492)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 55, 2, 2),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 12).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(12))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 12),
                        contractCustomCreate(TRACEABILITY, THIRD, 4, 1, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(48592)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 4, 1, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario5",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55252))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(12),
                                                                formattedAssertionValue(524))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(27376)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario5",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963081)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960300)
                                                                        .setGasUsed(5324)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55252)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(952373)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        12)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(949607)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(946117)
                                                                        .setGasUsed(5777)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "staticCallAddressGetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_STATICCALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(928090)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(940069)
                                                                        .setGasUsed(3320)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "staticCallAddressGetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_STATICCALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(924598)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        1)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario6() {
        return defaultHapiSpec("traceabilityE2EScenario6")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 2, 3, 4).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(68492)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 2, 3, 4),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 3).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 3),
                        contractCustomCreate(TRACEABILITY, THIRD, 0, 1, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 0, 1, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario6",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(4),
                                                                formattedAssertionValue(5))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(29910)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario6",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963082)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960301)
                                                                        .setGasUsed(5324)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(954706)
                                                                        .setGasUsed(5810)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressGetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(936481)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(948616)
                                                                        .setGasUsed(4209)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "delegateCallAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        5)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(932843)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        5)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(943883)
                                                                        .setGasUsed(5777)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "staticCallAddressGetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_STATICCALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(925891)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(937591)
                                                                        .setGasUsed(3320)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "staticCallAddressGetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_STATICCALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGas(922159)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        1)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario7() {
        return defaultHapiSpec("traceabilityE2EScenario7")
                .given(
                        uploadInitCode(TRACEABILITY_CALLCODE),
                        contractCreate(TRACEABILITY_CALLCODE, 55, 2, 2).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGasUsed(67632)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN,
                                TRACEABILITY_CALLCODE,
                                TRACEABILITY_CALLCODE,
                                55,
                                2,
                                2),
                        contractCustomCreate(TRACEABILITY_CALLCODE, SECOND, 0, 0, 12)
                                .via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(12))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGasUsed(27832)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN,
                                TRACEABILITY_CALLCODE + SECOND,
                                TRACEABILITY_CALLCODE,
                                0,
                                0,
                                12),
                        contractCustomCreate(TRACEABILITY_CALLCODE, THIRD, 4, 1, 0)
                                .via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setGasUsed(47732)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN,
                                TRACEABILITY_CALLCODE + THIRD,
                                TRACEABILITY_CALLCODE,
                                4,
                                1,
                                0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY_CALLCODE,
                                                                "eetScenario7",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY_CALLCODE + "B",
                                                                        spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY_CALLCODE + "C",
                                                                        spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55252))),
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(54)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(12),
                                                                formattedAssertionValue(524))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(51483)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "eetScenario7",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(963159)
                                                                        .setGasUsed(2500)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setCallDepth(1)
                                                                        .setGas(960259)
                                                                        .setGasUsed(5249)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55252)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(952294)
                                                                        .setGasUsed(2368)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        12)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(949526)
                                                                        .setGasUsed(3215)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(945992)
                                                                        .setGasUsed(6069)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressGetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(927718)
                                                                        .setGasUsed(2500)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                GET_ZERO_SLOT)
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(939626)
                                                                        .setGasUsed(21544)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressSetSlot0",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        54)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(923822)
                                                                        .setGasUsed(20381)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                SET_ZERO_SLOT,
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                54))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(918049)
                                                                        .setGasUsed(3393)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressGetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + THIRD,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(902867)
                                                                        .setGasUsed(2522)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                GET_FIRST_SLOT)
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(914320)
                                                                        .setGasUsed(1270)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressSetSlot1",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + THIRD,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(899149)
                                                                        .setGasUsed(349)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                SET_FIRST_SLOT,
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                0))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario8() {
        return defaultHapiSpec("traceabilityE2EScenario8")
                .given(
                        uploadInitCode(TRACEABILITY_CALLCODE),
                        contractCreate(TRACEABILITY_CALLCODE, 55, 2, 2).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGasUsed(67632)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN,
                                TRACEABILITY_CALLCODE,
                                TRACEABILITY_CALLCODE,
                                55,
                                2,
                                2),
                        contractCustomCreate(TRACEABILITY_CALLCODE, SECOND, 0, 0, 12)
                                .via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(12))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGasUsed(27832)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN,
                                TRACEABILITY_CALLCODE + SECOND,
                                TRACEABILITY_CALLCODE,
                                0,
                                0,
                                12),
                        contractCustomCreate(TRACEABILITY_CALLCODE, THIRD, 4, 1, 0)
                                .via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setGasUsed(47732)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN,
                                TRACEABILITY_CALLCODE + THIRD,
                                TRACEABILITY_CALLCODE,
                                4,
                                1,
                                0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY_CALLCODE,
                                                                "eetScenario8",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY_CALLCODE + "B",
                                                                        spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY_CALLCODE + "C",
                                                                        spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY_CALLCODE)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(55252)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(524))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(29301)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "eetScenario8",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(962924)
                                                                        .setGasUsed(2500)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setCallDepth(1)
                                                                        .setGas(960024)
                                                                        .setGasUsed(3281)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(956466)
                                                                        .setGasUsed(2522)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        GET_FIRST_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setCallDepth(1)
                                                                        .setGas(953547)
                                                                        .setGasUsed(3149)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55252)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(950079)
                                                                        .setGasUsed(5783)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressGetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + SECOND,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(931893)
                                                                        .setGasUsed(2368)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                GET_SECOND_SLOT)
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(943995)
                                                                        .setGasUsed(4290)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY_CALLCODE,
                                                                                        "callcodeAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY_CALLCODE
                                                                                                                + SECOND,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(928209)
                                                                        .setGasUsed(3215)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                SET_SECOND_SLOT,
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                524))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE))
                                                                        .setGas(938961)
                                                                        .setGasUsed(4144)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                "callcodeAddressSetSlot0",
                                                                                                hexedSolidityAddressToHeadlongAddress(
                                                                                                        getNestedContractAddress(
                                                                                                                TRACEABILITY_CALLCODE
                                                                                                                        + THIRD,
                                                                                                                spec)),
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                55))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALLCODE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + SECOND))
                                                                        .setGas(920706)
                                                                        .setGasUsed(481)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY_CALLCODE
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                                TRACEABILITY_CALLCODE,
                                                                                                SET_ZERO_SLOT,
                                                                                                BigInteger
                                                                                                        .valueOf(
                                                                                                                55))
                                                                                        .concat(
                                                                                                CALL_CODE_INPUT_SUFFIX))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario9() {
        return defaultHapiSpec("traceabilityE2EScenario9")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 55, 2, 2).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(68492)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 55, 2, 2),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 12).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(12))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 12),
                        contractCustomCreate(TRACEABILITY, THIRD, 0, 1, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 0, 1, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario9",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(55)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(2))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(12))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(50335)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario9",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963040)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960258)
                                                                        .setGasUsed(5324)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55252)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(952332)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        12)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(949566)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        524)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(943624)
                                                                        .setGasUsed(29899)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callToContractCForE2EScenario92"))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(928493)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(925711)
                                                                        .setGasUsed(20323)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        55)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(905493)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        1)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(902659)
                                                                        .setGasUsed(3224)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        155)))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario10() {
        return defaultHapiSpec("traceabilityE2EScenario10")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 2, 3, 4).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(68492)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 2, 3, 4),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 3).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 3),
                        contractCustomCreate(TRACEABILITY, THIRD, 0, 1, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 0, 1, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario10",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(4))),
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(5))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(52541)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario10",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963038)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960256)
                                                                        .setGasUsed(5324)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(954662)
                                                                        .setGasUsed(5811)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressGetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(936436)
                                                                        .setGasUsed(2315)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        3)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_SECOND_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(948571)
                                                                        .setGasUsed(4235)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "callAddressSetSlot2",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + SECOND,
                                                                                                        spec)),
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        5)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(932774)
                                                                        .setGasUsed(3180)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_SECOND_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        5)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(941591)
                                                                        .setGasUsed(29898)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "failingGettingAndSetting"))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(926492)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(923710)
                                                                        .setGasUsed(20323)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        12)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(903492)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        1)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGas(900658)
                                                                        .setGasUsed(3224)
                                                                        .setCallDepth(2)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario11() {
        return defaultHapiSpec("traceabilityE2EScenario11")
                .given(
                        uploadInitCode(TRACEABILITY),
                        contractCreate(TRACEABILITY, 2, 3, 4).via(FIRST_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                FIRST_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(4))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGasUsed(68492)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, TRACEABILITY, TRACEABILITY, 2, 3, 4),
                        contractCustomCreate(TRACEABILITY, SECOND, 0, 0, 3).via(SECOND_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                SECOND_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + SECOND)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(3))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        SECOND_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                SECOND_CREATE_TXN, TRACEABILITY + SECOND, TRACEABILITY, 0, 0, 3),
                        contractCustomCreate(TRACEABILITY, THIRD, 0, 1, 0).via(THIRD_CREATE_TXN),
                        expectContractStateChangesSidecarFor(
                                THIRD_CREATE_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(1)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(2),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        THIRD_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setGasUsed(28692)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                THIRD_CREATE_TXN, TRACEABILITY + THIRD, TRACEABILITY, 0, 1, 0))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                TRACEABILITY,
                                                                "eetScenario11",
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "B", spec),
                                                                getNestedContractAddress(
                                                                        TRACEABILITY + "C", spec))
                                                        .gas(1_000_000)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        expectContractStateChangesSidecarFor(
                                TRACEABILITY_TXN,
                                List.of(
                                        StateChange.stateChangeFor(TRACEABILITY)
                                                .withStorageChanges(
                                                        StorageChange.onlyRead(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(2)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(3),
                                                                formattedAssertionValue(4))),
                                        StateChange.stateChangeFor(TRACEABILITY + THIRD)
                                                .withStorageChanges(
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(0),
                                                                formattedAssertionValue(123)),
                                                        StorageChange.readAndWritten(
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(1),
                                                                formattedAssertionValue(0))))),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(44077)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        "eetScenario11",
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "B",
                                                                                                        spec)),
                                                                                        hexedSolidityAddressToHeadlongAddress(
                                                                                                getNestedContractAddress(
                                                                                                        TRACEABILITY
                                                                                                                + "C",
                                                                                                        spec))))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(963038)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        2)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(960256)
                                                                        .setGasUsed(5324)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_DELEGATECALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(952341)
                                                                        .setGasUsed(237)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + SECOND))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                ByteString.copyFrom(
                                                                                        "readAndWriteThenRevert()"
                                                                                                .getBytes(
                                                                                                        StandardCharsets
                                                                                                                .UTF_8)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(949404)
                                                                        .setGasUsed(2347)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_ZERO_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setCallDepth(1)
                                                                        .setGas(946606)
                                                                        .setGasUsed(20323)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_ZERO_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        123)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(926387)
                                                                        .setGasUsed(2391)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(
                                                                                uint256ReturnWithValue(
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        1)))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        GET_FIRST_SLOT))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY))
                                                                        .setGas(923534)
                                                                        .setGasUsed(3224)
                                                                        .setCallDepth(1)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                TRACEABILITY
                                                                                                        + THIRD))
                                                                        .setOutput(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        TRACEABILITY,
                                                                                        SET_FIRST_SLOT,
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        0)))
                                                                        .build())))));
    }

    HapiApiSpec traceabilityE2EScenario13() {
        AtomicReference<AccountID> accountIDAtomicReference = new AtomicReference<>();
        return defaultHapiSpec("traceabilityE2EScenario13")
                .given(
                        overriding("contracts.chainId", "298"),
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(RELAYER).balance(6 * ONE_MILLION_HBARS),
                        cryptoTransfer(
                                        tinyBarsFromAccountToAlias(
                                                GENESIS, SECP_256K1_SOURCE_KEY, ONE_HUNDRED_HBARS))
                                .via("autoAccount"),
                        getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                .exposingIdTo(accountIDAtomicReference::set),
                        getTxnRecord("autoAccount").andAllChildRecords(),
                        uploadInitCode(PAY_RECEIVABLE_CONTRACT))
                .when(
                        ethereumContractCreate(PAY_RECEIVABLE_CONTRACT)
                                .type(EthTxData.EthTransactionType.EIP1559)
                                .signingWith(SECP_256K1_SOURCE_KEY)
                                .payingWith(RELAYER)
                                .nonce(0)
                                .maxGasAllowance(ONE_HUNDRED_HBARS)
                                .gasLimit(1_000_000L)
                                .hasKnownStatus(SUCCESS)
                                .via(FIRST_CREATE_TXN))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord =
                                            getTxnRecord(FIRST_CREATE_TXN);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    FIRST_CREATE_TXN,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CREATE)
                                                                    .setCallingAccount(
                                                                            accountIDAtomicReference
                                                                                    .get())
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            PAY_RECEIVABLE_CONTRACT))
                                                                    .setGas(947000)
                                                                    .setGasUsed(135)
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }),
                        expectContractBytecodeWithMinimalFieldsSidecarFor(
                                FIRST_CREATE_TXN, PAY_RECEIVABLE_CONTRACT),
                        resetToDefault("contracts.chainId"));
    }

    private HapiApiSpec traceabilityE2EScenario17() {
        return defaultHapiSpec("traceabilityE2EScenario17")
                .given(
                        uploadInitCode(REVERTING_CONTRACT),
                        contractCreate(REVERTING_CONTRACT, 6).via(FIRST_CREATE_TXN),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setGasUsed(345)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, REVERTING_CONTRACT, REVERTING_CONTRACT, 6))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                REVERTING_CONTRACT,
                                                                "createContract",
                                                                BigInteger.valueOf(4))
                                                        .gas(1_000_000)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(32583)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setRevertReason(EMPTY)
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        REVERTING_CONTRACT,
                                                                                        "createContract",
                                                                                        BigInteger
                                                                                                .valueOf(
                                                                                                        4)))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setGas(931868)
                                                                        .setCallDepth(1)
                                                                        .setGasUsed(201)
                                                                        .setRevertReason(EMPTY)
                                                                        .build())))));
    }

    private HapiApiSpec traceabilityE2EScenario18() {
        return defaultHapiSpec("traceabilityE2EScenario18")
                .given(uploadInitCode(REVERTING_CONTRACT))
                .when(
                        contractCreate(REVERTING_CONTRACT, 4)
                                .via(FIRST_CREATE_TXN)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setGasUsed(201)
                                                                        .setRevertReason(EMPTY)
                                                                        .build())))),
                        expectFailedContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, REVERTING_CONTRACT, 4));
    }

    private HapiApiSpec traceabilityE2EScenario20() {
        return defaultHapiSpec("traceabilityE2EScenario20")
                .given(uploadInitCode(REVERTING_CONTRACT))
                .when(
                        contractCreate(REVERTING_CONTRACT, 6)
                                .via(FIRST_CREATE_TXN)
                                .gas(53050)
                                .hasKnownStatus(INSUFFICIENT_GAS))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(50)
                                                                        .setGasUsed(50)
                                                                        .setError(
                                                                                ByteString
                                                                                        .copyFromUtf8(
                                                                                                INSUFFICIENT_GAS
                                                                                                        .name()))
                                                                        .build())))),
                        expectFailedContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, REVERTING_CONTRACT, 6));
    }

    private HapiApiSpec traceabilityE2EScenario21() {
        return defaultHapiSpec("traceabilityE2EScenario21")
                .given(
                        uploadInitCode(REVERTING_CONTRACT),
                        contractCreate(REVERTING_CONTRACT, 6).via(FIRST_CREATE_TXN),
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        FIRST_CREATE_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CREATE)
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CREATE)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setGas(197000)
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setGasUsed(345)
                                                                        .setOutput(EMPTY)
                                                                        .build())))),
                        expectContractBytecodeSidecarFor(
                                FIRST_CREATE_TXN, REVERTING_CONTRACT, REVERTING_CONTRACT, 6))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                REVERTING_CONTRACT,
                                                                "callingWrongAddress")
                                                        .gas(1_000_000)
                                                        .hasKnownStatus(INVALID_SOLIDITY_ADDRESS)
                                                        .via(TRACEABILITY_TXN))))
                .then(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                expectContractActionSidecarFor(
                                                        TRACEABILITY_TXN,
                                                        List.of(
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingAccount(
                                                                                TxnUtils.asId(
                                                                                        GENESIS,
                                                                                        spec))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setGas(979000)
                                                                        .setGasUsed(979000)
                                                                        .setError(
                                                                                ByteString
                                                                                        .copyFromUtf8(
                                                                                                INVALID_SOLIDITY_ADDRESS
                                                                                                        .name()))
                                                                        .setRecipientContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setInput(
                                                                                encodeFunctionCall(
                                                                                        REVERTING_CONTRACT,
                                                                                        "callingWrongAddress"))
                                                                        .build(),
                                                                ContractAction.newBuilder()
                                                                        .setCallType(CALL)
                                                                        .setCallingContract(
                                                                                spec.registry()
                                                                                        .getContractId(
                                                                                                REVERTING_CONTRACT))
                                                                        .setCallOperationType(
                                                                                CallOperationType
                                                                                        .OP_CALL)
                                                                        .setCallDepth(1)
                                                                        .setGas(978487)
                                                                        .setError(
                                                                                ByteString
                                                                                        .copyFromUtf8(
                                                                                                INVALID_SOLIDITY_ADDRESS
                                                                                                        .name()))
                                                                        .setInvalidSolidityAddress(
                                                                                ByteString.copyFrom(
                                                                                        asSolidityAddress(
                                                                                                0,
                                                                                                0,
                                                                                                0)))
                                                                        .build())))));
    }

    private HapiApiSpec vanillaBytecodeSidecar() {
        final var EMPTY_CONSTRUCTOR_CONTRACT = "EmptyConstructor";
        final var vanillaBytecodeSidecar = "vanillaBytecodeSidecar";
        final var firstTxn = "firstTxn";
        return defaultHapiSpec(vanillaBytecodeSidecar)
                .given(uploadInitCode(EMPTY_CONSTRUCTOR_CONTRACT))
                .when(
                        contractCreate(EMPTY_CONSTRUCTOR_CONTRACT)
                                .hasKnownStatus(SUCCESS)
                                .via(firstTxn))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord = getTxnRecord(firstTxn);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    firstTxn,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CREATE)
                                                                    .setCallingAccount(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            GENESIS))
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            EMPTY_CONSTRUCTOR_CONTRACT))
                                                                    .setGas(197000)
                                                                    .setGasUsed(66)
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }),
                        expectContractBytecodeSidecarFor(
                                firstTxn, EMPTY_CONSTRUCTOR_CONTRACT, EMPTY_CONSTRUCTOR_CONTRACT));
    }

    private HapiApiSpec vanillaBytecodeSidecar2() {
        final var contract = "CreateTrivial";
        final String trivialCreate = "vanillaBytecodeSidecar2";
        final var firstTxn = "firstTxn";
        return defaultHapiSpec(trivialCreate)
                .given(uploadInitCode(contract))
                .when(contractCreate(contract).via(firstTxn))
                .then(
                        withOpContext(
                                (spec, opLog) -> {
                                    final HapiGetTxnRecord txnRecord = getTxnRecord(firstTxn);
                                    allRunFor(
                                            spec,
                                            txnRecord,
                                            expectContractActionSidecarFor(
                                                    firstTxn,
                                                    List.of(
                                                            ContractAction.newBuilder()
                                                                    .setCallType(CREATE)
                                                                    .setCallOperationType(
                                                                            CallOperationType
                                                                                    .OP_CREATE)
                                                                    .setCallingAccount(
                                                                            spec.registry()
                                                                                    .getAccountID(
                                                                                            GENESIS))
                                                                    .setRecipientContract(
                                                                            spec.registry()
                                                                                    .getContractId(
                                                                                            contract))
                                                                    .setGas(197000)
                                                                    .setGasUsed(214)
                                                                    .setOutput(EMPTY)
                                                                    .build())));
                                }),
                        expectContractBytecodeSidecarFor(firstTxn, contract, contract));
    }

    @SuppressWarnings("java:S5960")
    private HapiApiSpec assertSidecars() {
        return defaultHapiSpec("assertSidecars")
                .given(
                        // send a dummy transaction to trigger externalization of last sidecars
                        cryptoCreate("externalizeFinalSidecars").delayBy(2000))
                .when(
                        withOpContext(
                                (spec, opLog) -> {
                                    sidecarWatcher.waitUntilFinished();
                                    sidecarWatcher.tearDown();
                                }))
                .then(
                        assertionsHold(
                                (spec, assertLog) -> {
                                    assertTrue(
                                            sidecarWatcher.thereAreNoMismatchedSidecars(),
                                            sidecarWatcher.getErrors());
                                    assertTrue(
                                            sidecarWatcher.thereAreNoPendingSidecars(),
                                            "There are some sidecars that have not been yet"
                                                    + " externalized in the sidecar files after all"
                                                    + " specs.");
                                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    private CustomSpecAssert expectContractActionSidecarFor(
            String txnName, List<ContractAction> actions) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(txnName);
                    allRunFor(spec, txnRecord);
                    final var consensusTimestamp =
                            txnRecord.getResponseRecord().getConsensusTimestamp();
                    sidecarWatcher.addExpectedSidecar(
                            new ExpectedSidecar(
                                    spec.getName(),
                                    TransactionSidecarRecord.newBuilder()
                                            .setConsensusTimestamp(consensusTimestamp)
                                            .setActions(
                                                    ContractActions.newBuilder()
                                                            .addAllContractActions(actions)
                                                            .build())
                                            .build()));
                });
    }

    private CustomSpecAssert expectContractStateChangesSidecarFor(
            final String txnName, final List<StateChange> stateChanges) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(txnName);
                    allRunFor(spec, txnRecord);
                    final var consensusTimestamp =
                            txnRecord.getResponseRecord().getConsensusTimestamp();
                    sidecarWatcher.addExpectedSidecar(
                            new ExpectedSidecar(
                                    spec.getName(),
                                    TransactionSidecarRecord.newBuilder()
                                            .setConsensusTimestamp(consensusTimestamp)
                                            .setStateChanges(
                                                    ContractStateChanges.newBuilder()
                                                            .addAllContractStateChanges(
                                                                    stateChangesToGrpc(
                                                                            stateChanges, spec))
                                                            .build())
                                            .build()));
                });
    }

    private CustomSpecAssert expectContractBytecodeSidecarFor(
            final String contractCreateTxn,
            final String contractName,
            final String binFileName,
            final Object... constructorArgs) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(contractCreateTxn);
                    final String runtimeBytecode = "runtimeBytecode";
                    final var contractBytecode =
                            getContractBytecode(contractName).saveResultTo(runtimeBytecode);
                    allRunFor(spec, txnRecord, contractBytecode);
                    final var consensusTimestamp =
                            txnRecord.getResponseRecord().getConsensusTimestamp();
                    final var initCode = getInitcode(binFileName, constructorArgs);
                    sidecarWatcher.addExpectedSidecar(
                            new ExpectedSidecar(
                                    spec.getName(),
                                    TransactionSidecarRecord.newBuilder()
                                            .setConsensusTimestamp(consensusTimestamp)
                                            .setBytecode(
                                                    ContractBytecode.newBuilder()
                                                            .setContractId(
                                                                    txnRecord
                                                                            .getResponseRecord()
                                                                            .getContractCreateResult()
                                                                            .getContractID())
                                                            .setInitcode(initCode)
                                                            .setRuntimeBytecode(
                                                                    ByteString.copyFrom(
                                                                            spec.registry()
                                                                                    .getBytes(
                                                                                            runtimeBytecode)))
                                                            .build())
                                            .build()));
                });
    }

    private CustomSpecAssert expectFailedContractBytecodeSidecarFor(
            final String contractCreateTxn,
            final String binFileName,
            final Object... constructorArgs) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(contractCreateTxn);
                    allRunFor(spec, txnRecord);
                    final var consensusTimestamp =
                            txnRecord.getResponseRecord().getConsensusTimestamp();
                    final var initCode = getInitcode(binFileName, constructorArgs);
                    sidecarWatcher.addExpectedSidecar(
                            new ExpectedSidecar(
                                    spec.getName(),
                                    TransactionSidecarRecord.newBuilder()
                                            .setConsensusTimestamp(consensusTimestamp)
                                            .setBytecode(
                                                    ContractBytecode.newBuilder()
                                                            .setInitcode(initCode)
                                                            .build())
                                            .build()));
                });
    }

    private CustomSpecAssert expectContractBytecodeWithMinimalFieldsSidecarFor(
            final String contractCreateTxn, final String contractName) {
        return withOpContext(
                (spec, opLog) -> {
                    final var txnRecord = getTxnRecord(contractCreateTxn).andAllChildRecords();
                    final String runtimeBytecode = "runtimeBytecode";
                    final var contractBytecode =
                            getContractBytecode(contractName).saveResultTo(runtimeBytecode);
                    allRunFor(spec, txnRecord, contractBytecode);
                    final var consensusTimestamp =
                            txnRecord.getChildRecord(0).getConsensusTimestamp();
                    sidecarWatcher.addExpectedSidecar(
                            new ExpectedSidecar(
                                    spec.getName(),
                                    TransactionSidecarRecord.newBuilder()
                                            .setConsensusTimestamp(consensusTimestamp)
                                            .setBytecode(
                                                    ContractBytecode.newBuilder()
                                                            .setContractId(
                                                                    txnRecord
                                                                            .getResponseRecord()
                                                                            .getContractCreateResult()
                                                                            .getContractID())
                                                            .setRuntimeBytecode(
                                                                    ByteString.copyFrom(
                                                                            spec.registry()
                                                                                    .getBytes(
                                                                                            runtimeBytecode)))
                                                            .build())
                                            .build()));
                });
    }

    private ByteString getInitcode(final String binFileName, final Object... constructorArgs) {
        final var initCode = extractBytecodeUnhexed(getResourcePath(binFileName, ".bin"));
        final var params =
                constructorArgs.length == 0
                        ? new byte[] {}
                        : CallTransaction.Function.fromJsonInterface(
                                        getABIFor(
                                                FunctionType.CONSTRUCTOR,
                                                StringUtils.EMPTY,
                                                binFileName))
                                .encodeArguments(constructorArgs);
        return initCode.concat(ByteStringUtils.wrapUnsafely(params));
    }

    private static void initialize() throws Exception {
        final var recordStreamFolderPath =
                HapiApiSpec.isRunningInCi()
                        ? HapiApiSpec.ciPropOverrides().get(RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY)
                        : HapiSpecSetup.getDefaultPropertySource()
                                .get(RECORD_STREAM_FOLDER_PATH_PROPERTY_KEY);
        sidecarWatcher = new SidecarWatcher(Paths.get(recordStreamFolderPath));
        sidecarWatcher.watch();
    }

    private ByteString encodeFunctionCall(
            final String contractName, final String functionName, final Object... args) {
        return ByteStringUtils.wrapUnsafely(
                Function.fromJson(getABIFor(FunctionType.FUNCTION, functionName, contractName))
                        .encodeCallWithArgs(args)
                        .array());
    }

    private byte[] encodeTuple(final String argumentsSignature, final Object... actualArguments) {
        return TupleType.parse(argumentsSignature).encode(Tuple.of(actualArguments)).array();
    }

    private ByteString uint256ReturnWithValue(final BigInteger value) {
        return ByteStringUtils.wrapUnsafely(encodeTuple("(uint256)", value));
    }

    private Address hexedSolidityAddressToHeadlongAddress(final String hexedSolidityAddress) {
        return Address.wrap(Address.toChecksumAddress("0x" + hexedSolidityAddress));
    }

    private ByteString formattedAssertionValue(final long value) {
        return ByteString.copyFrom(
                Bytes.wrap(UInt256.valueOf(value)).trimLeadingZeros().toArrayUnsafe());
    }
}
