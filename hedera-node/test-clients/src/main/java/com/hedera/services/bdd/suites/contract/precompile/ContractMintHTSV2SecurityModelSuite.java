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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.hedera.services.bdd.spec.HapiPropertySource.asHexedSolidityAddress;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiSpec.propertyPreservingHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.SomeFungibleTransfers.changingFungibleBalances;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode.NONDETERMINISTIC_FUNCTION_PARAMETERS;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.expectedPrecompileGasFor;
import static com.hedera.services.bdd.suites.contract.Utils.getNestedContractAddress;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.services.bdd.suites.contract.precompile.V1SecurityModelOverrides.CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_RECEIVING_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FunctionType;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.keys.SigControl.Nature;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@HapiTestSuite
@SuppressWarnings("java:S1192") // "string literal should not be duplicated" - this rule makes test suites worse
public class ContractMintHTSV2SecurityModelSuite extends HapiSuite {

    private static final Logger LOG = LogManager.getLogger(ContractMintHTSV1SecurityModelSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String TOKEN_TREASURY = "treasury";
    private static final KeyShape TRESHOLD_KEY_SHAPE =
            KeyShape.threshOf(1, ED25519, CONTRACT);
    private static final String CONTRACT_KEY = "ContractKey";
    public static final String MINT_CONTRACT = "MintContract";
    private static final String HTS_CALLS = "HTSCalls";
    public static final String TRESHOLD_KEY_CORRECT_CONTRACT_ID = "tresholdKeyWithCorrectContractAndIncorrectSignerPublicKey";
    public static final String TRESHOLD_KEY_WITH_SIGNER_KEY = "tresholdKeyWithIncorrectContractAndCorrectSignerPublicKey";
    public static final String THRESHOLD_KEY = "Tresh1WithRandomEdKeyAndCorrectContractID";
    private static final String MULTI_KEY = "purpose";
    private static final String SIGNER = "anybody";
    private static final String SIGNER2 = "anybody";
    private static final String FUNGIBLE_TOKEN = "fungibleToken";
    private static final String SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS = "signerAndTokenHaveNoUpdatedKeys";
    private static final String TOKEN_HAS_NO_UPDATED_KEY = "tokenHasUpdatedContractKey";
    private static final String SIGNER_MINTS_WITH_CONTRACT_ID = "signerMintsAndTokenSupplyKeyHasTheIntermediaryContractId";
    private static final String TOKEN_WITH_CONTRACT_KEY = "tokenHasKeyWithTypeContract";
    private static final String SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY = "signerMintsAndTokenSupplyKeyHasTheSignerPublicKey";
    private static final String TREASURY_MINTS = "treasuryIsSignerWithUpdatedKeys";
    private static final String SIGNER_AND_PAYER_ARE_DIFFERENT = "signerAndPayerAreDifferentAccounts";
    private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
    private static final String TEST_METADATA_1 = "Test metadata 1";


    public static void main(final String... args) {
        new ContractMintHTSV2SecurityModelSuite().runSuiteSync();
    }


    public List<HapiSpec> getSpecsInSuite() {
        return allOf(positiveSpecs(), negativeSpecs());
    }

    List<HapiSpec> negativeSpecs() {
        return List.of(
                V2Security002FungibleTokenMintNegative(),
                V2Security003NonFungibleTokenMintNegative()
        );
    }

    List<HapiSpec> positiveSpecs() {
        return List.of(
                V2Security002FungibleTokenMintPositive(),
                V2Security003NonFungibleTokenMintPositive()
         );
    }


    @HapiTest
    final HapiSpec V2Security002FungibleTokenMintPositive() {
        final var amount = 10L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("V2Security002FungibleTokenMintPositive")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER2),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))),
                        uploadInitCode(HTS_CALLS),
                        contractCreate(HTS_CALLS),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                            MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get())))))
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        newKeyNamed(TRESHOLD_KEY_CORRECT_CONTRACT_ID)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, HTS_CALLS))),
                        newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(HTS_CALLS)),
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                        contractCall(
                                HTS_CALLS,
                                "mintTokenCall",
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                BigInteger.valueOf(amount),
                                new byte[][] {})
                                .via(SIGNER_MINTS_WITH_CONTRACT_ID)
                                .gas(GAS_TO_OFFER)
                                .payingWith(SIGNER),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(amount),
                        contractCall(
                                HTS_CALLS,
                                "mintTokenCall",
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                BigInteger.valueOf(amount),
                                new byte[][] {})
                                .via(TREASURY_MINTS)
                                .gas(GAS_TO_OFFER)
                                .signedBy(TOKEN_TREASURY)
                                .payingWith(TOKEN_TREASURY),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(2 * amount),
                        contractCall(
                                HTS_CALLS,
                                "mintTokenCall",
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                BigInteger.valueOf(amount),
                                new byte[][] {})
                                .via(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .gas(GAS_TO_OFFER)
                                .signedBy(SIGNER2, TOKEN_TREASURY)
                                .payingWith(SIGNER2),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(3 * amount),
                                tokenUpdate(FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                                contractCall(
                                        HTS_CALLS,
                                        "mintTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(amount),
                                        new byte[][] {})
                                        .via(TOKEN_WITH_CONTRACT_KEY)
                                        .gas(GAS_TO_OFFER)
                                        .signedBy(SIGNER)
                                        .payingWith(SIGNER),
                                getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(4 * amount)
                        )))
                .then(
                        getTxnRecord(SIGNER_MINTS_WITH_CONTRACT_ID)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(TREASURY_MINTS)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(TOKEN_WITH_CONTRACT_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS))
        );
    }

    @HapiTest
    final HapiSpec V2Security003NonFungibleTokenMintPositive() {
        final var amount = 1;
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("V2Security003NonFungibleTokenMintPositive")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER2),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> nonFungible.set(asToken(idLit))),
                        uploadInitCode(HTS_CALLS),
                        contractCreate(HTS_CALLS),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(nonFungible.get())))))
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                                spec,
                                newKeyNamed(TRESHOLD_KEY_CORRECT_CONTRACT_ID)
                                        .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, HTS_CALLS))),
                                newKeyNamed(CONTRACT_KEY).shape(CONTRACT.signedWith(HTS_CALLS)),
                                tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_CORRECT_CONTRACT_ID),
                                contractCall(
                                        HTS_CALLS,
                                        "mintTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(0L),
                                        new byte[][] {TEST_METADATA_1.getBytes()})
                                        .via(SIGNER_MINTS_WITH_CONTRACT_ID)
                                        .gas(GAS_TO_OFFER)
                                        .payingWith(SIGNER),
                                getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(amount),
                                contractCall(
                                        HTS_CALLS,
                                        "mintTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(0L),
                                        new byte[][] {TEST_METADATA_1.getBytes()})
                                        .via(TREASURY_MINTS)
                                        .gas(GAS_TO_OFFER)
                                        .signedBy(TOKEN_TREASURY)
                                        .payingWith(TOKEN_TREASURY),
                                getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(2 * amount),
                                contractCall(
                                        HTS_CALLS,
                                        "mintTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(0L),
                                        new byte[][] {TEST_METADATA_1.getBytes()})
                                        .via(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                        .gas(GAS_TO_OFFER)
                                        .signedBy(SIGNER2, TOKEN_TREASURY)
                                        .payingWith(SIGNER2),
                                getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(3 * amount),
                                tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(CONTRACT_KEY),
                                contractCall(
                                        HTS_CALLS,
                                        "mintTokenCall",
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                        BigInteger.valueOf(0L),
                                        new byte[][] {TEST_METADATA_1.getBytes()})
                                        .via(TOKEN_WITH_CONTRACT_KEY)
                                        .gas(GAS_TO_OFFER)
                                        .signedBy(SIGNER)
                                        .payingWith(SIGNER),
                                getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(4 * amount)
                        )))
                .then(
                        getTxnRecord(SIGNER_MINTS_WITH_CONTRACT_ID)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(TREASURY_MINTS)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(SIGNER_AND_PAYER_ARE_DIFFERENT)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS)),
                        getTxnRecord(TOKEN_WITH_CONTRACT_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(SUCCESS))
                );
    }

    @HapiTest
    final HapiSpec V2Security002FungibleTokenMintNegative() {
        final var amount = 10L;
        final AtomicReference<TokenID> fungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("V2Security002FungibleTokenMintNegative")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(FUNGIBLE_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> fungible.set(asToken(idLit))),
        uploadInitCode(HTS_CALLS),
                        contractCreate(HTS_CALLS),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(fungible.get())))))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(THRESHOLD_KEY)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, HTS_CALLS))),
                        newKeyNamed(TRESHOLD_KEY_WITH_SIGNER_KEY)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, MINT_CONTRACT))),
                        contractCall(
                                HTS_CALLS,
                                "mintTokenCall",
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                BigInteger.valueOf(amount),
                                new byte[][] {})
                                .via(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .gas(GAS_TO_OFFER)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(0L),
                        cryptoUpdate(SIGNER).key(TRESHOLD_KEY_WITH_SIGNER_KEY),
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_WITH_SIGNER_KEY),
                        contractCall(
                                HTS_CALLS,
                                "mintTokenCall",
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                BigInteger.valueOf(amount),
                                new byte[][] {})
                                .via(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(SIGNER)
                                .payingWith(SIGNER),
                        tokenUpdate(FUNGIBLE_TOKEN).supplyKey(MULTI_KEY),
                        cryptoUpdate(SIGNER).key(THRESHOLD_KEY),
                        contractCall(
                                HTS_CALLS,
                                "mintTokenCall",
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))),
                                BigInteger.valueOf(amount),
                                new byte[][] {})
                                .via(TOKEN_HAS_NO_UPDATED_KEY)
                                .gas(GAS_TO_OFFER)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER),
                        getTokenInfo(FUNGIBLE_TOKEN).hasTotalSupply(0L)
                )))
                .then(
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                         getTxnRecord(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        getTxnRecord(TOKEN_HAS_NO_UPDATED_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                );
    }

    @HapiTest
    final HapiSpec V2Security003NonFungibleTokenMintNegative() {
        final AtomicReference<TokenID> nonFungible = new AtomicReference<>();

        return propertyPreservingHapiSpec("V2Security003NonFungibleTokenMintNegative")
                .preserving(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS)
                .given(
                        overriding(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, CONTRACTS_V2_SECURITY_MODEL_BLOCK_CUTOFF),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(SIGNER).balance(ONE_MILLION_HBARS),
                        tokenCreate(NON_FUNGIBLE_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .initialSupply(0)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .supplyKey(TOKEN_TREASURY)
                                .exposingCreatedIdTo(idLit -> nonFungible.set(asToken(idLit))),
                        uploadInitCode(HTS_CALLS),
                        contractCreate(HTS_CALLS),
                        uploadInitCode(MINT_CONTRACT),
                        sourcing(() -> contractCreate(
                                MINT_CONTRACT, HapiParserUtil.asHeadlongAddress(asAddress(nonFungible.get())))))
                .when(
                        withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        newKeyNamed(MULTI_KEY),
                        newKeyNamed(THRESHOLD_KEY)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, HTS_CALLS))),
                        newKeyNamed(TRESHOLD_KEY_WITH_SIGNER_KEY)
                                .shape(TRESHOLD_KEY_SHAPE.signedWith(sigs(ON, MINT_CONTRACT))),
                        contractCall(
                                HTS_CALLS,
                                "mintTokenCall",
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                BigInteger.valueOf(0L),
                                new byte[][] {TEST_METADATA_1.getBytes()})
                                .via(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .gas(GAS_TO_OFFER)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(0),
                        cryptoUpdate(SIGNER).key(TRESHOLD_KEY_WITH_SIGNER_KEY),
                        tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(TRESHOLD_KEY_WITH_SIGNER_KEY),
                        contractCall(
                                HTS_CALLS,
                                "mintTokenCall",
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                BigInteger.valueOf(0L),
                                new byte[][] {TEST_METADATA_1.getBytes()})
                                .via(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
                                .gas(GAS_TO_OFFER)
                                .alsoSigningWithFullPrefix(SIGNER)
                                .payingWith(SIGNER),
                                getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(0),
                                tokenUpdate(NON_FUNGIBLE_TOKEN).supplyKey(MULTI_KEY),
                        cryptoUpdate(SIGNER).key(THRESHOLD_KEY),
                        contractCall(
                                HTS_CALLS,
                                "mintTokenCall",
                                HapiParserUtil.asHeadlongAddress(
                                        asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))),
                                BigInteger.valueOf(0L),
                                new byte[][] {TEST_METADATA_1.getBytes()})
                                .via(TOKEN_HAS_NO_UPDATED_KEY)
                                .gas(GAS_TO_OFFER)
                                .signedBy(SIGNER)
                                .payingWith(SIGNER),
                        getTokenInfo(NON_FUNGIBLE_TOKEN).hasTotalSupply(0)
                )))
                .then(
                        getTxnRecord(SIGNER_AND_TOKEN_HAVE_NO_UPDATED_KEYS)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        getTxnRecord(SIGNER_MINTS_WITH_SIGNER_PUBLIC_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE)),
                        getTxnRecord(TOKEN_HAS_NO_UPDATED_KEY)
                                .andAllChildRecords()
                                .hasChildRecords(recordWith().status(INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE))
                );
    }

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }
}
