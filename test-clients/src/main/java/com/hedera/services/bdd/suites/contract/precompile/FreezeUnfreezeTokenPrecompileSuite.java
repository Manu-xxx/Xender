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
package com.hedera.services.bdd.suites.contract.precompile;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.KNOWABLE_TOKEN;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FreezeUnfreezeTokenPrecompileSuite extends HapiApiSuite {
    private static final Logger log =
            LogManager.getLogger(FreezeUnfreezeTokenPrecompileSuite.class);
    private static final String FREEZE_CONTRACT = "FreezeUnfreezeContract";
    private static final String IS_FROZEN_FUNC = "isTokenFrozen";
    private static final String TOKEN_FREEZE_FUNC = "tokenFreeze";
    private static final String TOKEN_UNFREEZE_FUNC = "tokenUnfreeze";
    private static final String ACCOUNT = "anybody";
    private static final String FREEZE_KEY = "freezeKey";
    private static final String MULTI_KEY = "purpose";
    private static final long GAS_TO_OFFER = 4_000_000L;

    public static void main(String... args) {
        new FreezeUnfreezeTokenPrecompileSuite().runSuiteSync();
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(
                List.of(
                        freezeFungibleTokensHappyPath(),
                        freezeNftTokenHappyPath(),
                        unFreezeFungibleTokensHappyPath(),
                        unFreezeNftTokenHappyPath()));
    }

    private HapiApiSpec freezeFungibleTokensHappyPath() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final var isFrozenTxn = "isFrozenTxn";

        return defaultHapiSpec("freezeFungibleTokenHappyPath")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set)
                                .key(FREEZE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_FREEZE_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via("freezeTxn")
                                                        .gas(GAS_TO_OFFER),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                 IS_FROZEN_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via(isFrozenTxn)
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                isFrozenTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                HTSPrecompileResult
                                                                                        .FunctionType
                                                                                        .IS_FROZEN)
                                                                        .withStatus(SUCCESS)
                                                                        .withIsFrozen(true)))));
    }

    private HapiApiSpec unFreezeFungibleTokensHappyPath() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        final var isFrozenTxn = "isFrozenTxn";

        return defaultHapiSpec("unFreezeFungibleTokensHappyPath")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set)
                                .key(FREEZE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_UNFREEZE_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via("freezeTxn")
                                                        .gas(GAS_TO_OFFER),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                IS_FROZEN_FUNC,
                                                                asAddress(vanillaTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via(isFrozenTxn)
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                isFrozenTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                HTSPrecompileResult
                                                                                        .FunctionType
                                                                                        .IS_FROZEN)
                                                                        .withStatus(SUCCESS)
                                                                        .withIsFrozen(false)))));
    }

    private HapiApiSpec freezeNftTokenHappyPath() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> nftTokenID = new AtomicReference<>();
        final var isFrozenNftTxn = "isFrozenNftTxn";

        return defaultHapiSpec("freezeNftTokenHappyPath")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set)
                                .key(FREEZE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(KNOWABLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> nftTokenID.set(asToken(id))),
                        mintToken(KNOWABLE_TOKEN, List.of(copyFromUtf8("First!"))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, KNOWABLE_TOKEN),
                        cryptoTransfer(
                                movingUnique(KNOWABLE_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                        TOKEN_FREEZE_FUNC,
                                                                asAddress(nftTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .via("freezeNFTTxn")
                                                        .gas(GAS_TO_OFFER),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                IS_FROZEN_FUNC,
                                                                asAddress(nftTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via(isFrozenNftTxn)
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                isFrozenNftTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                HTSPrecompileResult
                                                                                        .FunctionType
                                                                                        .IS_FROZEN)
                                                                        .withStatus(SUCCESS)
                                                                        .withIsFrozen(true)))));
    }

    private HapiApiSpec unFreezeNftTokenHappyPath() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> nftTokenID = new AtomicReference<>();
        final var isFrozenNftTxn = "isFrozenNftTxn";

        return defaultHapiSpec("unFreezeNftTokenHappyPath")
                .given(
                        newKeyNamed(FREEZE_KEY),
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set)
                                .key(FREEZE_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(KNOWABLE_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .freezeKey(FREEZE_KEY)
                                .supplyKey(MULTI_KEY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> nftTokenID.set(asToken(id))),
                        mintToken(KNOWABLE_TOKEN, List.of(copyFromUtf8("First!"))),
                        uploadInitCode(FREEZE_CONTRACT),
                        contractCreate(FREEZE_CONTRACT),
                        tokenAssociate(ACCOUNT, KNOWABLE_TOKEN),
                        cryptoTransfer(
                                movingUnique(KNOWABLE_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                TOKEN_UNFREEZE_FUNC,
                                                                asAddress(nftTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .via("freezeNFTTxn")
                                                        .gas(GAS_TO_OFFER),
                                                contractCall(
                                                                FREEZE_CONTRACT,
                                                                IS_FROZEN_FUNC,
                                                                asAddress(nftTokenID.get()),
                                                                asAddress(accountID.get()))
                                                        .logged()
                                                        .payingWith(ACCOUNT)
                                                        .via(isFrozenNftTxn)
                                                        .gas(GAS_TO_OFFER))))
                .then(
                        childRecordsCheck(
                                isFrozenNftTxn,
                                SUCCESS,
                                recordWith()
                                        .status(SUCCESS)
                                        .contractCallResult(
                                                resultWith()
                                                        .contractCallResult(
                                                                htsPrecompileResult()
                                                                        .forFunction(
                                                                                HTSPrecompileResult
                                                                                        .FunctionType
                                                                                        .IS_FROZEN)
                                                                        .withStatus(SUCCESS)
                                                                        .withIsFrozen(false)))));
    }
}
