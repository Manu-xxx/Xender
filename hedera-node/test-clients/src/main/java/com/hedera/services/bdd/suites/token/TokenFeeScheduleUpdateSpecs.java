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

package com.hedera.services.bdd.suites.token;

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFeeInheritingRoyaltyCollector;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHtsFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fractionalFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.incompleteCustomFee;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.royaltyFeeWithFallback;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHbarFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fixedHtsFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.fractionalFeeInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeTests.royaltyFeeWithFallbackInHbarsInSchedule;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.token.TokenAssociationSpecs.VANILLA_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CUSTOM_FEE_NOT_FULLY_SPECIFIED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FRACTION_DIVIDES_BY_ZERO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID_IN_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.records.SnapshotMatchMode;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.AccountID;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(TOKEN)
public class TokenFeeScheduleUpdateSpecs extends HapiSuite {

    private static final Logger log = LogManager.getLogger(TokenFeeScheduleUpdateSpecs.class);
    private static final String ACCOUNT = "anybody";

    public static void main(String... args) {
        new TokenFeeScheduleUpdateSpecs().runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            onlyValidCustomFeeScheduleCanBeUpdated(),
            baseOperationIsChargedExpectedFee(),
            createFungibleTokenFeeScheduleKeyFromHollowAccountAlias(),
            createNFTTokenFeeScheduleKeyFromHollowAccountAlias()
        });
    }

    @HapiTest
    final HapiSpec baseOperationIsChargedExpectedFee() {
        final var htsAmount = 2_345L;
        final var targetToken = "immutableToken";
        final var feeDenom = "denom";
        final var htsCollector = "denomFee";
        final var feeScheduleKey = "feeSchedule";
        final var expectedBasePriceUsd = 0.001;

        return defaultHapiSpec("BaseOperationIsChargedExpectedFee", SnapshotMatchMode.NONDETERMINISTIC_TRANSACTION_FEES)
                .given(
                        newKeyNamed(feeScheduleKey),
                        cryptoCreate("civilian").key(feeScheduleKey),
                        cryptoCreate(htsCollector),
                        tokenCreate(feeDenom).treasury(htsCollector),
                        tokenCreate(targetToken)
                                .expiry(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                                .feeScheduleKey(feeScheduleKey))
                .when(tokenFeeScheduleUpdate(targetToken)
                        .signedBy(feeScheduleKey)
                        .payingWith("civilian")
                        .blankMemo()
                        .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                        .via("baseFeeSchUpd"))
                .then(validateChargedUsdWithin("baseFeeSchUpd", expectedBasePriceUsd, 1.0));
    }

    @HapiTest
    final HapiSpec onlyValidCustomFeeScheduleCanBeUpdated() {
        final var hbarAmount = 1_234L;
        final var htsAmount = 2_345L;
        final var numerator = 1;
        final var denominator = 10;
        final var minimumToCollect = 5;
        final var maximumToCollect = 50;

        final var token = "withCustomSchedules";
        final var immutableTokenWithFeeScheduleKey = "immutableToken";
        final var noFeeScheduleKeyToken = "tokenWithoutFeeScheduleKey";
        final var feeDenom = "denom";
        final var hbarCollector = "hbarFee";
        final var htsCollector = "denomFee";
        final var tokenCollector = "fractionalFee";
        final var invalidEntityId = "1.2.786";

        final var adminKey = "admin";
        final var feeScheduleKey = "feeSchedule";

        final var newHbarAmount = 17_234L;
        final var newHtsAmount = 27_345L;
        final var newNumerator = 17;
        final var newDenominator = 107;
        final var newMinimumToCollect = 57;
        final var newMaximumToCollect = 507;

        final var newFeeDenom = "newDenom";
        final var newHbarCollector = "newHbarFee";
        final var newHtsCollector = "newDenomFee";
        final var newTokenCollector = "newFractionalFee";

        return defaultHapiSpec("OnlyValidCustomFeeScheduleCanBeUpdated")
                .given(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of("tokens.maxCustomFeesAllowed", "10")),
                        newKeyNamed(adminKey),
                        newKeyNamed(feeScheduleKey),
                        cryptoCreate(htsCollector),
                        cryptoCreate(newHtsCollector),
                        cryptoCreate(hbarCollector),
                        cryptoCreate(newHbarCollector),
                        cryptoCreate(tokenCollector),
                        cryptoCreate(newTokenCollector),
                        tokenCreate(feeDenom).treasury(htsCollector),
                        tokenCreate(newFeeDenom).treasury(newHtsCollector),
                        tokenCreate(token)
                                .adminKey(adminKey)
                                .feeScheduleKey(feeScheduleKey)
                                .treasury(tokenCollector)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector)),
                        tokenCreate(immutableTokenWithFeeScheduleKey)
                                .feeScheduleKey(feeScheduleKey)
                                .treasury(tokenCollector)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector)),
                        tokenCreate(noFeeScheduleKeyToken)
                                .adminKey(adminKey)
                                .treasury(tokenCollector)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector)),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of("tokens.maxCustomFeesAllowed", "1")))
                .when(
                        tokenFeeScheduleUpdate(immutableTokenWithFeeScheduleKey)
                                .withCustom(fractionalFee(
                                        numerator,
                                        0,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(FRACTION_DIVIDES_BY_ZERO),
                        tokenFeeScheduleUpdate(noFeeScheduleKeyToken)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(TOKEN_HAS_NO_FEE_SCHEDULE_KEY),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fractionalFee(
                                        numerator,
                                        0,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(FRACTION_DIVIDES_BY_ZERO),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fractionalFee(
                                        -numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        -minimumToCollect,
                                        OptionalLong.of(maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fractionalFee(
                                        numerator,
                                        denominator,
                                        minimumToCollect,
                                        OptionalLong.of(-maximumToCollect),
                                        tokenCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHbarFee(hbarAmount, hbarCollector))
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, htsCollector))
                                .hasKnownStatus(CUSTOM_FEES_LIST_TOO_LONG),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHbarFee(hbarAmount, invalidEntityId))
                                .hasKnownStatus(INVALID_CUSTOM_FEE_COLLECTOR),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHtsFee(htsAmount, invalidEntityId, htsCollector))
                                .hasKnownStatus(INVALID_TOKEN_ID_IN_CUSTOM_FEES),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHtsFee(htsAmount, feeDenom, hbarCollector))
                                .hasKnownStatus(TOKEN_NOT_ASSOCIATED_TO_FEE_COLLECTOR),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHtsFee(-htsAmount, feeDenom, htsCollector))
                                .hasKnownStatus(CUSTOM_FEE_MUST_BE_POSITIVE),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(incompleteCustomFee(hbarCollector))
                                .hasKnownStatus(CUSTOM_FEE_NOT_FULLY_SPECIFIED),
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(Map.of("tokens.maxCustomFeesAllowed", "10")),
                        tokenAssociate(newTokenCollector, token),
                        tokenFeeScheduleUpdate(token)
                                .withCustom(fixedHbarFee(newHbarAmount, newHbarCollector))
                                .withCustom(fixedHtsFee(newHtsAmount, newFeeDenom, newHtsCollector))
                                .withCustom(fractionalFee(
                                        newNumerator,
                                        newDenominator,
                                        newMinimumToCollect,
                                        OptionalLong.of(newMaximumToCollect),
                                        newTokenCollector)))
                .then(getTokenInfo(token)
                        .hasCustom(fixedHbarFeeInSchedule(newHbarAmount, newHbarCollector))
                        .hasCustom(fixedHtsFeeInSchedule(newHtsAmount, newFeeDenom, newHtsCollector))
                        .hasCustom(fractionalFeeInSchedule(
                                newNumerator,
                                newDenominator,
                                newMinimumToCollect,
                                OptionalLong.of(newMaximumToCollect),
                                false,
                                newTokenCollector)));
    }

    @HapiTest
    public HapiSpec createFungibleTokenFeeScheduleKeyFromHollowAccountAlias() {
        final var hbarAmount = 1_234L;
        final var htsAmount = 2_345L;
        final var numerator = 1;
        final var denominator = 10;
        final var minimumToCollect = 5;
        final var maximumToCollect = 50;

        return defaultHapiSpec("CreateFungibleTokenFeeScheduleKeyFromHollowAccountAlias")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS * 5000L),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS * 5000L))
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
                            // Create a token with the ECDSA alias key as FEE SCHEDULE key
                            tokenCreate(VANILLA_TOKEN)
                                    .tokenType(FUNGIBLE_COMMON)
                                    .feeScheduleKey(SECP_256K1_SOURCE_KEY)
                                    .initialSupply(100L)
                                    .treasury(TOKEN_TREASURY));
                }))
                .then(withOpContext((spec, opLog) -> {
                    allRunFor(
                            spec,
                            tokenFeeScheduleUpdate(VANILLA_TOKEN)
                                    .withCustom(fixedHbarFee(hbarAmount, TOKEN_TREASURY))
                                    .withCustom(fixedHtsFee(htsAmount, VANILLA_TOKEN, TOKEN_TREASURY))
                                    .withCustom(fractionalFee(
                                            numerator,
                                            denominator,
                                            minimumToCollect,
                                            OptionalLong.of(maximumToCollect),
                                            TOKEN_TREASURY))
                                    .signedBy(GENESIS, SECP_256K1_SOURCE_KEY)
                                    .payingWith(GENESIS),
                            getTokenInfo(VANILLA_TOKEN)
                                    .hasCustom(fixedHbarFeeInSchedule(hbarAmount, TOKEN_TREASURY))
                                    .hasCustom(fixedHtsFeeInSchedule(htsAmount, VANILLA_TOKEN, TOKEN_TREASURY))
                                    .hasCustom(fractionalFeeInSchedule(
                                            numerator,
                                            denominator,
                                            minimumToCollect,
                                            OptionalLong.of(maximumToCollect),
                                            false,
                                            TOKEN_TREASURY)));
                }));
    }

    @HapiTest
    public HapiSpec createNFTTokenFeeScheduleKeyFromHollowAccountAlias() {
        final var hbarAmount = 1_234L;

        return defaultHapiSpec("CreateNFTTokenFeeScheduleKeyFromHollowAccountAlias")
                .given(
                        // Create an ECDSA key
                        newKeyNamed(SECP_256K1_SOURCE_KEY).shape(SECP_256K1_SHAPE),
                        cryptoCreate(ACCOUNT).balance(ONE_MILLION_HBARS * 5000L),
                        cryptoCreate(TOKEN_TREASURY).balance(ONE_MILLION_HBARS * 5000L))
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
                            // Create a token with the ECDSA alias key as FEE SCHEDULE key
                            tokenCreate(VANILLA_TOKEN)
                                    .tokenType(NON_FUNGIBLE_UNIQUE)
                                    .feeScheduleKey(SECP_256K1_SOURCE_KEY)
                                    .supplyKey(SECP_256K1_SOURCE_KEY)
                                    .initialSupply(0L)
                                    .treasury(TOKEN_TREASURY));
                }))
                .then(withOpContext((spec, opLog) -> {
                    allRunFor(
                            spec,
                            tokenFeeScheduleUpdate(VANILLA_TOKEN)
                                    .withCustom(fixedHbarFee(hbarAmount, TOKEN_TREASURY))
                                    .withCustom(royaltyFeeWithFallback(
                                            1, 3, fixedHbarFeeInheritingRoyaltyCollector(10), TOKEN_TREASURY))
                                    .signedBy(GENESIS, SECP_256K1_SOURCE_KEY)
                                    .payingWith(GENESIS),
                            getTokenInfo(VANILLA_TOKEN)
                                    .hasCustom(fixedHbarFeeInSchedule(hbarAmount, TOKEN_TREASURY))
                                    .hasCustom(royaltyFeeWithFallbackInHbarsInSchedule(1, 3, 10, TOKEN_TREASURY)));
                }));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
