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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCallWithFunctionAbi;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts;
import com.hedera.services.bdd.spec.dsl.annotations.ContractSpec;
import com.hedera.services.bdd.spec.dsl.annotations.FungibleTokenSpec;
import com.hedera.services.bdd.spec.dsl.annotations.NonFungibleTokenSpec;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import com.hedera.services.bdd.spec.dsl.entities.SpecFungibleToken;
import com.hedera.services.bdd.spec.dsl.entities.SpecNonFungibleToken;
import com.hedera.services.bdd.suites.contract.Utils;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;

@Tag(SMART_CONTRACT)
@DisplayName("isAssociated")
@SuppressWarnings("java:S1192")
@HapiTestLifecycle
public class IsAssociatedSystemContractTest {

    @FungibleTokenSpec(name = "immutableToken")
    static SpecFungibleToken immutableToken;

    @NonFungibleTokenSpec(name = "immutableNft")
    static SpecNonFungibleToken immutableNft;

    @ContractSpec(contract = "HRCContract", creationGas = 4_000_000L)
    static SpecContract hrcContract;

    @HapiTest
    @DisplayName("check token is not associated with an account with static call")
    public Stream<DynamicTest> checkTokenIsNotAssociatedWithAnAccountWithStaticCall() {
        return hapiTest(hrcContract
                .staticCall("isAssociated", immutableToken)
                .andAssert(query -> query.has(ContractFnResultAsserts.resultWith()
                        .resultThruAbi(
                                getABIFor(FUNCTION, "isAssociated", "HRCContract"),
                                isLiteralResult(new Object[] {false})))));
    }

    @HapiTest
    @DisplayName("check nft is not associated with an account with static call")
    public Stream<DynamicTest> checkNftIsNotAssociatedWithAnAccountWithStaticCall() {
        return hapiTest(hrcContract
                .staticCall("isAssociated", immutableNft)
                .andAssert(query -> query.has(ContractFnResultAsserts.resultWith()
                        .resultThruAbi(
                                getABIFor(FUNCTION, "isAssociated", "HRCContract"),
                                isLiteralResult(new Object[] {false})))));
    }

    @Nested
    @DisplayName("calling from Contract")
    class Contract {
        static final AtomicReference<String> fungibleTokenNum = new AtomicReference<>();
        static final AtomicReference<String> nonFungibleTokenNum = new AtomicReference<>();
        private static final String hrcContract = "hrcContract";

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(
                    uploadInitCode(hrcContract),
                    contractCreate(hrcContract),
                    newKeyNamed("multikey"),
                    tokenCreate("fungibleToken").exposingCreatedIdTo(fungibleTokenNum::set),
                    tokenCreate("nonFungibleToken")
                            .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                            .initialSupply(0)
                            .supplyKey("multikey")
                            .exposingCreatedIdTo(nonFungibleTokenNum::set));
        }

        @HapiTest
        @DisplayName("check token is not associated with an account")
        public Stream<DynamicTest> checkTokenIsNotAssociatedWithAnAccount() {
            return hapiTest(
                    sourcing(() -> contractCall(
                                    hrcContract,
                                    "isAssociated",
                                    asHeadlongAddress(asHexedSolidityAddress(asToken(fungibleTokenNum.get()))))
                            .gas(1_000_000)
                            .via("test")),
                    childRecordsCheck(
                            "test",
                            SUCCESS,
                            TransactionRecordAsserts.recordWith()
                                    .status(SUCCESS)
                                    .contractCallResult(resultWith()
                                            .resultThruAbi(
                                                    getABIFor(
                                                            Utils.FunctionType.FUNCTION, "isAssociated", "hrcContract"),
                                                    isLiteralResult(new Object[] {false})))));
        }

        @HapiTest
        @DisplayName("check nft is not associated with an account")
        public Stream<DynamicTest> checkNftIsNotAssociatedWithAnAccount() {
            return hapiTest(
                    sourcing(() -> contractCall(
                                    hrcContract,
                                    "isAssociated",
                                    asHeadlongAddress(asHexedSolidityAddress(asToken(nonFungibleTokenNum.get()))))
                            .gas(1_000_000)
                            .via("test")),
                    childRecordsCheck(
                            "test",
                            SUCCESS,
                            TransactionRecordAsserts.recordWith()
                                    .status(SUCCESS)
                                    .contractCallResult(resultWith()
                                            .resultThruAbi(
                                                    getABIFor(
                                                            Utils.FunctionType.FUNCTION, "isAssociated", "hrcContract"),
                                                    isLiteralResult(new Object[] {false})))));
        }
    }

    @Nested
    @DisplayName("calling from EOA")
    class EOA {
        static final AtomicReference<String> fungibleTokenNum = new AtomicReference<>();
        static final AtomicReference<String> nonFungibleTokenNum = new AtomicReference<>();

        @BeforeAll
        static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
            testLifecycle.doAdhoc(
                    newKeyNamed("multikey"),
                    tokenCreate("fungibleToken").exposingCreatedIdTo(fungibleTokenNum::set),
                    tokenCreate("nonFungibleToken")
                            .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                            .initialSupply(0)
                            .supplyKey("multikey")
                            .exposingCreatedIdTo(nonFungibleTokenNum::set));
        }

        @HapiTest
        @DisplayName("check token is associated with an account")
        public Stream<DynamicTest> checkTokenIsAssociatedWithAnAccount() {
            return hapiTest(
                    sourcing(() -> contractCallWithFunctionAbi(
                                    asHexedSolidityAddress(asToken(fungibleTokenNum.get())),
                                    getABIFor(Utils.FunctionType.FUNCTION, "isAssociated", "HRC"))
                            .gas(1_000_000)
                            .via("test")),
                    childRecordsCheck(
                            "test",
                            SUCCESS,
                            TransactionRecordAsserts.recordWith()
                                    .status(SUCCESS)
                                    .contractCallResult(resultWith()
                                            .resultThruAbi(
                                                    getABIFor(Utils.FunctionType.FUNCTION, "isAssociated", "HRC"),
                                                    isLiteralResult(new Object[] {true})))));
        }

        @HapiTest
        @DisplayName("check nft is associated with an account")
        public Stream<DynamicTest> checkNftIsAssociatedWithAnAccount() {
            return hapiTest(
                    sourcing(() -> contractCallWithFunctionAbi(
                                    asHexedSolidityAddress(asToken(nonFungibleTokenNum.get())),
                                    getABIFor(Utils.FunctionType.FUNCTION, "isAssociated", "HRC"))
                            .gas(1_000_000)
                            .via("test")),
                    childRecordsCheck(
                            "test",
                            SUCCESS,
                            TransactionRecordAsserts.recordWith()
                                    .status(SUCCESS)
                                    .contractCallResult(resultWith()
                                            .resultThruAbi(
                                                    getABIFor(Utils.FunctionType.FUNCTION, "isAssociated", "HRC"),
                                                    isLiteralResult(new Object[] {true})))));
        }
    }
}
