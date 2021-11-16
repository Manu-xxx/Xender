package com.hedera.services.bdd.suites.contract;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.ADD_NTH_FIB_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CONSPICUOUS_DONATION_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.EMPTY_CONSTRUCTOR;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FIBONACCI_PLUS_CONSTRUCTOR_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.FIBONACCI_PLUS_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.MULTIPURPOSE_BYTECODE_PATH;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.SEND_THEN_REVERT_NESTED_SENDS_ABI;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyListNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ERROR_DECODING_BYTESTRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_GAS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ContractCreateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCreateSuite.class);

	public static void main(String... args) {
		new ContractCreateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						createEmptyConstructor(),
						insufficientPayerBalanceUponCreation(),
						rejectsInvalidMemo(),
						rejectsInsufficientFee(),
						rejectsInvalidBytecode(),
						revertsNonzeroBalance(),
						createFailsIfMissingSigs(),
						rejectsInsufficientGas(),
						createsVanillaContractAsExpectedWithOmittedAdminKey(),
						childCreationsHaveExpectedKeysWithOmittedAdminKey(),
						cannotCreateTooLargeContract(),
						canCallPendingContractSafely(),
						revertedTryExtCallHasNoSideEffects(),
						cannotSendToNonExistentAccount(),
						getsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee(),
				}
		);
	}

	private HapiApiSpec insufficientPayerBalanceUponCreation() {
		return defaultHapiSpec("InsufficientPayerBalanceUponCreation")
				.given(
						cryptoCreate("bankrupt")
								.balance(0L),
						fileCreate("contractCode")
								.path(EMPTY_CONSTRUCTOR)
				)
				.when()
				.then(
						contractCreate("defaultContract")
								.bytecode("contractCode")
								.payingWith("bankrupt")
								.hasPrecheck(INSUFFICIENT_PAYER_BALANCE)
				);
	}

	private HapiApiSpec canCallPendingContractSafely() {
		final int numSlots = 64;
		final int createBurstSize = 500;
		final int[] targets = { 19, 24 };
		final AtomicLong createdFileNum = new AtomicLong();
		final var callTxn = "callTxn";
		final var initcode = "initcode";

		return defaultHapiSpec("CanCallPendingContract")
				.given(
						fileCreate(initcode)
								.path(FIBONACCI_PLUS_PATH)
								.payingWith(GENESIS)
								.exposingNumTo(createdFileNum::set),
						inParallel(IntStream.range(0, createBurstSize)
								.mapToObj(i ->
										contractCreate("contract" + i, FIBONACCI_PLUS_CONSTRUCTOR_ABI, numSlots)
												.fee(ONE_HUNDRED_HBARS)
												.gas(300_000L)
												.payingWith(GENESIS)
												.noLogging()
												.deferStatusResolution()
												.bytecode(initcode)
												.adminKey(THRESHOLD))
								.toArray(HapiSpecOperation[]::new))
				).when(
						sourcing(() ->
								contractCall(
										"0.0." + (createdFileNum.get() + createBurstSize),
										ADD_NTH_FIB_ABI, targets, 12
								)
										.payingWith(GENESIS)
										.gas(300_000L)
										.via(callTxn))
				).then(
						getTxnRecord(callTxn).logged()
				);
	}

	HapiApiSpec cannotSendToNonExistentAccount() {
		Object[] donationArgs = new Object[] { 666666, "Hey, Ma!" };

		return defaultHapiSpec("CannotSendToNonExistentAccount").given(
				fileCreate("multiBytecode")
						.path(MULTIPURPOSE_BYTECODE_PATH)
		).when(
				contractCreate("multi")
						.bytecode("multiBytecode")
						.balance(666)
		).then(
				contractCall("multi", CONSPICUOUS_DONATION_ABI, donationArgs)
		);
	}

	private HapiApiSpec createsVanillaContractAsExpectedWithOmittedAdminKey() {
		final var name = "testContract";

		return defaultHapiSpec("CreatesVanillaContract")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.VALID_BYTECODE_PATH)
				).when().then(
						contractCreate(name)
								.omitAdminKey()
								.bytecode("contractFile"),
						getContractInfo(name)
								.has(contractWith().immutableContractKey(name))
								.logged()
				);
	}

	private HapiApiSpec childCreationsHaveExpectedKeysWithOmittedAdminKey() {
		final AtomicLong firstStickId = new AtomicLong();
		final AtomicLong secondStickId = new AtomicLong();
		final AtomicLong thirdStickId = new AtomicLong();
		final String txn = "creation";

		return defaultHapiSpec("ChildCreationsHaveExpectedKeysWithOmittedAdminKey")
				.given(
						fileCreate("bytecode").path(ContractResources.FUSE_BYTECODE_PATH),
						contractCreate("fuse").bytecode("bytecode").omitAdminKey().via(txn),
						withOpContext((spec, opLog) -> {
							final var op = getTxnRecord(txn);
							allRunFor(spec, op);
							final var record = op.getResponseRecord();
							final var creationResult = record.getContractCreateResult();
							final var createdIds = creationResult.getCreatedContractIDsList();
							assertEquals(
									4, createdIds.size(),
									"Expected four creations but got " + createdIds);
							firstStickId.set(createdIds.get(1).getContractNum());
							secondStickId.set(createdIds.get(2).getContractNum());
							thirdStickId.set(createdIds.get(3).getContractNum());
						})
				).when(
						sourcing(() -> getContractInfo("0.0." + firstStickId.get())
								.has(contractWith().immutableContractKey("0.0." + firstStickId.get()))
								.logged()),
						sourcing(() -> getContractInfo("0.0." + secondStickId.get())
								.has(contractWith().immutableContractKey("0.0." + secondStickId.get()))
								.logged()),
						sourcing(() -> getContractInfo("0.0." + thirdStickId.get())
								.logged()),
						contractCall("fuse", ContractResources.LIGHT_ABI).via("lightTxn")
				).then(
						sourcing(() -> getContractInfo("0.0." + firstStickId.get())
								.hasCostAnswerPrecheck(CONTRACT_DELETED)),
						sourcing(() -> getContractInfo("0.0." + secondStickId.get())
								.hasCostAnswerPrecheck(CONTRACT_DELETED)),
						sourcing(() -> getContractInfo("0.0." + thirdStickId.get())
								.hasCostAnswerPrecheck(CONTRACT_DELETED))
				);
	}

	private HapiApiSpec createEmptyConstructor() {
		return defaultHapiSpec("EmptyConstructor")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.EMPTY_CONSTRUCTOR)
				).when(

				).then(
						contractCreate("emptyConstructorTest")
								.bytecode("contractFile")
								.hasKnownStatus(SUCCESS)
				);
	}

	private HapiApiSpec revertedTryExtCallHasNoSideEffects() {
		final var balance = 3_000;
		final int sendAmount = balance / 3;
		final var initcode = "initcode";
		final var contract = "contract";
		final var aBeneficiary = "aBeneficiary";
		final var bBeneficiary = "bBeneficiary";
		final var txn = "txn";

		return defaultHapiSpec("RevertedTryExtCallHasNoSideEffects")
				.given(
						fileCreate(initcode)
								.path(ContractResources.REVERTING_SEND_TRY),
						contractCreate(contract)
								.bytecode(initcode)
								.balance(balance),
						cryptoCreate(aBeneficiary).balance(0L),
						cryptoCreate(bBeneficiary).balance(0L)
				).when(
						withOpContext((spec, opLog) -> {
							final var registry = spec.registry();
							final int aNum = (int) registry.getAccountID(aBeneficiary).getAccountNum();
							final int bNum = (int) registry.getAccountID(bBeneficiary).getAccountNum();
							final Object[] sendArgs = new Object[] { sendAmount, aNum, bNum };

							final var op = contractCall(
									contract,
									SEND_THEN_REVERT_NESTED_SENDS_ABI,
									sendArgs
							).via(txn);
							allRunFor(spec, op);
						})
				).then(
						getTxnRecord(txn).logged(),
						getAccountBalance(aBeneficiary).logged(),
						getAccountBalance(bBeneficiary).logged()
				);
	}

	private HapiApiSpec createFailsIfMissingSigs() {
		KeyShape shape = listOf(SIMPLE, threshOf(2, 3), threshOf(1, 3));
		SigControl validSig = shape.signedWith(sigs(ON, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));
		SigControl invalidSig = shape.signedWith(sigs(OFF, sigs(ON, ON, OFF), sigs(OFF, OFF, ON)));

		return defaultHapiSpec("CreateFailsIfMissingSigs")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.VALID_BYTECODE_PATH)
				).when().then(
						contractCreate("testContract")
								.adminKeyShape(shape)
								.bytecode("contractFile")
								.sigControl(forKey("testContract", invalidSig))
								.hasKnownStatus(INVALID_SIGNATURE),
						contractCreate("testContract")
								.adminKeyShape(shape)
								.bytecode("contractFile")
								.sigControl(forKey("testContract", validSig))
				);
	}

	private HapiApiSpec rejectsInsufficientGas() {
		return defaultHapiSpec("RejectsInsufficientGas")
				.given(
						fileCreate("simpleStorageBytecode")
								.path(ContractResources.SIMPLE_STORAGE_BYTECODE_PATH)
				).when().then(
						contractCreate("simpleStorage")
								.bytecode("simpleStorageBytecode")
								.gas(0L)
								.hasKnownStatus(INSUFFICIENT_GAS)
				);
	}

	private HapiApiSpec rejectsInvalidMemo() {
		return defaultHapiSpec("RejectsInvalidMemo")
				.given().when().then(
						contractCreate("testContract")
								.entityMemo(TxnUtils.nAscii(101))
								.hasPrecheck(MEMO_TOO_LONG),
						contractCreate("testContract")
								.entityMemo(ZERO_BYTE_MEMO)
								.hasPrecheck(INVALID_ZERO_BYTE_IN_STRING)
				);
	}

	private HapiApiSpec rejectsInsufficientFee() {
		return defaultHapiSpec("RejectsInsufficientFee")
				.given(
						cryptoCreate("payer"),
						fileCreate("contractFile")
								.path(ContractResources.VALID_BYTECODE_PATH)
				).when().then(
						contractCreate("testContract")
								.bytecode("contractFile")
								.payingWith("payer")
								.fee(1L)
								.hasPrecheck(INSUFFICIENT_TX_FEE)
				);
	}

	private HapiApiSpec rejectsInvalidBytecode() {
		return defaultHapiSpec("RejectsInvalidBytecode")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.INVALID_BYTECODE_PATH)
				).when().then(
						contractCreate("testContract")
								.bytecode("contractFile")
								.hasKnownStatus(ERROR_DECODING_BYTESTRING)
				);
	}

	private HapiApiSpec revertsNonzeroBalance() {
		return defaultHapiSpec("RevertsNonzeroBalance")
				.given(
						fileCreate("contractFile")
								.path(ContractResources.VALID_BYTECODE_PATH)
				).when().then(
						contractCreate("testContract")
								.balance(1L)
								.bytecode("contractFile")
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
				);
	}

	private HapiApiSpec getsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee() {
		final var initcode = "initcode";
		final var firstContract = "firstContract";
		final var secondContract = "secondContract";
		final var civilian = "civilian";
		final var creation = "creation";
		final AtomicLong baseCreationFee = new AtomicLong();

		return defaultHapiSpec("GetsInsufficientPayerBalanceIfSendingAccountCanPayEverythingButServiceFee")
				.given(
						cryptoCreate(civilian).balance(ONE_HUNDRED_HBARS),
						fileCreate(initcode)
								.path(ContractResources.MULTIPURPOSE_BYTECODE_PATH)
				).when(
						contractCreate(firstContract)
								.bytecode(initcode)
								.gas(300_000L)
								.payingWith(civilian)
								.balance(0L)
								.via(creation),
						getTxnRecord(creation).providingFeeTo(baseCreationFee::set)
				).then(
						sourcing(() -> contractCreate(secondContract)
								.bytecode(initcode)
								.gas(100_000L)
								.payingWith(civilian)
								.balance(ONE_HUNDRED_HBARS - 2 * baseCreationFee.get()))
				);
	}

	private HapiApiSpec cannotCreateTooLargeContract() {
		ByteString contents;
		try {
			contents =
					ByteString.copyFrom(Files.readAllBytes(Path.of(ContractResources.LARGE_CONTRACT_CRYPTO_KITTIES)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		final var FILE_KEY = "fileKey";
		final var KEY_LIST = "keyList";
		final var ACCOUNT = "acc";
		return defaultHapiSpec("cannotCreateLargeContract")
				.given(
						newKeyNamed(FILE_KEY),
						newKeyListNamed(KEY_LIST, List.of(FILE_KEY)),
						cryptoCreate(ACCOUNT).balance(ONE_HUNDRED_HBARS * 10).key(FILE_KEY),
						fileCreate("bytecode")
								.path(ContractResources.LARGE_CONTRACT_CRYPTO_KITTIES)
								.hasPrecheck(TRANSACTION_OVERSIZE)
				)
				.when(
						fileCreate("bytecode").contents("").key(KEY_LIST),
						UtilVerbs.updateLargeFile(ACCOUNT, "bytecode", contents)
				)
				.then(
						contractCreate("contract")
								.bytecode("bytecode")
								.payingWith(ACCOUNT)
								.hasKnownStatus(INSUFFICIENT_GAS)
				);
	}

	private HapiApiSpec maxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller() {
		return defaultHapiSpec("MaxRefundIsMaxGasRefundConfiguredWhenTXGasPriceIsSmaller")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "5"),
						fileCreate("contractFile").path(ContractResources.VALID_BYTECODE_PATH)
				).when(
						contractCreate("testContract").bytecode("contractFile").gas(300_000L).via("createTX")
				).then(
						withOpContext((spec, ignore) -> {
							final var subop01 = getTxnRecord("createTX").saveTxnRecordToRegistry("createTXRec");
							CustomSpecAssert.allRunFor(spec, subop01);

							final var gasUsed = spec.registry().getTransactionRecord("createTXRec")
									.getContractCreateResult().getGasUsed();
							assertEquals(285_000L, gasUsed);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec minChargeIsTXGasUsed() {
		return defaultHapiSpec("MinChargeIsTXGasUsed")
				.given(
						UtilVerbs.overriding("contracts.maxRefundPercentOfGasLimit", "100"),
						fileCreate("contractFile").path(ContractResources.VALID_BYTECODE_PATH)
				).when(
						contractCreate("testContract").bytecode("contractFile").gas(300_000L).via("createTX")
				).then(
						withOpContext((spec, ignore) -> {
							final var subop01 = getTxnRecord("createTX").saveTxnRecordToRegistry("createTXRec");
							CustomSpecAssert.allRunFor(spec, subop01);

							final var gasUsed = spec.registry().getTransactionRecord("createTXRec")
									.getContractCreateResult().getGasUsed();
							Assertions.assertTrue(gasUsed > 0L);
						}),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec gasLimitOverMaxGasLimitFailsPrecheck() {
		return defaultHapiSpec("GasLimitOverMaxGasLimitFailsPrecheck")
				.given(
						UtilVerbs.overriding("contracts.maxGas", "100"),
						fileCreate("contractFile").path(ContractResources.VALID_BYTECODE_PATH)
				).when().then(
						contractCreate("testContract").bytecode("contractFile").gas(101L).hasPrecheck(
								MAX_GAS_LIMIT_EXCEEDED),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
