/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
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
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class DeleteTokenPrecompileSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(DeleteTokenPrecompileSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String DELETE_TOKEN_CONTRACT = "DeleteTokenContract";
    private static final String TOKEN_DELETE_FUNCTION = "tokenDelete";
    private static final String ACCOUNT = "anybody";
    private static final String MULTI_KEY = "purpose";
    private static final String DELETE_TXN = "deleteTxn";
    final AtomicReference<AccountID> accountID = new AtomicReference<>();

    public static void main(String... args) {
        new DeleteTokenPrecompileSuite().runSuiteSync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return allOf(List.of(deleteFungibleTokenHappyPath(), deleteNftTokenHappyPath()));
    }

    private HapiApiSpec deleteFungibleTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return defaultHapiSpec("deleteFungibleTokenHappyPath")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .key(MULTI_KEY)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                                .initialSupply(1110),
                        uploadInitCode(DELETE_TOKEN_CONTRACT),
                        contractCreate(DELETE_TOKEN_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                DELETE_TOKEN_CONTRACT,
                                                                TOKEN_DELETE_FUNCTION,
                                                                asAddress(vanillaTokenID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .gas(GAS_TO_OFFER)
                                                        .via(DELETE_TXN))))
                .then(getTokenInfo(VANILLA_TOKEN).isDeleted().logged());
    }

    private HapiApiSpec deleteNftTokenHappyPath() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();

        return defaultHapiSpec("deleteNftTokenHappyPath")
                .given(
                        newKeyNamed(MULTI_KEY),
                        cryptoCreate(ACCOUNT)
                                .key(MULTI_KEY)
                                .balance(100 * ONE_HBAR)
                                .exposingCreatedIdTo(accountID::set),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id)))
                                .initialSupply(0),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        uploadInitCode(DELETE_TOKEN_CONTRACT),
                        contractCreate(DELETE_TOKEN_CONTRACT),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        cryptoTransfer(
                                movingUnique(VANILLA_TOKEN, 1L).between(TOKEN_TREASURY, ACCOUNT)))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCall(
                                                                DELETE_TOKEN_CONTRACT,
                                                                TOKEN_DELETE_FUNCTION,
                                                                asAddress(vanillaTokenID.get()))
                                                        .payingWith(ACCOUNT)
                                                        .gas(GAS_TO_OFFER)
                                                        .via(DELETE_TXN))))
                .then(getTokenInfo(VANILLA_TOKEN).isDeleted().logged());
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
