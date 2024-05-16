/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.DELEGATE_CONTRACT;
import static com.hedera.services.bdd.spec.keys.KeyShape.ED25519;
import static com.hedera.services.bdd.spec.keys.KeyShape.SECP256K1;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.SigControl.ED25519_ON;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.emptyChildRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_NOT_PROVIDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(SMART_CONTRACT)
public class TokenUpdatePrecompileSuite extends HapiSuite {

    private static final Logger log = LogManager.getLogger(TokenUpdatePrecompileSuite.class);

    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final long AUTO_RENEW_PERIOD = 8_000_000L;
    private static final String ACCOUNT = "account";
    private static final String VANILLA_TOKEN = "TokenD";
    private static final String NFT_TOKEN = "TokenD";
    private static final String MULTI_KEY = "multiKey";
    private static final String UPDATE_KEY_FUNC = "tokenUpdateKeys";
    private static final String GET_KEY_FUNC = "getKeyFromToken";
    public static final String TOKEN_UPDATE_CONTRACT = "UpdateTokenInfoContract";
    private static final String UPDATE_TXN = "updateTxn";
    private static final String NO_ADMIN_KEY = "noAdminKeyTxn";
    private static final long DEFAULT_AMOUNT_TO_SEND = 20 * ONE_HBAR;
    private static final String ED25519KEY = "ed25519key";
    private static final String ECDSA_KEY = "ecdsa";
    public static final String TOKEN_UPDATE_AS_KEY = "tokenCreateContractAsKey";
    private static final String DELEGATE_KEY = "tokenUpdateAsKeyDelegate";
    private static final String ACCOUNT_TO_ASSOCIATE = "account3";
    private static final String ACCOUNT_TO_ASSOCIATE_KEY = "associateKey";
    private static final String AUTO_RENEW_ACCOUNT = "autoRenewAccount";
    public static final String CUSTOM_NAME = "customName";
    public static final String CUSTOM_SYMBOL = "Ω";
    public static final String CUSTOM_MEMO = "Omega";
    private static final long ADMIN_KEY_TYPE = 1L;
    private static final long SUPPLY_KEY_TYPE = 16L;
    private static final KeyShape KEY_SHAPE = KeyShape.threshOf(1, ED25519, CONTRACT);

    public static void main(String... args) {
        new TokenUpdatePrecompileSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return allOf(negativeCases(), positiveCases());
    }

    List<HapiSpec> negativeCases() {
        return List.of(
                updateTokenWithInvalidKeyValues(),
                updateNftTokenKeysWithWrongTokenIdAndMissingAdminKey(),
                getTokenKeyForNonFungibleNegative());
    }

    List<HapiSpec> positiveCases() {
        return List.of(
                tokenUpdateSingleFieldCases(),
                createFungibleTokenAdminKeyFromHollowAccountAlias(),
                createNFTTokenAdminKeyFromHollowAccountAlias());
    }

    @HapiTest
    final HapiSpec updateTokenWithInvalidKeyValues() {
        final AtomicReference<TokenID> vanillaTokenID = new AtomicReference<>();
        return defaultHapiSpec("updateTokenWithInvalidKeyValues")
                .given(
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(MULTI_KEY).shape(ED25519_ON),
                        cryptoCreate(TOKEN_TREASURY),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(MULTI_KEY),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(VANILLA_TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> vanillaTokenID.set(asToken(id))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN),
                        grantTokenKyc(VANILLA_TOKEN, ACCOUNT),
                        cryptoTransfer(moving(500, VANILLA_TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        "updateTokenWithInvalidKeyValues",
                                        HapiParserUtil.asHeadlongAddress(asAddress(vanillaTokenID.get())),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getAccountID(ACCOUNT))),
                                        AUTO_RENEW_PERIOD)
                                .via(UPDATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        newKeyNamed(DELEGATE_KEY).shape(DELEGATE_CONTRACT.signedWith(TOKEN_UPDATE_CONTRACT)),
                        newKeyNamed(TOKEN_UPDATE_AS_KEY).shape(CONTRACT.signedWith(TOKEN_UPDATE_CONTRACT)))))
                .then(sourcing(() -> emptyChildRecordsCheck(UPDATE_TXN, CONTRACT_REVERT_EXECUTED)));
    }

    @HapiTest
    public HapiSpec updateNftTokenKeysWithWrongTokenIdAndMissingAdminKey() {
        final AtomicReference<TokenID> nftToken = new AtomicReference<>();
        return defaultHapiSpec("updateNftTokenKeysWithWrongTokenIdAndMissingAdminKey")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(ECDSA_KEY).shape(SECP256K1),
                        newKeyNamed(ACCOUNT_TO_ASSOCIATE_KEY),
                        newKeyNamed(MULTI_KEY).shape(ED25519_ON),
                        cryptoCreate(ACCOUNT).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
                        cryptoCreate(ACCOUNT_TO_ASSOCIATE).key(ACCOUNT_TO_ASSOCIATE_KEY),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> nftToken.set(asToken(id))),
                        mintToken(NFT_TOKEN, List.of(ByteString.copyFromUtf8("nft4"))),
                        tokenAssociate(ACCOUNT, NFT_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        UPDATE_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(new byte[20]),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_UPDATE_CONTRACT))))
                                .via(UPDATE_TXN)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        UPDATE_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(nftToken.get())),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        spec.registry()
                                                .getKey(ECDSA_KEY)
                                                .getECDSASecp256K1()
                                                .toByteArray(),
                                        HapiParserUtil.asHeadlongAddress(
                                                asAddress(spec.registry().getContractId(TOKEN_UPDATE_CONTRACT))))
                                .via(NO_ADMIN_KEY)
                                .gas(GAS_TO_OFFER)
                                .sending(DEFAULT_AMOUNT_TO_SEND)
                                .hasRetryPrecheckFrom(BUSY)
                                .payingWith(ACCOUNT)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                UPDATE_TXN,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                NO_ADMIN_KEY,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(TOKEN_IS_IMMUTABLE)))));
    }

    @HapiTest
    public HapiSpec getTokenKeyForNonFungibleNegative() {
        final AtomicReference<TokenID> nftToken = new AtomicReference<>();
        return defaultHapiSpec("getTokenKeyForNonFungibleNegative")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        newKeyNamed(MULTI_KEY).shape(ED25519_ON),
                        cryptoCreate(ACCOUNT).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
                        uploadInitCode(TOKEN_UPDATE_CONTRACT),
                        contractCreate(TOKEN_UPDATE_CONTRACT).gas(GAS_TO_OFFER),
                        tokenCreate(NFT_TOKEN)
                                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0)
                                .supplyKey(MULTI_KEY)
                                .exposingCreatedIdTo(id -> nftToken.set(asToken(id))),
                        mintToken(NFT_TOKEN, List.of(ByteString.copyFromUtf8("nft5"))),
                        tokenAssociate(ACCOUNT, VANILLA_TOKEN))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        contractCallLocal(
                                TOKEN_UPDATE_CONTRACT,
                                GET_KEY_FUNC,
                                HapiParserUtil.asHeadlongAddress(asAddress(nftToken.get())),
                                BigInteger.valueOf(SUPPLY_KEY_TYPE)),
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        GET_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(nftToken.get())),
                                        BigInteger.valueOf(89L))
                                .via("Invalid_Key_Type")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        GET_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(new byte[20]),
                                        BigInteger.valueOf(SUPPLY_KEY_TYPE))
                                .via("InvalidTokenId")
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        contractCall(
                                        TOKEN_UPDATE_CONTRACT,
                                        GET_KEY_FUNC,
                                        HapiParserUtil.asHeadlongAddress(asAddress(nftToken.get())),
                                        BigInteger.valueOf(ADMIN_KEY_TYPE))
                                .via(NO_ADMIN_KEY)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED))))
                .then(withOpContext((spec, opLog) -> allRunFor(
                        spec,
                        childRecordsCheck(
                                "InvalidTokenId",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                NO_ADMIN_KEY,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(KEY_NOT_PROVIDED)
                                // .contractCallResult(ContractFnResultAsserts.resultWith()
                                //
                                // .contractCallResult(htsPrecompileResult()
                                //
                                // .forFunction(FunctionType.HAPI_GET_TOKEN_KEY)
                                //                                                        .withStatus(KEY_NOT_PROVIDED)
                                //
                                // .withTokenKeyValue(Key.newBuilder().build()))))
                                ))));
    }

    @HapiTest
    final HapiSpec tokenUpdateSingleFieldCases() {
        final var tokenInfoUpdateContract = "TokenInfoSingularUpdate";
        final var newTokenTreasury = "new treasury";
        final var oldName = "Old Name";
        final var sym = "SYM";
        final var memo = "Memo";
        final var newName = "New Name";
        final var sym1 = "SYM1";
        final var newMemo = "New Memo";
        final var updateToEdKey = "updateTokenKeyEd";
        final var updateToContractId = "updateTokenKeyContractId";
        final var contractIdKey = "contractIdKey";
        final AtomicReference<TokenID> token = new AtomicReference<>();
        final AtomicReference<AccountID> newTreasury = new AtomicReference<>();
        final AtomicReference<AccountID> autoRenewAccount = new AtomicReference<>();
        return defaultHapiSpec("tokenUpdateNegativeCases")
                .given(
                        cryptoCreate(TOKEN_TREASURY),
                        uploadInitCode(tokenInfoUpdateContract),
                        contractCreate(tokenInfoUpdateContract).gas(GAS_TO_OFFER),
                        newKeyNamed(MULTI_KEY).shape(KEY_SHAPE.signedWith(sigs(ON, tokenInfoUpdateContract))),
                        newKeyNamed(ED25519KEY).shape(ED25519),
                        newKeyNamed(contractIdKey).shape(CONTRACT.signedWith(tokenInfoUpdateContract)),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS).key(MULTI_KEY),
                        cryptoCreate(newTokenTreasury).key(MULTI_KEY).exposingCreatedIdTo(newTreasury::set),
                        cryptoCreate(AUTO_RENEW_ACCOUNT)
                                .balance(ONE_MILLION_HBARS)
                                .key(MULTI_KEY)
                                .exposingCreatedIdTo(autoRenewAccount::set),
                        tokenCreate(TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .name(oldName)
                                .symbol(sym)
                                .entityMemo(memo)
                                .autoRenewAccount(ACCOUNT)
                                .autoRenewPeriod(AUTO_RENEW_PERIOD)
                                .supplyType(TokenSupplyType.INFINITE)
                                .treasury(TOKEN_TREASURY)
                                .adminKey(MULTI_KEY)
                                .supplyKey(MULTI_KEY)
                                .feeScheduleKey(MULTI_KEY)
                                .pauseKey(MULTI_KEY)
                                .wipeKey(MULTI_KEY)
                                .freezeKey(MULTI_KEY)
                                .kycKey(MULTI_KEY)
                                .initialSupply(1_000)
                                .exposingCreatedIdTo(id -> token.set(asToken(id))),
                        tokenAssociate(ACCOUNT, TOKEN),
                        tokenAssociate(AUTO_RENEW_ACCOUNT, TOKEN),
                        tokenAssociate(newTokenTreasury, TOKEN),
                        grantTokenKyc(TOKEN, ACCOUNT),
                        cryptoTransfer(moving(500, TOKEN).between(TOKEN_TREASURY, ACCOUNT)))
                .when(withOpContext((spec, opLog) -> allRunFor(
                        spec,

                        // Try updating token name
                        contractCall(
                                        tokenInfoUpdateContract,
                                        "updateTokenName",
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        newName)
                                .via("updateOnlyName")
                                .logged()
                                .signingWith(MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym)
                                .hasName(newName)
                                .hasEntityMemo(memo)
                                .hasTreasury(TOKEN_TREASURY)
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(MULTI_KEY)
                                .hasKycKey(MULTI_KEY)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try updating token symbol
                        contractCall(
                                        tokenInfoUpdateContract,
                                        "updateTokenSymbol",
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        sym1)
                                .via("updateOnlySym")
                                .logged()
                                .signingWith(MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(memo)
                                .hasTreasury(TOKEN_TREASURY)
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(MULTI_KEY)
                                .hasKycKey(MULTI_KEY)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try updating token memo
                        contractCall(
                                        tokenInfoUpdateContract,
                                        "updateTokenMemo",
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        newMemo)
                                .via("updateOnlyMemo")
                                .logged()
                                .signingWith(MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(TOKEN_TREASURY)
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(MULTI_KEY)
                                .hasKycKey(MULTI_KEY)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try updating token treasury
                        contractCall(
                                        tokenInfoUpdateContract,
                                        "updateTokenTreasury",
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(newTreasury.get())))
                                .via("updateOnlyTreasury")
                                .logged()
                                .signingWith(MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(MULTI_KEY)
                                .hasKycKey(MULTI_KEY)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try updating auto-renew account
                        contractCall(
                                        tokenInfoUpdateContract,
                                        "updateTokenAutoRenewAccount",
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(autoRenewAccount.get())))
                                .via("updateOnlyAutoRenewAccount")
                                .logged()
                                .signingWith(MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(MULTI_KEY)
                                .hasKycKey(MULTI_KEY)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try updating auto-renew period
                        contractCall(
                                        tokenInfoUpdateContract,
                                        "updateTokenAutoRenewPeriod",
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        AUTO_RENEW_PERIOD - 1000L)
                                .via("updateOnlyAutoRenewPeriod")
                                .logged()
                                .signingWith(MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(MULTI_KEY)
                                .hasKycKey(MULTI_KEY)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try update token admin key
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToEdKey,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        0)
                                .via("updateOnlyAdminKeyWithEd")
                                .logged()
                                .signedBy(ED25519KEY, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(MULTI_KEY)
                                .hasKycKey(MULTI_KEY)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToContractId,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getKey(contractIdKey)
                                                .getContractID())),
                                        0)
                                .via("updateOnlyAdminKey")
                                .logged()
                                .signedBy(ED25519KEY, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(MULTI_KEY)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try update token kyc key
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToEdKey,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        1)
                                .via("updateOnlyKycKeyWithEd")
                                .logged()
                                .signedBy(ED25519KEY, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(ED25519KEY)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToContractId,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getKey(contractIdKey)
                                                .getContractID())),
                                        1)
                                .via("updateOnlyKycKey")
                                .logged()
                                .signedBy(MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(MULTI_KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try update token freeze key
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToEdKey,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        2)
                                .via("updateOnlyFreezeKeyWithEd")
                                .logged()
                                .signedBy(contractIdKey, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(ED25519KEY)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToContractId,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getKey(contractIdKey)
                                                .getContractID())),
                                        2)
                                .via("updateOnlyFreezeKey")
                                .logged()
                                .signedBy(ED25519KEY, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(contractIdKey)
                                .hasWipeKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try update token wipe key
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToEdKey,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        3)
                                .via("updateOnlyWipeKeyWithEd")
                                .logged()
                                .signedBy(contractIdKey, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(contractIdKey)
                                .hasWipeKey(ED25519KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToContractId,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getKey(contractIdKey)
                                                .getContractID())),
                                        3)
                                .via("updateOnlyWipeKey")
                                .logged()
                                .signedBy(contractIdKey, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(contractIdKey)
                                .hasWipeKey(contractIdKey)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasSupplyKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try update token supply key
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToEdKey,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        4)
                                .via("updateOnlySupplyKeyWithEd")
                                .logged()
                                .signedBy(contractIdKey, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(contractIdKey)
                                .hasWipeKey(contractIdKey)
                                .hasSupplyKey(ED25519KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToContractId,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getKey(contractIdKey)
                                                .getContractID())),
                                        4)
                                .via("updateOnlySupplyKey")
                                .logged()
                                .signedBy(contractIdKey, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(contractIdKey)
                                .hasWipeKey(contractIdKey)
                                .hasSupplyKey(contractIdKey)
                                .hasPauseKey(MULTI_KEY)
                                .hasFeeScheduleKey(MULTI_KEY)
                                .hasPauseKey(MULTI_KEY),

                        // Try update token fee schedule key
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToEdKey,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        5)
                                .via("updateOnlyFeeKeyWithEd")
                                .logged()
                                .signedBy(contractIdKey, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(contractIdKey)
                                .hasWipeKey(contractIdKey)
                                .hasSupplyKey(contractIdKey)
                                .hasFeeScheduleKey(ED25519KEY)
                                .hasPauseKey(MULTI_KEY),
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToContractId,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getKey(contractIdKey)
                                                .getContractID())),
                                        5)
                                .via("updateOnlyFeeKey")
                                .logged()
                                .signedBy(contractIdKey, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(contractIdKey)
                                .hasWipeKey(contractIdKey)
                                .hasSupplyKey(contractIdKey)
                                .hasFeeScheduleKey(contractIdKey)
                                .hasPauseKey(MULTI_KEY),

                        // Try update token pause key
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToEdKey,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        spec.registry()
                                                .getKey(ED25519KEY)
                                                .getEd25519()
                                                .toByteArray(),
                                        6)
                                .via("updateOnlyPauseKeyWithEd")
                                .logged()
                                .signedBy(contractIdKey, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(contractIdKey)
                                .hasWipeKey(contractIdKey)
                                .hasSupplyKey(contractIdKey)
                                .hasFeeScheduleKey(contractIdKey)
                                .hasPauseKey(ED25519KEY),
                        contractCall(
                                        tokenInfoUpdateContract,
                                        updateToContractId,
                                        HapiParserUtil.asHeadlongAddress(asAddress(token.get())),
                                        HapiParserUtil.asHeadlongAddress(asAddress(spec.registry()
                                                .getKey(contractIdKey)
                                                .getContractID())),
                                        6)
                                .via("updateOnlyPauseKey")
                                .logged()
                                .signedBy(contractIdKey, MULTI_KEY)
                                .payingWith(ACCOUNT)
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS),
                        getTokenInfo(TOKEN)
                                .logged()
                                .hasTokenType(TokenType.FUNGIBLE_COMMON)
                                .hasSymbol(sym1)
                                .hasName(newName)
                                .hasEntityMemo(newMemo)
                                .hasTreasury(newTokenTreasury)
                                .hasAutoRenewAccount(AUTO_RENEW_ACCOUNT)
                                .hasAutoRenewPeriod(AUTO_RENEW_PERIOD - 1000L)
                                .hasSupplyType(TokenSupplyType.INFINITE)
                                .searchKeysGlobally()
                                .hasAdminKey(contractIdKey)
                                .hasKycKey(contractIdKey)
                                .hasFreezeKey(contractIdKey)
                                .hasWipeKey(contractIdKey)
                                .hasSupplyKey(contractIdKey)
                                .hasFeeScheduleKey(contractIdKey)
                                .hasPauseKey(contractIdKey))))
                .then(
                        childRecordsCheck(
                                "updateOnlyName", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck("updateOnlySym", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyMemo", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyTreasury", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyAutoRenewAccount",
                                SUCCESS,
                                recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyAutoRenewPeriod",
                                SUCCESS,
                                recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyAdminKeyWithEd",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_SIGNATURE)),
                        childRecordsCheck(
                                "updateOnlyAdminKey", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyKycKeyWithEd", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyKycKey", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyFreezeKeyWithEd",
                                SUCCESS,
                                recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyFreezeKey", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyWipeKeyWithEd", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyWipeKey", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlySupplyKeyWithEd",
                                SUCCESS,
                                recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlySupplyKey", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyFeeKeyWithEd", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyFeeKey", SUCCESS, recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyPauseKeyWithEd",
                                SUCCESS,
                                recordWith().status(SUCCESS)),
                        childRecordsCheck(
                                "updateOnlyPauseKey", SUCCESS, recordWith().status(SUCCESS)));
    }

    @HapiTest
    public HapiSpec createFungibleTokenAdminKeyFromHollowAccountAlias() {
        final var freezeKey = "freezeKey";
        final var newFreezeKey = "newFreezeKey";
        return defaultHapiSpec("CreateFungibleTokenAdminKeyFromHollowAccountAlias")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(freezeKey),
                        newKeyNamed(newFreezeKey),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    spec.registry()
                            .saveAccountAlias(
                                    SECP_256K1_SOURCE_KEY,
                                    AccountID.newBuilder().setAlias(evmAddress).build());

                    allRunFor(
                            spec,
                            // Transfer money to the alias --> creates HOLLOW ACCOUNT
                            cryptoTransfer(
                                    movingHbar(ONE_HUNDRED_HBARS).distributing(TOKEN_TREASURY, SECP_256K1_SOURCE_KEY)),
                            // Verify that the account is created and is hollow
                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                    .has(accountWith().hasEmptyKey()),
                            // Create a token with the ECDSA alias key as ADMIN key
                            tokenCreate(VANILLA_TOKEN)
                                    .tokenType(FUNGIBLE_COMMON)
                                    .adminKey(SECP_256K1_SOURCE_KEY)
                                    .freezeKey(freezeKey)
                                    .initialSupply(100L)
                                    .treasury(TOKEN_TREASURY));
                }))
                .then(withOpContext((spec, opLog) -> {
                    allRunFor(
                            spec,
                            tokenUpdate(VANILLA_TOKEN)
                                    .freezeKey(newFreezeKey)
                                    .signedBy(ACCOUNT, SECP_256K1_SOURCE_KEY)
                                    .payingWith(ACCOUNT),
                            getTokenInfo(VANILLA_TOKEN).searchKeysGlobally().hasFreezeKey(newFreezeKey));
                }));
    }

    @HapiTest
    public HapiSpec createNFTTokenAdminKeyFromHollowAccountAlias() {
        final var freezeKey = "freezeKey";
        final var newFreezeKey = "newFreezeKey";
        return defaultHapiSpec("CreateNFTTokenAdminKeyFromHollowAccountAlias")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        newKeyNamed(freezeKey),
                        newKeyNamed(newFreezeKey),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS))
                .when(withOpContext((spec, opLog) -> {
                    final var ecdsaKey = spec.registry()
                            .getKey(SECP_256K1_SOURCE_KEY)
                            .getECDSASecp256K1()
                            .toByteArray();
                    final var evmAddress = ByteString.copyFrom(recoverAddressFromPubKey(ecdsaKey));
                    spec.registry()
                            .saveAccountAlias(
                                    SECP_256K1_SOURCE_KEY,
                                    AccountID.newBuilder().setAlias(evmAddress).build());

                    allRunFor(
                            spec,
                            // Transfer money to the alias --> creates HOLLOW ACCOUNT
                            cryptoTransfer(
                                    movingHbar(ONE_HUNDRED_HBARS).distributing(TOKEN_TREASURY, SECP_256K1_SOURCE_KEY)),
                            // Verify that the account is created and is hollow
                            getAliasedAccountInfo(SECP_256K1_SOURCE_KEY)
                                    .has(accountWith().hasEmptyKey()),
                            // Create a token with the ECDSA alias key as ADMIN key
                            tokenCreate(NFT_TOKEN)
                                    .tokenType(NON_FUNGIBLE_UNIQUE)
                                    .adminKey(SECP_256K1_SOURCE_KEY)
                                    .supplyKey(SECP_256K1_SOURCE_KEY)
                                    .freezeKey(freezeKey)
                                    .initialSupply(0L)
                                    .treasury(TOKEN_TREASURY));
                }))
                .then(withOpContext((spec, opLog) -> {
                    allRunFor(
                            spec,
                            tokenUpdate(NFT_TOKEN)
                                    .freezeKey(newFreezeKey)
                                    .signedBy(ACCOUNT, SECP_256K1_SOURCE_KEY)
                                    .payingWith(ACCOUNT),
                            getTokenInfo(NFT_TOKEN).searchKeysGlobally().hasFreezeKey(newFreezeKey));
                }));
    }
}
