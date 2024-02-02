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

package com.hedera.services.bdd.suites.contract.opcodes;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.crypto.Hash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(SMART_CONTRACT)
public class ExtCodeHashOperationSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(ExtCodeHashOperationSuite.class);

    public static void main(String[] args) {
        new ExtCodeHashOperationSuite().runSuiteAsync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(verifiesExistence(), readsAccountStorageBeforeCheckingGas());
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @SuppressWarnings("java:S5960")
    @HapiTest
    HapiSpec verifiesExistence() {
        final var contract = "ExtCodeOperationsChecker";
        final var invalidAddress = "0x0000000000000000000000000000000000123456";
        final var expectedAccountHash =
                ByteString.copyFrom(Hash.keccak256(Bytes.EMPTY).toArray());
        final var hashOf = "hashOf";

        final String account = "account";
        return defaultHapiSpec("VerifiesExistence", NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(uploadInitCode(contract), contractCreate(contract), cryptoCreate(account))
                .when()
                .then(
                        contractCall(contract, hashOf, asHeadlongAddress(invalidAddress))
                                .hasKnownStatus(SUCCESS),
                        contractCallLocal(contract, hashOf, asHeadlongAddress(invalidAddress))
                                .hasAnswerOnlyPrecheck(OK),
                        withOpContext((spec, opLog) -> {
                            final var accountID = spec.registry().getAccountID(account);
                            final var contractID = spec.registry().getContractId(contract);
                            final var accountSolidityAddress = asHexedSolidityAddress(accountID);
                            final var contractAddress = asHexedSolidityAddress(contractID);

                            final var call = contractCall(contract, hashOf, asHeadlongAddress(accountSolidityAddress))
                                    .via("callRecord");
                            final var callRecord = getTxnRecord("callRecord");

                            final var accountCodeHashCallLocal = contractCallLocal(
                                            contract, hashOf, asHeadlongAddress(accountSolidityAddress))
                                    .saveResultTo("accountCodeHash");

                            final var contractCodeHash = contractCallLocal(
                                            contract, hashOf, asHeadlongAddress(contractAddress))
                                    .saveResultTo("contractCodeHash");

                            final var getBytecode =
                                    getContractBytecode(contract).saveResultTo("contractBytecode");

                            allRunFor(spec, call, callRecord, accountCodeHashCallLocal, contractCodeHash, getBytecode);

                            final var recordResult =
                                    callRecord.getResponseRecord().getContractCallResult();
                            final var accountCodeHash = spec.registry().getBytes("accountCodeHash");

                            final var contractCodeResult = spec.registry().getBytes("contractCodeHash");
                            final var contractBytecode = spec.registry().getBytes("contractBytecode");
                            final var expectedContractCodeHash = ByteString.copyFrom(
                                            Hash.keccak256(Bytes.of(contractBytecode))
                                                    .toArray())
                                    .toByteArray();

                            Assertions.assertEquals(expectedAccountHash, recordResult.getContractCallResult());
                            Assertions.assertArrayEquals(expectedAccountHash.toByteArray(), accountCodeHash);
                            Assertions.assertArrayEquals(expectedContractCodeHash, contractCodeResult);
                        }));
    }

    @SuppressWarnings("java:S5960")
    @HapiTest
    HapiSpec readsAccountStorageBeforeCheckingGas() {

        final var contract = "ExtCodeOperationsChecker";
        final var attackerContract = "ExtCodeHashAttacker";
        final String account = "account";
        final var attackFunction = "callHashOfInLoopWithTryCatch";

        return defaultHapiSpec("VerifiesExistence", NONDETERMINISTIC_FUNCTION_PARAMETERS)
                .given(
                        uploadInitCode(contract),
                        contractCreate(contract),
                        uploadInitCode(attackerContract),
                        contractCreate(attackerContract),
                        cryptoCreate(account))
                .when()
                .then(withOpContext((spec, opLog) -> {
                    final var contractID = spec.registry().getContractId(contract);
                    final var contractSolidityAddress = asHexedSolidityAddress(contractID);

                    final var response = contractCallLocal(
                                    attackerContract,
                                    attackFunction,
                                    asHeadlongAddress(contractSolidityAddress),
                                    new BigInteger("229"), // gasLimit calculated to leave 0 gas for EXTCODEHASH op
                                    new BigInteger("10"))
                            .gas(25800 + (9 * 900))
                            .saveResultTo("contractCodeHash");
                    // 25800 gas for initial call and 900 for subsequent loops
                    // note that gas for each loop should be over 2600 for cold EXTCODEHASH op

                    allRunFor(spec, response);
                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
