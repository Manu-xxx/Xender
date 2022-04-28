package com.hedera.services.bdd.suites.contract.precompile;

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
import com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.HapiPropertySource.asContractString;
import static com.hedera.services.bdd.spec.HapiPropertySource.contractIdFromHexedMirrorAddress;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractLogAsserts.logWith;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenNftInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoApproveAllowance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingUnique;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.asAddress;
import static com.hedera.services.bdd.suites.contract.Utils.eventSignatureOf;
import static com.hedera.services.bdd.suites.contract.Utils.parsedToByteString;
import static com.hedera.services.bdd.suites.contract.precompile.DynamicGasCostSuite.captureChildCreate2MetaFor;
import static com.hedera.services.bdd.suites.utils.contracts.precompile.HTSPrecompileResult.htsPrecompileResult;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public class ERCPrecompileSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ERCPrecompileSuite.class);
	private static final long GAS_TO_OFFER = 4_000_000L;
	private static final String FUNGIBLE_TOKEN = "fungibleToken";
	private static final String NON_FUNGIBLE_TOKEN = "nonFungibleToken";
	private static final String MULTI_KEY = "purpose";
	private static final String ERC_20_CONTRACT_NAME = "erc20Contract";
	private static final String OWNER = "owner";
	private static final String ACCOUNT = "anybody";
	private static final String RECIPIENT = "recipient";
	private static final ByteString FIRST_META = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
	private static final ByteString SECOND_META = ByteString.copyFrom("FIRST".getBytes(StandardCharsets.UTF_8));
	private static final String TRANSFER_SIG_NAME = "transferSig";
	private static final String TRANSFER_SIGNATURE = "Transfer(address,address,uint256)";
	private static final String ERC_20_CONTRACT = "ERC20Contract";
	private static final String ERC_721_CONTRACT = "ERC721Contract";

	public static void main(String... args) {
		new ERCPrecompileSuite().runSuiteSync();
	}

	@Override
	public boolean canRunAsync() {
		return false;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				ERC_20(),
				ERC_721()
		);
	}

	List<HapiApiSpec> ERC_20() {
		return List.of(new HapiApiSpec[] {
				getErc20TokenName(),
				getErc20TokenSymbol(),
				getErc20TokenDecimals(),
				getErc20TotalSupply(),
				getErc20BalanceOfAccount(),
				transferErc20Token(),
				erc20Allowance(),
				erc20Approve(),
				getErc20TokenDecimalsFromErc721TokenFails(),
				transferErc20TokenFromErc721TokenFails(),
				transferErc20TokenReceiverContract(),
				transferErc20TokenSenderAccount(),
				transferErc20TokenAliasedSender()
		});
	}

	List<HapiApiSpec> ERC_721() {
		return List.of(new HapiApiSpec[] {
				getErc721TokenName(),
				getErc721Symbol(),
				getErc721TokenURI(),
				getErc721OwnerOf(),
				getErc721BalanceOf(),
				getErc721TotalSupply(),
				getErc721TokenURIFromErc20TokenFails(),
				getErc721OwnerOfFromErc20TokenFails()
		});
	}

	private HapiApiSpec getErc20TokenName() {
		final var tokenName = "TokenA";
		final var nameTxn = "nameTxn";

		return defaultHapiSpec("ERC_20_NAME")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.name(tokenName)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "name", asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))
												)
														.payingWith(ACCOUNT)
														.via(nameTxn)
														.gas(4_000_000)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						childRecordsCheck(nameTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.NAME)
																.withName(tokenName)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc20TokenSymbol() {
		final var tokenSymbol = "F";
		final var symbolTxn = "symbolTxn";
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();

		return defaultHapiSpec("ERC_20_SYMBOL")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.symbol(tokenSymbol)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "symbol", asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))
												)
														.payingWith(ACCOUNT)
														.via(symbolTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(symbolTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.SYMBOL)
																.withSymbol(tokenSymbol)
														)
										)
						),
						sourcing(() -> contractCallLocal(ERC_20_CONTRACT_NAME, "symbol", tokenAddr.get()))
				);
	}

	private HapiApiSpec getErc20TokenDecimals() {
		final var decimals = 10;
		final var decimalsTxn = "decimalsTxn";
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();

		return defaultHapiSpec("ERC_20_DECIMALS")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.decimals(decimals)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						fileCreate(ERC_20_CONTRACT_NAME),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "decimals", asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))
												)
														.payingWith(ACCOUNT)
														.via(decimalsTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(decimalsTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.DECIMALS)
																.withDecimals(decimals)))),
						sourcing(() -> contractCallLocal(ERC_20_CONTRACT_NAME, "decimals", tokenAddr.get()))
				);
	}

	private HapiApiSpec getErc20TotalSupply() {
		final var totalSupply = 50;
		final var supplyTxn = "supplyTxn";
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();

		return defaultHapiSpec("ERC_20_TOTAL_SUPPLY")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(totalSupply)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "totalSupply", asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN))
												)
														.payingWith(ACCOUNT)
														.via(supplyTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(supplyTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(
																		HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																.withTotalSupply(totalSupply)
														)
										)
						),
						sourcing(() -> contractCallLocal(ERC_20_CONTRACT_NAME, "decimals", tokenAddr.get()))
				);
	}

	private HapiApiSpec getErc20BalanceOfAccount() {
		final var balanceTxn = "balanceTxn";
		final var zeroBalanceTxn = "zBalanceTxn";
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();
		final AtomicReference<byte[]> accountAddr = new AtomicReference<>();

		return defaultHapiSpec("ERC_20_BALANCE_OF")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.INFINITE)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(3, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "balanceOf",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)))
														.payingWith(ACCOUNT)
														.via(balanceTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						/* expect 0 returned from balanceOf() if the account and token are not associated -*/
						childRecordsCheck(zeroBalanceTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BALANCE)
																.withBalance(0)
														)
										)
						),
						childRecordsCheck(balanceTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BALANCE)
																.withBalance(3)
														)
										)
						),
						sourcing(() -> contractCallLocal(
								ERC_20_CONTRACT_NAME, "balanceOf",
								tokenAddr.get(), accountAddr.get()))
				);
	}

	private HapiApiSpec transferErc20Token() {
		final var transferTxn = "transferTxn";
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();
		final AtomicReference<byte[]> accountAddr = new AtomicReference<>();

		return defaultHapiSpec("ERC_20_TRANSFER")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "transfer",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2)
														.via(transferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
						getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getContractInfo(ERC_20_CONTRACT).getContractID();
							final var receiver = spec.registry().getAccountInfo(RECIPIENT).getAccountID();

							var txnRecord =
									getTxnRecord(transferTxn).hasPriority(recordWith().contractCallResult(resultWith()
													.logs(inOrder(logWith().withTopicsInOrder(List.of(
																	eventSignatureOf(TRANSFER_SIGNATURE),
																	parsedToByteString(sender.getContractNum()),
																	parsedToByteString(receiver.getAccountNum())
															)).longValue(2))
													)))
											.andAllChildRecords().logged();
							allRunFor(spec, txnRecord);
						}),
						childRecordsCheck(transferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(
																		HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(ERC_20_CONTRACT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2),
						sourcing(() -> contractCallLocal(
										ERC_20_CONTRACT_NAME, "transfer",
										tokenAddr.get(), accountAddr.get(), 1
								).hasAnswerOnlyPrecheck(NOT_SUPPORTED)
						)
				);
	}

	private HapiApiSpec transferErc20TokenReceiverContract() {
		final var transferTxn = "transferTxn";
		final var nestedContract = "NestedERC20Contract";

		return defaultHapiSpec("ERC_20_TRANSFER_RECEIVER_CONTRACT")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_20_CONTRACT, nestedContract),
						contractCreate(ERC_20_CONTRACT),
						contractCreate(nestedContract),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(nestedContract, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ERC_20_CONTRACT))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "transfer",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getContractId(nestedContract)), 2
												)
														.via(transferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
						getContractInfo(nestedContract).saveToRegistry(nestedContract),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getContractInfo(ERC_20_CONTRACT).getContractID();
							final var receiver = spec.registry().getContractInfo(nestedContract).getContractID();

							var txnRecord =
									getTxnRecord(transferTxn).hasPriority(recordWith().contractCallResult(resultWith()
													.logs(inOrder(logWith().withTopicsInOrder(List.of(
																	eventSignatureOf(TRANSFER_SIGNATURE),
																	parsedToByteString(sender.getContractNum()),
																	parsedToByteString(receiver.getContractNum())
															)).longValue(2))
													)))
											.andAllChildRecords().logged();
							allRunFor(spec, txnRecord);
						}),
						childRecordsCheck(transferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(
																		HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(ERC_20_CONTRACT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(nestedContract)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec transferErc20TokenSenderAccount() {
		final var transferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_SENDER_ACCOUNT")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(5)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(5, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, ACCOUNT)),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "delegateTransfer",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2
												)
														.payingWith(ACCOUNT)
														.alsoSigningWithFullPrefix(MULTI_KEY)
														.via(transferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getAccountInfo(ACCOUNT).savingSnapshot(ACCOUNT),
						getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getAccountInfo(ACCOUNT).getAccountID();
							final var receiver = spec.registry().getAccountInfo(RECIPIENT).getAccountID();

							var txnRecord =
									getTxnRecord(transferTxn).hasPriority(recordWith().contractCallResult(resultWith()
													.logs(inOrder(logWith().withTopicsInOrder(List.of(
																	eventSignatureOf(TRANSFER_SIGNATURE),
																	parsedToByteString(sender.getAccountNum()),
																	parsedToByteString(receiver.getAccountNum())
															)).longValue(2))
													)))
											.andAllChildRecords().logged();
							allRunFor(spec, txnRecord);
						}),

						childRecordsCheck(transferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(
																		HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(ACCOUNT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 3),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 2)
				);
	}

	private HapiApiSpec transferErc20TokenAliasedSender() {
		final var aliasedTransferTxn = "aliasedTransferTxn";
		final var addLiquidityTxn = "addLiquidityTxn";
		final var create2Txn = "create2Txn";

		final var ACCOUNT_A = "AccountA";
		final var ACCOUNT_B = "AccountB";
		final var TOKEN_A = "TokenA";

		final var ALIASED_TRANSFER = "AliasedTransfer";
		final byte[][] ALIASED_ADDRESS = new byte[1][1];

		final AtomicReference<String> childMirror = new AtomicReference<>();
		final AtomicReference<String> childEip1014 = new AtomicReference<>();

		return defaultHapiSpec("ERC_20_TRANSFER_ALIASED_SENDER")
				.given(
						UtilVerbs.overriding("contracts.throttle.throttleByGas", "false"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER),
						cryptoCreate(ACCOUNT),
						cryptoCreate(ACCOUNT_A).key(MULTI_KEY).balance(ONE_MILLION_HBARS),
						cryptoCreate(ACCOUNT_B).balance(ONE_MILLION_HBARS),
						tokenCreate("TokenA")
								.adminKey(MULTI_KEY)
								.initialSupply(10000)
								.treasury(ACCOUNT_A),
						tokenAssociate(ACCOUNT_B, TOKEN_A),
						uploadInitCode(ALIASED_TRANSFER),
						contractCreate(ALIASED_TRANSFER)
								.gas(300_000),
						withOpContext(
								(spec, opLog) -> allRunFor(
										spec,
										contractCall(ALIASED_TRANSFER,
												"deployWithCREATE2",
												asAddress(spec.registry().getTokenID(TOKEN_A)))
												.exposingResultTo(result -> {
													final var res = (byte[]) result[0];
													ALIASED_ADDRESS[0] = res;
												})
												.payingWith(ACCOUNT)
												.alsoSigningWithFullPrefix(MULTI_KEY)
												.via(create2Txn).gas(GAS_TO_OFFER)
												.hasKnownStatus(SUCCESS)
								)
						)
				).when(
						captureChildCreate2MetaFor(
								2, 0,
								"setup", create2Txn, childMirror, childEip1014),
						withOpContext(
								(spec, opLog) -> allRunFor(
										spec,
										contractCall(ALIASED_TRANSFER,
												"giveTokensToOperator",
												asAddress(spec.registry().getTokenID(TOKEN_A)),
												asAddress(spec.registry().getAccountID(ACCOUNT_A)),
												1500)
												.payingWith(ACCOUNT)
												.alsoSigningWithFullPrefix(MULTI_KEY)
												.via(addLiquidityTxn).gas(GAS_TO_OFFER)
												.hasKnownStatus(SUCCESS)
								)
						),
						withOpContext(
								(spec, opLog) -> allRunFor(
										spec,
										contractCall(ALIASED_TRANSFER,
												"transfer",
												asAddress(spec.registry().getAccountID(ACCOUNT_B)),
												1000)
												.payingWith(ACCOUNT)
												.alsoSigningWithFullPrefix(MULTI_KEY)
												.via(aliasedTransferTxn).gas(GAS_TO_OFFER)
												.hasKnownStatus(SUCCESS)
								))
				).then(
						sourcing(
								() -> getContractInfo(asContractString(
										contractIdFromHexedMirrorAddress(childMirror.get())))
										.hasToken(ExpectedTokenRel.relationshipWith(TOKEN_A).balance(500))
										.logged()
						),
						getAccountBalance(ACCOUNT_B).hasTokenBalance(TOKEN_A, 1000),
						getAccountBalance(ACCOUNT_A).hasTokenBalance(TOKEN_A, 8500)
				);
	}

	private HapiApiSpec transferErc20TokenFrom() {
		final var accountNotAssignedToTokenTxn = "accountNotAssignedToTokenTxn";
		final var transferFromAccountTxn = "transferFromAccountTxn";
		final var transferFromOtherAccountWithSignaturesTxn = "transferFromOtherAccountWithSignaturesTxn";
		final var transferWithZeroAddressesTxn = "transferWithZeroAddressesTxn";
		final var transferWithAccountWithZeroAddressTxn = "transferWithAccountWithZeroAddressTxn";
		final var transferWithRecipientWithZeroAddressTxn = "transferWithRecipientWithZeroAddressTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_FROM")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(10 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(35)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														5)
														.payingWith(GENESIS)
														.via(accountNotAssignedToTokenTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
												tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
												cryptoTransfer(TokenMovement.moving(20, FUNGIBLE_TOKEN).
														between(TOKEN_TREASURY, ACCOUNT)).payingWith(ACCOUNT),
												contractCall(ERC_20_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														5)
														.payingWith(ACCOUNT)
														.via(transferFromAccountTxn)
														.hasKnownStatus(SUCCESS),
												newKeyNamed(TRANSFER_SIG_NAME).shape(
														SIMPLE.signedWith(ON)),
												cryptoUpdate(ACCOUNT).key(TRANSFER_SIG_NAME),
												cryptoUpdate(RECIPIENT).key(TRANSFER_SIG_NAME),
												contractCall(ERC_20_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														5)
														.payingWith(GENESIS)
														.alsoSigningWithFullPrefix(TRANSFER_SIG_NAME)
														.via(transferFromOtherAccountWithSignaturesTxn)
														.hasKnownStatus(SUCCESS),
												contractCall(ERC_20_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														asAddress(AccountID.parseFrom(new byte[]{})),
														5)
														.payingWith(GENESIS)
														.via(transferWithZeroAddressesTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ERC_20_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														5)
														.payingWith(GENESIS)
														.via(transferWithAccountWithZeroAddressTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ERC_20_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(ACCOUNT)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														5)
														.payingWith(ACCOUNT)
														.via(transferWithRecipientWithZeroAddressTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)

						)
				).then(
						getAccountInfo(ACCOUNT).savingSnapshot(ACCOUNT),
						getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getAccountInfo(ACCOUNT).getAccountID();
							final var receiver = spec.registry().getAccountInfo(RECIPIENT).getAccountID();

							var accountNotAssignedToTokenRecord =
									getTxnRecord(accountNotAssignedToTokenTxn).hasChildRecords(recordWith().status(INVALID_SIGNATURE)
									).andAllChildRecords().logged();

							var transferWithZeroAddressesRecord =
									getTxnRecord(transferWithZeroAddressesTxn).hasChildRecords(recordWith().status(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS)
									).andAllChildRecords().logged();

							var transferWithAccountWithZeroAddressRecord =
									getTxnRecord(transferWithAccountWithZeroAddressTxn).hasChildRecords(recordWith().status(INVALID_ACCOUNT_ID)
									).andAllChildRecords().logged();

							var transferWithRecipientWithZeroAddressRecord =
									getTxnRecord(transferWithRecipientWithZeroAddressTxn).hasChildRecords(recordWith().status(INVALID_ACCOUNT_ID)
									).andAllChildRecords().logged();

							var transferFromAccountRecord =
									getTxnRecord(transferFromAccountTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getAccountNum()),
															parsedToByteString(receiver.getAccountNum())
													)).longValue(5))
											))).andAllChildRecords().logged();
							var transferFromNotOwnerWithSignaturesRecord =
									getTxnRecord(transferFromOtherAccountWithSignaturesTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getAccountNum()),
															parsedToByteString(receiver.getAccountNum())
													)).longValue(5))
											))).andAllChildRecords().logged();

							allRunFor(spec,
									accountNotAssignedToTokenRecord, transferWithZeroAddressesRecord, transferWithAccountWithZeroAddressRecord,
									transferWithRecipientWithZeroAddressRecord, transferFromAccountRecord, transferFromNotOwnerWithSignaturesRecord
							);
						}),
						childRecordsCheck(transferFromAccountTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						childRecordsCheck(transferFromOtherAccountWithSignaturesTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(FUNGIBLE_TOKEN, 15),
						getAccountBalance(ACCOUNT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 10),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 10)
				);
	}

	private HapiApiSpec transferErc20TokenFromContract() {
		final var transferTxn = "transferTxn";
		final var transferFromOtherContractWithSignaturesTxn = "transferFromOtherContractWithSignaturesTxn";
		final var nestedContract = "NestedERC20Contract";

		return defaultHapiSpec("ERC_20_TRANSFER_FROM_CONTRACT")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(10 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(35)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_20_CONTRACT, nestedContract),

						newKeyNamed(TRANSFER_SIG_NAME).shape(
								SIMPLE.signedWith(ON)),

						contractCreate(ERC_20_CONTRACT)
								.adminKey(TRANSFER_SIG_NAME),
						contractCreate(nestedContract)
								.adminKey(TRANSFER_SIG_NAME)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(ACCOUNT, List.of(FUNGIBLE_TOKEN)),
												tokenAssociate(RECIPIENT, List.of(FUNGIBLE_TOKEN)),
												tokenAssociate(ERC_20_CONTRACT, List.of(FUNGIBLE_TOKEN)),
												tokenAssociate(nestedContract, List.of(FUNGIBLE_TOKEN)),
												cryptoTransfer(TokenMovement.moving(20, FUNGIBLE_TOKEN).
														between(TOKEN_TREASURY, ERC_20_CONTRACT)).payingWith(ACCOUNT),
												contractCall(ERC_20_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getContractId(ERC_20_CONTRACT)),
														asAddress(spec.registry().getContractId(nestedContract)),
														5)
														.via(transferTxn)
														.hasKnownStatus(SUCCESS),
												contractCall(ERC_20_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getContractId(ERC_20_CONTRACT)),
														asAddress(spec.registry().getContractId(nestedContract)),
														5)
														.payingWith(GENESIS)
														.alsoSigningWithFullPrefix(TRANSFER_SIG_NAME)
														.via(transferFromOtherContractWithSignaturesTxn)
										)
						)
				).then(
						getContractInfo(ERC_20_CONTRACT).saveToRegistry(ERC_20_CONTRACT),
						getContractInfo(nestedContract).saveToRegistry(nestedContract),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getContractInfo(ERC_20_CONTRACT).getContractID();
							final var receiver = spec.registry().getContractInfo(nestedContract).getContractID();

							var transferRecord =
									getTxnRecord(transferTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getContractNum()),
															parsedToByteString(receiver.getContractNum())
													)).longValue(5))
											))).andAllChildRecords().logged();

							var transferFromOtherContractWithSignaturesTxnRecord =
									getTxnRecord(transferFromOtherContractWithSignaturesTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getContractNum()),
															parsedToByteString(receiver.getContractNum())
													)).longValue(5))
											))).andAllChildRecords().logged();

							allRunFor(spec,
									transferRecord, transferFromOtherContractWithSignaturesTxnRecord
							);
						}),

						childRecordsCheck(transferTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),

						childRecordsCheck(transferFromOtherContractWithSignaturesTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ERC_TRANSFER)
																.withErcFungibleTransferStatus(true)
														)
										)
						),

						getAccountBalance(ERC_20_CONTRACT)
								.hasTokenBalance(FUNGIBLE_TOKEN, 10),
						getAccountBalance(nestedContract)
								.hasTokenBalance(FUNGIBLE_TOKEN, 10)

				);
	}

	private HapiApiSpec erc20Allowance() {
		final var theSpender = "spender";
		final var allowanceTxn = "allowanceTxn";

		return defaultHapiSpec("ERC_20_ALLOWANCE")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(theSpender),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.initialSupply(10L)
								.maxSupply(1000L)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT),
						tokenAssociate(OWNER, FUNGIBLE_TOKEN),
						cryptoTransfer(moving(10, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
						cryptoApproveAllowance()
								.payingWith(DEFAULT_PAYER)
								.addTokenAllowance(OWNER, FUNGIBLE_TOKEN, theSpender, 2L)
								.via("baseApproveTxn")
								.logged()
								.signedBy(DEFAULT_PAYER, OWNER)
								.fee(ONE_HBAR)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "allowance",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(theSpender))
												)
														.payingWith(OWNER)
														.via(allowanceTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getTxnRecord(allowanceTxn).andAllChildRecords().logged(),
						childRecordsCheck(allowanceTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.ALLOWANCE)
																.withAllowance(2)
														)
										)
						)
				);
	}

	private HapiApiSpec erc20Approve() {
		final var approveTxn = "approveTxn";
		final var theSpender = "spender";

		return defaultHapiSpec("ERC_20_APPROVE")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(theSpender),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.supplyType(TokenSupplyType.FINITE)
								.initialSupply(10L)
								.maxSupply(1000L)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT),
						tokenAssociate(OWNER, FUNGIBLE_TOKEN),
						tokenAssociate(ERC_20_CONTRACT, FUNGIBLE_TOKEN)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "approve",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(theSpender)), 10)
														.payingWith(OWNER)
														.gas(500_000L)
														.via(approveTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getTxnRecord(approveTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec getErc20TokenDecimalsFromErc721TokenFails() {
		final var invalidDecimalsTxn = "decimalsFromErc721Txn";

		return defaultHapiSpec("ERC_20_DECIMALS_FROM_ERC_721_TOKEN")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						fileCreate(ERC_20_CONTRACT_NAME),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "decimals", asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))
												)
														.payingWith(ACCOUNT)
														.via(invalidDecimalsTxn)
														.hasKnownStatus(INVALID_TOKEN_ID)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(invalidDecimalsTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec transferErc20TokenFromErc721TokenFails() {
		final var invalidTransferTxn = "transferTxn";

		return defaultHapiSpec("ERC_20_TRANSFER_FROM_ERC_721_TOKEN")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						tokenAssociate(ACCOUNT, List.of(NON_FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
						cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).
								between(TOKEN_TREASURY, ACCOUNT)).payingWith(ACCOUNT),
						uploadInitCode(ERC_20_CONTRACT),
						contractCreate(ERC_20_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_20_CONTRACT, "transfer",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)), 2
												)
														.payingWith(ACCOUNT).alsoSigningWithFullPrefix(MULTI_KEY)
														.via(invalidTransferTxn).gas(GAS_TO_OFFER)
														.hasKnownStatus(INVALID_TOKEN_ID)
										)
						)
				).then(
						getTxnRecord(invalidTransferTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec getErc721TokenName() {
		final var tokenName = "TokenA";
		final var nameTxn = "nameTxn";

		return defaultHapiSpec("ERC_721_NAME")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.name(tokenName)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "name",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))
												)
														.payingWith(ACCOUNT)
														.via(nameTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(nameTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.NAME)
																.withName(tokenName)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc721Symbol() {
		final var tokenSymbol = "N";
		final var symbolTxn = "symbolTxn";

		return defaultHapiSpec("ERC_721_SYMBOL")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.symbol(tokenSymbol)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "symbol",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))
												)
														.payingWith(ACCOUNT)
														.via(symbolTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(symbolTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.SYMBOL)
																.withSymbol(tokenSymbol)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc721TokenURI() {
		final var tokenURITxn = "tokenURITxn";
		final var nonExistingTokenURITxn = "nonExistingTokenURITxn";
		final var ERC721MetadataNonExistingToken = "ERC721Metadata: URI query for nonexistent token";

		return defaultHapiSpec("ERC_721_TOKEN_URI")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "tokenURI",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)), 1
												)
														.payingWith(ACCOUNT)
														.via(tokenURITxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER),
												contractCall(ERC_721_CONTRACT,
														"tokenURI",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)), 2)
														.payingWith(ACCOUNT)
														.via(nonExistingTokenURITxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(tokenURITxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.TOKEN_URI)
																.withTokenUri("FIRST")
														)
										)
						),
						childRecordsCheck(nonExistingTokenURITxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.TOKEN_URI)
																.withTokenUri(ERC721MetadataNonExistingToken)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc721TotalSupply() {
		final var totalSupplyTxn = "totalSupplyTxn";

		return defaultHapiSpec("ERC_721_TOTAL_SUPPLY")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "totalSupply",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN))
												)
														.payingWith(ACCOUNT)
														.via(totalSupplyTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						childRecordsCheck(totalSupplyTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(
																		HTSPrecompileResult.FunctionType.TOTAL_SUPPLY)
																.withTotalSupply(1)
														)
										)
						)
				);
	}


	private HapiApiSpec getErc721BalanceOf() {
		final var balanceOfTxn = "balanceOfTxn";
		final var zeroBalanceOfTxn = "zbalanceOfTxn";

		return defaultHapiSpec("ERC_721_BALANCE_OF")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
						cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, OWNER)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "balanceOf",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER))
												)
														.payingWith(OWNER)
														.via(zeroBalanceOfTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER),
												tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
												cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1)
														.between(TOKEN_TREASURY, OWNER)),
												contractCall(ERC_721_CONTRACT,
														"balanceOf",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)))
														.payingWith(OWNER)
														.via(balanceOfTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						/* expect 0 returned from balanceOf() if the account and token are not associated -*/
						childRecordsCheck(zeroBalanceOfTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BALANCE)
																.withBalance(0)
														)
										)
						),
						childRecordsCheck(balanceOfTxn, SUCCESS,
								recordWith()
										.status(SUCCESS)
										.contractCallResult(
												resultWith()
														.contractCallResult(htsPrecompileResult()
																.forFunction(HTSPrecompileResult.FunctionType.BALANCE)
																.withBalance(1)
														)
										)
						)
				);
	}

	private HapiApiSpec getErc721OwnerOf() {
		final var ownerOfTxn = "ownerOfTxn";
		final AtomicReference<byte[]> ownerAddr = new AtomicReference<>();
		final AtomicReference<byte[]> tokenAddr = new AtomicReference<>();

		HapiApiSpec then = defaultHapiSpec("ERC_721_OWNER_OF")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
						cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1).between(TOKEN_TREASURY, OWNER)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "ownerOf",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)), 1
												)
														.payingWith(OWNER)
														.via(ownerOfTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						withOpContext(
								(spec, opLog) -> {
									ownerAddr.set(asAddress(spec.registry().getAccountID(OWNER)));
									allRunFor(
											spec,
											childRecordsCheck(ownerOfTxn, SUCCESS,
													recordWith()
															.status(SUCCESS)
															.contractCallResult(
																	resultWith()
																			.contractCallResult(htsPrecompileResult()
																					.forFunction(
																							HTSPrecompileResult.FunctionType.OWNER)
																					.withOwner(ownerAddr.get())
																			)
															)
											)
									);
								}
						),
						sourcing(() ->
								contractCallLocal(
										ERC_721_CONTRACT, "ownerOf", tokenAddr.get(), 1
								)
										.payingWith(OWNER)
										.gas(GAS_TO_OFFER))
				);
		return then;
	}

	private HapiApiSpec getErc721TransferFrom() {
		final var ownerNotAssignedToTokenTxn = "ownerNotAssignedToTokenTxn";
		final var transferFromOwnerTxn = "transferFromToAccountTxn";
		final var transferFromNotOwnerWithSignaturesTxn = "transferFromNotOwnerWithSignaturesTxn";
		final var transferWithZeroAddressesTxn = "transferWithZeroAddressesTxn";
		final var transferWithOwnerWithZeroAddressTxn = "transferWithOwnerWithZeroAddressTxn";
		final var transferWithRecipientWithZeroAddressTxn = "transferWithRecipientWithZeroAddressTxn";

		return defaultHapiSpec("ERC_721_TRANSFER_FROM")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(10 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														1)
														.payingWith(GENESIS)
														.via(ownerNotAssignedToTokenTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
												tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
												cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1, 2).
														between(TOKEN_TREASURY, OWNER)).payingWith(OWNER),
												contractCall(ERC_721_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														1)
														.payingWith(OWNER)
														.via(transferFromOwnerTxn)
														.hasKnownStatus(SUCCESS),
												newKeyNamed(TRANSFER_SIG_NAME).shape(
														SIMPLE.signedWith(ON)),
												cryptoUpdate(OWNER).key(TRANSFER_SIG_NAME),
												cryptoUpdate(RECIPIENT).key(TRANSFER_SIG_NAME),
												contractCall(ERC_721_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														2)
														.payingWith(GENESIS)
														.alsoSigningWithFullPrefix(TRANSFER_SIG_NAME)
														.via(transferFromNotOwnerWithSignaturesTxn)
														.hasKnownStatus(SUCCESS),
												contractCall(ERC_721_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														asAddress(AccountID.parseFrom(new byte[]{})),
														1)
														.payingWith(GENESIS)
														.via(transferWithZeroAddressesTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ERC_721_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														1)
														.payingWith(GENESIS)
														.via(transferWithOwnerWithZeroAddressTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED),
												contractCall(ERC_721_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														1)
														.payingWith(OWNER)
														.via(transferWithRecipientWithZeroAddressTxn)
														.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
										)

						)
				).then(
						getAccountInfo(OWNER).savingSnapshot(OWNER),
						getAccountInfo(RECIPIENT).savingSnapshot(RECIPIENT),
						withOpContext((spec, log) -> {
							final var sender = spec.registry().getAccountInfo(OWNER).getAccountID();
							final var receiver = spec.registry().getAccountInfo(RECIPIENT).getAccountID();

							var ownerNotAssignedToTokenRecord =
									getTxnRecord(ownerNotAssignedToTokenTxn).hasChildRecords(recordWith().status(INVALID_SIGNATURE)
									).andAllChildRecords().logged();

							var transferWithZeroAddressesRecord =
									getTxnRecord(transferWithZeroAddressesTxn).hasChildRecords(recordWith().status(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS)
									).andAllChildRecords().logged();

							var transferWithOwnerWithZeroAddressRecord =
									getTxnRecord(transferWithOwnerWithZeroAddressTxn).hasChildRecords(recordWith().status(INVALID_ACCOUNT_ID)
									).andAllChildRecords().logged();

							var transferWithRecipientWithZeroAddressRecord =
									getTxnRecord(transferWithRecipientWithZeroAddressTxn).hasChildRecords(recordWith().status(INVALID_ACCOUNT_ID)
									).andAllChildRecords().logged();

							var transferFromOwnerRecord =
									getTxnRecord(transferFromOwnerTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getAccountNum()),
															parsedToByteString(receiver.getAccountNum()),
															parsedToByteString(1)
													)))
											))).andAllChildRecords().logged();
							var transferFromNotOwnerWithSignaturesRecord =
									getTxnRecord(transferFromNotOwnerWithSignaturesTxn).hasPriority(recordWith().contractCallResult(resultWith()
											.logs(inOrder(logWith().withTopicsInOrder(List.of(
															eventSignatureOf(TRANSFER_SIGNATURE),
															parsedToByteString(sender.getAccountNum()),
															parsedToByteString(receiver.getAccountNum()),
															parsedToByteString(2)
													)))
											))).andAllChildRecords().logged();

							allRunFor(spec,
									ownerNotAssignedToTokenRecord,
									transferWithZeroAddressesRecord,
									transferWithOwnerWithZeroAddressRecord,
									transferWithRecipientWithZeroAddressRecord,
									transferFromOwnerRecord,
									transferFromNotOwnerWithSignaturesRecord
							);
						}),
						getAccountBalance(TOKEN_TREASURY)
								.hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
						getAccountBalance(OWNER)
								.hasTokenBalance(NON_FUNGIBLE_TOKEN, 0),
						getAccountBalance(RECIPIENT)
								.hasTokenBalance(NON_FUNGIBLE_TOKEN, 2)
				);
	}

	private HapiApiSpec getErc721TokenURIFromErc20TokenFails() {
		final var invalidTokenURITxn = "tokenURITxnFromErc20";

		return defaultHapiSpec("ERC_721_TOKEN_URI_FROM_ERC_20_TOKEN")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(10)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "tokenURI",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)), 1
												)
														.payingWith(ACCOUNT)
														.via(invalidTokenURITxn)
														.hasKnownStatus(INVALID_TOKEN_ID)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(invalidTokenURITxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec getErc721OwnerOfFromErc20TokenFails() {
		final var invalidOwnerOfTxn = "ownerOfTxnFromErc20Token";

		return defaultHapiSpec("ERC_721_OWNER_OF_FROM_ERC_20_TOKEN")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(FUNGIBLE_TOKEN)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.initialSupply(10)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						tokenAssociate(OWNER, List.of(FUNGIBLE_TOKEN)),
						cryptoTransfer(moving(3, FUNGIBLE_TOKEN).between(TOKEN_TREASURY, OWNER)),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "ownerOf",
														asAddress(spec.registry().getTokenID(FUNGIBLE_TOKEN)), 1
												)
														.payingWith(OWNER)
														.via(invalidOwnerOfTxn)
														.hasKnownStatus(INVALID_TOKEN_ID)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(invalidOwnerOfTxn).andAllChildRecords().logged()
				);
	}

	private HapiApiSpec erc721TokenApprove() {
		final var tokenName = "TokenA";
		final var nameTxn = "nameTxn";

		return defaultHapiSpec("ERC_721_APPROVE")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(ACCOUNT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(RECIPIENT).balance(100 * ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META)),
						tokenAssociate(ACCOUNT, List.of(NON_FUNGIBLE_TOKEN)),
						tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
						tokenAssociate(ERC_721_CONTRACT, List.of(NON_FUNGIBLE_TOKEN)),
						cryptoTransfer(movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, ERC_721_CONTRACT)
						)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "approve",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														1)
														.payingWith(ACCOUNT)
														.via(nameTxn)
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						)
				).then(
						getTxnRecord(nameTxn).andAllChildRecords().logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec erc721GetApproved() {
		final var theSpender = "spender";
		final var theSpender2 = "spender2";
		final var allowanceTxn = "allowanceTxn";

		return defaultHapiSpec("ERC_721_GET_APPROVED")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER)
								.balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(theSpender),
						cryptoCreate(theSpender2),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.initialSupply(0)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.treasury(TOKEN_TREASURY),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT),
						tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(
								ByteString.copyFromUtf8("a")
						)).via("nftTokenMint"),
						cryptoTransfer(
								movingUnique(NON_FUNGIBLE_TOKEN, 1L).between(TOKEN_TREASURY, OWNER)
						),
						cryptoApproveAllowance()
								.payingWith(DEFAULT_PAYER)
								.addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, theSpender, false, List.of(1L))
								.via("baseApproveTxn")
								.logged()
								.signedBy(DEFAULT_PAYER, OWNER)
								.fee(ONE_HBAR)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "getApproved",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														1)
														.payingWith(OWNER)
														.via(allowanceTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												childRecordsCheck(allowanceTxn, SUCCESS,
														recordWith()
																.status(SUCCESS)
																.contractCallResult(
																		resultWith()
																				.contractCallResult(htsPrecompileResult()
																						.forFunction(HTSPrecompileResult.FunctionType.GET_APPROVED)
																						.withApproved(asAddress(spec.registry().getAccountID(theSpender)))
																				)
																)
												)
										)
						),
						getTxnRecord(allowanceTxn).andAllChildRecords().logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec erc721SetApprovalForAll() {
		final var theSpender = "spender";
		final var theSpender2 = "spender2";
		final var allowanceTxn = "allowanceTxn";

		return defaultHapiSpec("ERC_721_SET_APPROVAL_FOR_ALL")
				.given(
						UtilVerbs.overriding("contracts.redirectTokenCalls", "true"),
						UtilVerbs.overriding("contracts.precompile.exportRecordResults", "true"),
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER)
								.balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						cryptoCreate(theSpender),
						cryptoCreate(theSpender2),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.initialSupply(0)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey(MULTI_KEY)
								.adminKey(MULTI_KEY)
								.treasury(TOKEN_TREASURY),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT),
						tokenAssociate(OWNER, NON_FUNGIBLE_TOKEN),
						tokenAssociate(ERC_721_CONTRACT, NON_FUNGIBLE_TOKEN),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(
								ByteString.copyFromUtf8("a")
						)).via("nftTokenMint"),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(
								ByteString.copyFromUtf8("b")
						)),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(
								ByteString.copyFromUtf8("c")
						)),
						cryptoTransfer(
								movingUnique(NON_FUNGIBLE_TOKEN, 1L, 2L).between(TOKEN_TREASURY, OWNER)
						)
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "setApprovalForAll",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(theSpender)),
														true)
														.payingWith(OWNER)
														.via(allowanceTxn)
														.hasKnownStatus(SUCCESS)
										)
						)
				).then(
						getTxnRecord(allowanceTxn).andAllChildRecords().logged(),
						UtilVerbs.resetAppPropertiesTo("src/main/resource/bootstrap.properties")
				);
	}

	private HapiApiSpec getErc721ClearsApprovalAfterTransfer() {
		final var transferFromOwnerTxn = "transferFromToAccountTxn";

		return defaultHapiSpec("ERC_721_CLEARS_APPROVAL_AFTER_TRANSFER")
				.given(
						newKeyNamed(MULTI_KEY),
						cryptoCreate(OWNER).balance(10 * ONE_MILLION_HBARS),
						cryptoCreate(RECIPIENT),
						cryptoCreate(TOKEN_TREASURY),
						tokenCreate(NON_FUNGIBLE_TOKEN)
								.tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
								.initialSupply(0)
								.treasury(TOKEN_TREASURY)
								.adminKey(MULTI_KEY)
								.supplyKey(MULTI_KEY),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT),
						mintToken(NON_FUNGIBLE_TOKEN, List.of(FIRST_META, SECOND_META))
				).when(withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												tokenAssociate(OWNER, List.of(NON_FUNGIBLE_TOKEN)),
												tokenAssociate(RECIPIENT, List.of(NON_FUNGIBLE_TOKEN)),
												cryptoTransfer(TokenMovement.movingUnique(NON_FUNGIBLE_TOKEN, 1, 2).
														between(TOKEN_TREASURY, OWNER)).payingWith(OWNER),
												cryptoApproveAllowance()
														.payingWith(OWNER)
														.addNftAllowance(OWNER, NON_FUNGIBLE_TOKEN, RECIPIENT, false, List.of(1L))
														.via("otherAdjustTxn"),
												getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasSpenderID(RECIPIENT),
												getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasNoSpender(),
												contractCall(ERC_721_CONTRACT, "transferFrom",
														asAddress(spec.registry().getTokenID(NON_FUNGIBLE_TOKEN)),
														asAddress(spec.registry().getAccountID(OWNER)),
														asAddress(spec.registry().getAccountID(RECIPIENT)),
														1)
														.payingWith(OWNER)
														.via(transferFromOwnerTxn)
														.hasKnownStatus(SUCCESS)
										)

						)
				).then(
						getAccountInfo(OWNER).logged(),
						getTokenNftInfo(NON_FUNGIBLE_TOKEN, 1L).hasNoSpender(),
						getTokenNftInfo(NON_FUNGIBLE_TOKEN, 2L).hasNoSpender()
				);
	}


	private HapiApiSpec erc721ApproveWithZeroAddressClearsPreviousApprovals() {
		final String owner = "owner";
		final String spender = "spender";
		final String spender1 = "spender1";
		final String nft = "nft";
		return defaultHapiSpec("ERC_721_APPROVE_WITH_ZERO_ADDRESS_CLEARS_PREVIOUS_APPROVALS")
				.given(
						cryptoCreate(owner)
								.balance(ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						uploadInitCode(ERC_721_CONTRACT),
						contractCreate(ERC_721_CONTRACT),
						newKeyNamed("supplyKey"),
						cryptoCreate(spender)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(spender1)
								.balance(ONE_HUNDRED_HBARS),
						cryptoCreate(TOKEN_TREASURY).balance(100 * ONE_HUNDRED_HBARS)
								.maxAutomaticTokenAssociations(10),
						tokenCreate(nft)
								.maxSupply(10L)
								.initialSupply(0)
								.supplyType(TokenSupplyType.FINITE)
								.tokenType(NON_FUNGIBLE_UNIQUE)
								.supplyKey("supplyKey")
								.treasury(TOKEN_TREASURY),
						tokenAssociate(owner, nft),
						mintToken(nft, List.of(
								ByteString.copyFromUtf8("a"),
								ByteString.copyFromUtf8("b"),
								ByteString.copyFromUtf8("c")
						)).via("nftTokenMint"),
						cryptoTransfer(movingUnique(nft, 1L, 2L, 3L)
								.between(TOKEN_TREASURY, owner))
				)
				.when(
						cryptoApproveAllowance()
								.payingWith(owner)
								.addNftAllowance(owner, nft, spender, false, List.of(1L, 2L))
								.addNftAllowance(owner, nft, spender1, false, List.of(3L)),
						getTokenNftInfo(nft, 1L).hasSpenderID(spender),
						getTokenNftInfo(nft, 2L).hasSpenderID(spender),
						getTokenNftInfo(nft, 3L).hasSpenderID(spender1)
				)
				.then(
						withOpContext(
								(spec, opLog) ->
										allRunFor(
												spec,
												contractCall(ERC_721_CONTRACT, "approve",
														asAddress(spec.registry().getTokenID(nft)),
														asAddress(AccountID.parseFrom(new byte[]{})),
														1)
														.payingWith(owner)
														.via("cryptoDeleteAllowanceTxn")
														.hasKnownStatus(SUCCESS)
														.gas(GAS_TO_OFFER)
										)
						),
						getTxnRecord("cryptoDeleteAllowanceTxn").logged(),
						getTokenNftInfo(nft, 1L).hasNoSpender(),
						getTokenNftInfo(nft, 2L).hasSpenderID(spender),
						getTokenNftInfo(nft, 3L).hasSpenderID(spender1)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}