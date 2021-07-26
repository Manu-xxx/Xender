package com.hedera.services.bdd.suites.token;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfos;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.enablingAutoRenewWith;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class Hip17UnhappyTokensSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Hip17UnhappyTokensSuite.class);

	private static final String ANOTHER_USER = "AnotherUser";
	private static final String ANOTHER_KEY = "AnotherKey";

	private static final String TOKEN_TREASURY = "treasury";
	private static final String NEW_TOKEN_TREASURY = "newTreasury";
	private static final String AUTO_RENEW_ACCT = "autoRenewAcct";
	private static final String NEW_AUTO_RENEW_ACCT = "newAutoRenewAcct";

	private static final String NFTexpired = "NFTexpired";
	private static final String NFTdeleted = "NFTdeleted";
	private static final String NFTautoRemoved = "NFTautoRemoved";
	private static final String ADMIN_KEY = "adminKey";
	private static final String SUPPLY_KEY = "supplyKey";
	private static final String FREEZE_KEY = "freezeKey";
	private static final String WIPE_KEY = "wipeKey";
	private static final String KYC_KEY = "kycKey";
	private static final String FEE_SCHEDULE_KEY = "feeScheduleKey";

	private static final String NEW_FREEZE_KEY = "newFreezeKey";
	private static final String NEW_WIPE_KEY = "newWipeKey";
	private static final String NEW_KYC_KEY = "newKycKey";
	private static final String NEW_SUPPLY_KEY = "newSupplyKey";

	private static String FIRST_MEMO = "First things first";
	private static String SECOND_MEMO = "Nothing left to do";
	private static String SALTED_NAME = salted("primary");
	private static String NEW_SALTED_NAME = salted("primary");

	public static void main(String... args) {
		new Hip17UnhappyTokensSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				cannotGetNftInfosWhenDeleted(),
				canStillGetNftInfoWhenDeleted(),
				cannotWipeNftWhenDeleted(),
				cannotBurnNftWhenDeleted(),
				cannotMintNftWhenDeleted(),
				cannotDissociateNftWhenDeleted(),
				cannotAssociateNftWhenDeleted(),
				cannotUpdateNftWhenDeleted(),
				cannotUpdateNftFeeScheduleWhenDeleted(),
				cannotTransferNftWhenDeleted(),
				cannotFreezeNftWhenDeleted(),
				cannotUnfreezeNftWhenDeleted(),

				// TODO: when auto removal and expiry implemented, enable the following and
				// also add all those scenaios like above to complete the matrix.
				//cannotGetNftInfoWhenExpired()
				//cannotGetNftInfoWhenAutoRemoved(),
				//autoRemovalCasesSuiteCleanup()
				}
		);
	}
	private HapiApiSpec cannotGetNftInfosWhenDeleted() {
		return defaultHapiSpec("cannotGetNftInfosWhenDeleted")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(ADMIN_KEY),
						cryptoCreate(TOKEN_TREASURY).key(ADMIN_KEY),
						tokenCreate(NFTdeleted)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.adminKey(ADMIN_KEY)
								.treasury(TOKEN_TREASURY),
						mintToken(NFTdeleted, List.of(metadata(FIRST_MEMO)))
				).when(
						tokenDelete(NFTdeleted)
				).then(
						getTokenNftInfos(NFTdeleted, 0, 3)
								.hasCostAnswerPrecheck(TOKEN_WAS_DELETED)
				);
	}

	private HapiApiSpec canStillGetNftInfoWhenDeleted() {
		return defaultHapiSpec("canStillGetNftInfoWhenDeleted")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(ADMIN_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
						tokenCreate(NFTdeleted)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.adminKey(ADMIN_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY),
						mintToken(NFTdeleted, List.of(metadata(FIRST_MEMO)))
				).when(
						tokenDelete(NFTdeleted)
				).then(
						getTokenNftInfo(NFTdeleted, 1L)
								.hasTokenID(NFTdeleted)
								.hasSerialNum(1L)
				);
	}


	private HapiApiSpec cannotTransferNftWhenDeleted() {
		return defaultHapiSpec("cannotTransferNftWhenDeleted")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(ADMIN_KEY),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(ANOTHER_USER),
						tokenCreate(NFTdeleted)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.adminKey(ADMIN_KEY)
								.treasury(TOKEN_TREASURY)
				).when(
						tokenAssociate(ANOTHER_USER, NFTdeleted),
						mintToken(NFTdeleted, List.of(metadata(FIRST_MEMO), metadata(SECOND_MEMO))),
						cryptoTransfer(
								TokenMovement.movingUnique(NFTdeleted, 1L)
										.between(TOKEN_TREASURY, ANOTHER_USER)),
						tokenDelete(NFTdeleted)
				).then(
						cryptoTransfer(
								TokenMovement.movingUnique(NFTdeleted, 2L)
										.between(TOKEN_TREASURY, ANOTHER_USER))
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}


	private HapiApiSpec cannotUnfreezeNftWhenDeleted() {
		return defaultHapiSpec("cannotUnfreezeNftWhenDeleted")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(ADMIN_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(0L).key(ADMIN_KEY),
						tokenCreate(NFTdeleted)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.freezeKey(FREEZE_KEY)
								.freezeDefault(true)
								.adminKey(ADMIN_KEY)
								.supplyKey(SUPPLY_KEY)
								.treasury(TOKEN_TREASURY)

				).when(
						tokenDelete(NFTdeleted)
				).then(
						tokenUnfreeze(NFTdeleted, TOKEN_TREASURY)
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	private HapiApiSpec cannotFreezeNftWhenDeleted() {
		return defaultHapiSpec("cannotFreezeNftWhenDeleted")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(ADMIN_KEY),
						cryptoCreate(TOKEN_TREASURY).balance(0L).key(ADMIN_KEY),
						tokenCreate(NFTdeleted)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.freezeKey(FREEZE_KEY)
								.adminKey(ADMIN_KEY)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0L)
								.treasury(TOKEN_TREASURY)
				).when(
						tokenDelete(NFTdeleted)
				).then(
						tokenFreeze(NFTdeleted, TOKEN_TREASURY)
								.hasPrecheck(OK)
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	private HapiApiSpec cannotDissociateNftWhenDeleted() {
		return defaultHapiSpec("cannotDissociateNftWhenDeleted")
				.given(
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY).key(ADMIN_KEY),
						cryptoCreate(ANOTHER_USER),
						tokenCreate(NFTdeleted)
								.initialSupply(0)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.adminKey(ADMIN_KEY)
								.treasury(TOKEN_TREASURY),
						tokenAssociate(ANOTHER_USER, NFTdeleted)
				)
				.when(
						tokenDelete(NFTdeleted)
				)
				.then(
						tokenDissociate(ANOTHER_USER, NFTdeleted)
								.hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec cannotAssociateNftWhenDeleted() {
		return defaultHapiSpec("cannotAssociateNftWhenDeleted")
				.given(
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY).key(ADMIN_KEY),
						cryptoCreate(ANOTHER_USER),
						tokenCreate(NFTdeleted)
								.initialSupply(0)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.adminKey(ADMIN_KEY)
								.treasury(TOKEN_TREASURY)
				)
				.when(
						tokenDelete(NFTdeleted)
				)
				.then(
						tokenAssociate(ANOTHER_USER, NFTdeleted)
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	public HapiApiSpec cannotUpdateNftWhenDeleted() {
		return defaultHapiSpec("cannotUpdateNftWhenDeleted")
				.given(
						cryptoCreate(TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(NEW_TOKEN_TREASURY).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(AUTO_RENEW_ACCT).balance(ONE_HUNDRED_HBARS),
						cryptoCreate(NEW_AUTO_RENEW_ACCT).balance(ONE_HUNDRED_HBARS),
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(FREEZE_KEY),
						newKeyNamed(NEW_FREEZE_KEY),
						newKeyNamed(KYC_KEY),
						newKeyNamed(NEW_KYC_KEY),
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(NEW_SUPPLY_KEY),
						newKeyNamed(WIPE_KEY),
						newKeyNamed(NEW_WIPE_KEY),
						tokenCreate(NFTdeleted)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.name(SALTED_NAME)
								.entityMemo(FIRST_MEMO)
								.treasury(TOKEN_TREASURY)
								.autoRenewAccount(AUTO_RENEW_ACCT)
								.autoRenewPeriod(100)
								.initialSupply(0)
								.adminKey(ADMIN_KEY)
								.freezeKey(FREEZE_KEY)
								.kycKey(KYC_KEY)
								.supplyKey(SUPPLY_KEY)
								.wipeKey(WIPE_KEY),
						tokenAssociate(NEW_TOKEN_TREASURY, NFTdeleted),
						// can update before NFT is deleted
						tokenUpdate(NFTdeleted)
								.entityMemo(ZERO_BYTE_MEMO)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING),
						tokenUpdate(NFTdeleted)
								.name(NEW_SALTED_NAME)
								.entityMemo(SECOND_MEMO)
								.treasury(NEW_TOKEN_TREASURY)
								.autoRenewAccount(NEW_AUTO_RENEW_ACCT)
								.autoRenewPeriod(101)
								.freezeKey(NEW_FREEZE_KEY)
								.kycKey(NEW_KYC_KEY)
								.supplyKey(NEW_SUPPLY_KEY)
								.wipeKey(NEW_WIPE_KEY)
								.hasKnownStatus(SUCCESS)
						).when(
						tokenDelete(NFTdeleted)
				).then(
						// can't update after NFT is deleted.
						tokenUpdate(NFTdeleted)
								.name(NEW_SALTED_NAME)
								.entityMemo(SECOND_MEMO)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						tokenUpdate(NFTdeleted)
								.treasury(NEW_TOKEN_TREASURY)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						tokenUpdate(NFTdeleted)
								.autoRenewAccount(NEW_AUTO_RENEW_ACCT)
								.autoRenewPeriod(102)
								.hasKnownStatus(TOKEN_WAS_DELETED),
						tokenUpdate(NFTdeleted)
								.freezeKey(NEW_FREEZE_KEY)
								.kycKey(NEW_KYC_KEY)
								.supplyKey(NEW_SUPPLY_KEY)
								.wipeKey(NEW_WIPE_KEY)
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	private HapiApiSpec cannotUpdateNftFeeScheduleWhenDeleted() {
		final var origHbarFee = 1_234L;
		final var newHbarFee = 4_321L;
		final var hbarCollector = "hbarFee";

		return defaultHapiSpec("cannotUpdateNftFeeScheduleWhenDeleted")
				.given(
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(FEE_SCHEDULE_KEY),
						cryptoCreate(TOKEN_TREASURY).key(ADMIN_KEY),
						cryptoCreate(hbarCollector),
						tokenCreate(NFTdeleted)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.initialSupply(0L)
								.adminKey(ADMIN_KEY)
								.treasury(TOKEN_TREASURY)
								.feeScheduleKey(FEE_SCHEDULE_KEY)
								.withCustom(fixedHbarFee(origHbarFee, hbarCollector))
				).when(
						tokenDelete(NFTdeleted)
				).then(
						tokenFeeScheduleUpdate(NFTdeleted)
								.withCustom(fixedHbarFee(newHbarFee, hbarCollector))
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	private HapiApiSpec cannotMintNftWhenDeleted() {
		return defaultHapiSpec("cannotMintNftWhenDeleted")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(WIPE_KEY),
						newKeyNamed(ADMIN_KEY),
						cryptoCreate(TOKEN_TREASURY).key(ADMIN_KEY),
						cryptoCreate(ANOTHER_USER),
						tokenCreate(NFTdeleted)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(ADMIN_KEY)
				)
				.when(
						tokenDelete(NFTdeleted)
				)
				.then(
						mintToken(NFTdeleted, List.of(ByteString.copyFromUtf8(FIRST_MEMO)))
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	private HapiApiSpec cannotBurnNftWhenDeleted() {
		return defaultHapiSpec("cannotBurnNftWhenDeleted")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(WIPE_KEY),
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(ANOTHER_KEY),
						cryptoCreate(TOKEN_TREASURY).key(ADMIN_KEY),
						cryptoCreate(ANOTHER_USER).key(ANOTHER_KEY),
						tokenCreate(NFTdeleted)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(ADMIN_KEY),
						tokenAssociate(ANOTHER_USER, NFTdeleted),
						mintToken(NFTdeleted,
								List.of(ByteString.copyFromUtf8(FIRST_MEMO), ByteString.copyFromUtf8(SECOND_MEMO))),
						cryptoTransfer(
								movingUnique(NFTdeleted, 2L).between(TOKEN_TREASURY, ANOTHER_USER)
						),
						getAccountInfo(ANOTHER_USER).hasOwnedNfts(1),
						getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(1),
						getTokenInfo(NFTdeleted).hasTotalSupply(2),
						getTokenNftInfo(NFTdeleted, 2).hasCostAnswerPrecheck(OK),
						getTokenNftInfo(NFTdeleted, 1).hasSerialNum(1)
				)
				.when(
						tokenDelete(NFTdeleted)
				)
				.then(
						burnToken(NFTdeleted,  List.of(2L))
								.hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}

	private HapiApiSpec cannotWipeNftWhenDeleted() {
		return defaultHapiSpec("cannotWipeNftWhenDeleted")
				.given(
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(WIPE_KEY),
						newKeyNamed(ADMIN_KEY),
						newKeyNamed(ANOTHER_KEY),
						cryptoCreate(TOKEN_TREASURY).key(ADMIN_KEY),
						cryptoCreate(ANOTHER_USER).key(ANOTHER_KEY),
						tokenCreate(NFTdeleted)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(ADMIN_KEY)
								.wipeKey(WIPE_KEY),
						tokenAssociate(ANOTHER_USER, NFTdeleted),
						mintToken(NFTdeleted,
								List.of(ByteString.copyFromUtf8(FIRST_MEMO), ByteString.copyFromUtf8(SECOND_MEMO))),
						cryptoTransfer(
								movingUnique(NFTdeleted, 2L).between(TOKEN_TREASURY, ANOTHER_USER)
						),
						getAccountInfo(ANOTHER_USER).hasOwnedNfts(1),
						getAccountInfo(TOKEN_TREASURY).hasOwnedNfts(1),
						getTokenInfo(NFTdeleted).hasTotalSupply(2),
						getTokenNftInfo(NFTdeleted, 2).hasCostAnswerPrecheck(OK),
						getTokenNftInfo(NFTdeleted, 1).hasSerialNum(1)
				)
				.when(
						tokenDelete(NFTdeleted)
				)
				.then(
						wipeTokenAccount(NFTdeleted, ANOTHER_USER, List.of(1L)).hasKnownStatus(TOKEN_WAS_DELETED)
				);
	}


	private HapiApiSpec cannotGetNftInfoWhenExpired() {
		return defaultHapiSpec("cannotGetNftInfoWhenExpired")
				.given(
						tokenOpsEnablement(),
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(
										enablingAutoRenewWith(1, 0L, 2, 2)),
						newKeyNamed(SUPPLY_KEY),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NFTexpired)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.initialSupply(0)
								.expiry(Instant.now().getEpochSecond() + 5L)
								.treasury(TOKEN_TREASURY),
						mintToken(NFTexpired, List.of(metadata(FIRST_MEMO)))
				).when(
						sleepFor(6000)
				).then(
						getTokenNftInfo(NFTexpired, 1)
								.hasCostAnswerPrecheckFrom(OK)
				);
	}

	private HapiApiSpec cannotGetNftInfoWhenAutoRemoved() {
		return defaultHapiSpec("cannotGetNftInfoWhenAutoRemoved")
				.given(
						tokenOpsEnablement(),
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(
										enablingAutoRenewWith(1, 0L, 100, 100))
								.erasingProps(Set.of("minimumAutoRenewDuration")),
						newKeyNamed(SUPPLY_KEY),
						newKeyNamed(ADMIN_KEY),
						cryptoCreate(TOKEN_TREASURY)
								.balance(ONE_HUNDRED_HBARS).autoRenewSecs(THREE_MONTHS_IN_SECONDS),
						tokenCreate(NFTautoRemoved)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyType(TokenSupplyType.INFINITE)
								.supplyKey(SUPPLY_KEY)
								.adminKey(ADMIN_KEY)
								.initialSupply(0)
								.autoRenewAccount(TOKEN_TREASURY)
								.autoRenewPeriod(2L)
								.expiry(Instant.now().getEpochSecond() + 5L)
								.treasury(TOKEN_TREASURY),

						mintToken(NFTautoRemoved,List.of(metadata(FIRST_MEMO), metadata(SECOND_MEMO)), "nftMint")
								.payingWith(GENESIS)
				).when(
						cryptoTransfer(tinyBarsFromTo(TOKEN_TREASURY, GENESIS, ONE_HUNDRED_HBARS - 10))
								.payingWith(GENESIS),
						sleepFor(8000),
						cryptoTransfer(tinyBarsFromTo(TOKEN_TREASURY, GENESIS,  10)).payingWith(GENESIS)
				).then(
						getAccountBalance(TOKEN_TREASURY).logged(),
						getTokenNftInfo(NFTautoRemoved,1).hasAnswerOnlyPrecheck(OK).logged(),
						getTokenNftInfo(NFTautoRemoved,1)
								.hasCostAnswerPrecheckFrom(OK).logged()
				);
	}

	private HapiApiSpec autoRemovalCasesSuiteCleanup() {
		return defaultHapiSpec("AutoRemovalCasesSuiteCleanup")
				.given().when().then(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWithDefaults())
				);
	}

	private ByteString metadata(String contents) {
		return ByteString.copyFromUtf8(contents);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
