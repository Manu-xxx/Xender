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

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.spec.HapiPropertySource.asToken;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

public class DelegatePrecompileSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(DelegatePrecompileSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final KeyShape SIMPLE_AND_DELEGATE_KEY_SHAPE =
            KeyShape.threshOf(1, KeyShape.SIMPLE, DELEGATE_CONTRACT);
    private static final KeyShape DELEGATE_CONTRACT_KEY_SHAPE =
            KeyShape.threshOf(1, DELEGATE_CONTRACT);
    private static final String TOKEN_TREASURY = "treasury";
    private static final String OUTER_CONTRACT = "DelegateContract";
    private static final String NESTED_CONTRACT = "ServiceContract";
    private static final String ACCOUNT = "anybody";
    private static final String RECEIVER = "receiver";
    private static final String DELEGATE_KEY = "Delegate key";
    private static final String SIMPLE_AND_DELEGATE_KEY_NAME = "Simple And Delegate key";
    private static final String SUPPLY_KEY = "supplyKey";

    public static void main(String... args) {
        new DelegatePrecompileSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(delegateCallForTransfer(), delegateCallForBurn(), delegateCallForMint());
    }

    private HapiSpec delegateCallForTransfer() {
        final AtomicReference<AccountID> accountID = new AtomicReference<>();
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();
        final AtomicReference<AccountID> receiverID = new AtomicReference<>();

        return defaultHapiSpec("delegateCallForTransfer")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        cryptoCreate(ACCOUNT).exposingCreatedIdTo(accountID::set),
                        cryptoCreate(RECEIVER).exposingCreatedIdTo(receiverID::set),
                        uploadInitCode(OUTER_CONTRACT, NESTED_CONTRACT),
                        contractCreate(NESTED_CONTRACT),
                        tokenAssociate(NESTED_CONTRACT, VANILLA_TOKEN),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        tokenAssociate(RECEIVER, VANILLA_TOKEN),
                        cryptoTransfer(
                                        movingUnique(VANILLA_TOKEN, 1L)
                                                .between(TOKEN_TREASURY, ACCOUNT))
                                .payingWith(GENESIS))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(
                                                        OUTER_CONTRACT,
                                                        asHeadlongAddress(
                                                                getNestedContractAddress(
                                                                        NESTED_CONTRACT, spec))),
                                                tokenAssociate(OUTER_CONTRACT, VANILLA_TOKEN),
                                                newKeyNamed(SIMPLE_AND_DELEGATE_KEY_NAME)
                                                        .shape(
                                                                SIMPLE_AND_DELEGATE_KEY_SHAPE
                                                                        .signedWith(
                                                                                sigs(
                                                                                        ON,
                                                                                        OUTER_CONTRACT))),
                                                cryptoUpdate(ACCOUNT)
                                                        .key(SIMPLE_AND_DELEGATE_KEY_NAME),
                                                contractCall(
                                                                OUTER_CONTRACT,
                                                                "transferDelegateCall",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenTokenID
                                                                                        .get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(accountID.get())),
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                receiverID.get())),
                                                                1L)
                                                        .payingWith(GENESIS)
                                                        .via(
                                                                "delegateTransferCallWithDelegateContractKeyTxn")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(
                        getAccountBalance(ACCOUNT).hasTokenBalance(VANILLA_TOKEN, 1),
                        getAccountBalance(RECEIVER).hasTokenBalance(VANILLA_TOKEN, 0));
    }

    private HapiSpec delegateCallForBurn() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

        return defaultHapiSpec("delegateCallForBurn")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .supplyKey(SUPPLY_KEY)
                                .adminKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0L)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("First!"))),
                        mintToken(VANILLA_TOKEN, List.of(copyFromUtf8("Second!"))),
                        uploadInitCode(OUTER_CONTRACT, NESTED_CONTRACT),
                        contractCreate(NESTED_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(
                                                        OUTER_CONTRACT,
                                                        asHeadlongAddress(
                                                                getNestedContractAddress(
                                                                        NESTED_CONTRACT, spec))),
                                                newKeyNamed(DELEGATE_KEY)
                                                        .shape(
                                                                DELEGATE_CONTRACT_KEY_SHAPE
                                                                        .signedWith(
                                                                                sigs(
                                                                                        OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(DELEGATE_KEY),
                                                contractCall(
                                                                OUTER_CONTRACT,
                                                                "burnDelegateCall",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenTokenID
                                                                                        .get())),
                                                                BigInteger.ZERO,
                                                                new long[] {1L})
                                                        .payingWith(GENESIS)
                                                        .via(
                                                                "delegateBurnCallWithDelegateContractKeyTxn")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 2));
    }

    private HapiSpec delegateCallForMint() {
        final AtomicReference<TokenID> vanillaTokenTokenID = new AtomicReference<>();

        return defaultHapiSpec("delegateCallForMint")
                .given(
                        newKeyNamed(SUPPLY_KEY),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(TokenType.FUNGIBLE_COMMON)
                                .supplyKey(SUPPLY_KEY)
                                .adminKey(SUPPLY_KEY)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(50L)
                                .exposingCreatedIdTo(id -> vanillaTokenTokenID.set(asToken(id))),
                        uploadInitCode(OUTER_CONTRACT, NESTED_CONTRACT),
                        contractCreate(NESTED_CONTRACT))
                .when(
                        withOpContext(
                                (spec, opLog) ->
                                        allRunFor(
                                                spec,
                                                contractCreate(
                                                        OUTER_CONTRACT,
                                                        asHeadlongAddress(
                                                                getNestedContractAddress(
                                                                        NESTED_CONTRACT, spec))),
                                                newKeyNamed(DELEGATE_KEY)
                                                        .shape(
                                                                DELEGATE_CONTRACT_KEY_SHAPE
                                                                        .signedWith(
                                                                                sigs(
                                                                                        OUTER_CONTRACT))),
                                                tokenUpdate(VANILLA_TOKEN).supplyKey(DELEGATE_KEY),
                                                contractCall(
                                                                OUTER_CONTRACT,
                                                                "mintDelegateCall",
                                                                HapiParserUtil.asHeadlongAddress(
                                                                        asAddress(
                                                                                vanillaTokenTokenID
                                                                                        .get())),
                                                                BigInteger.ONE)
                                                        .payingWith(GENESIS)
                                                        .via(
                                                                "delegateBurnCallWithDelegateContractKeyTxn")
                                                        .gas(GAS_TO_OFFER)
                                                        .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(getAccountBalance(TOKEN_TREASURY).hasTokenBalance(VANILLA_TOKEN, 50));
    }

    @NotNull
    private String getNestedContractAddress(final String outerContract, final HapiSpec spec) {
        return AssociatePrecompileSuite.getNestedContractAddress(outerContract, spec);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
