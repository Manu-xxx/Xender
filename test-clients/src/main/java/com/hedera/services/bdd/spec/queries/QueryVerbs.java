package com.hedera.services.bdd.spec.queries;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.consensus.HapiGetTopicInfo;
import com.hedera.services.bdd.spec.queries.contract.HapiContractCallLocal;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractBytecode;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractInfo;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractRecords;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountBalance;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountRecords;
import com.hedera.services.bdd.spec.queries.crypto.ReferenceType;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileInfo;
import com.hedera.services.bdd.spec.queries.meta.HapiGetExecTime;
import com.hedera.services.bdd.spec.queries.meta.HapiGetReceipt;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.queries.meta.HapiGetVersionInfo;
import com.hedera.services.bdd.spec.queries.schedule.HapiGetScheduleInfo;
import com.hedera.services.bdd.spec.queries.token.HapiGetAccountNftInfos;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenInfo;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenNftInfo;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenNftInfos;
import com.hederahashgraph.api.proto.java.TransactionID;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.queries.contract.HapiContractCallLocal.fromDetails;
import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.FUNCTION;
import static com.hedera.services.bdd.suites.contract.Utils.getABIFor;

public class QueryVerbs {
	public static HapiGetReceipt getReceipt(final String txn) {
		return new HapiGetReceipt(txn);
	}

	public static HapiGetReceipt getReceipt(final TransactionID txnId) {
		return new HapiGetReceipt(txnId);
	}

	public static HapiGetFileInfo getFileInfo(final String file) {
		return new HapiGetFileInfo(file);
	}

	public static HapiGetFileInfo getFileInfo(final Supplier<String> supplier) {
		return new HapiGetFileInfo(supplier);
	}

	public static HapiGetFileContents getFileContents(final String file) {
		return new HapiGetFileContents(file);
	}

	public static HapiGetAccountInfo getAccountInfo(final String account) {
		return new HapiGetAccountInfo(account);
	}

	public static HapiGetAccountInfo getAliasedAccountInfo(final String sourceKey) {
		return new HapiGetAccountInfo(sourceKey, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiGetAccountRecords getAccountRecords(final String account) {
		return new HapiGetAccountRecords(account);
	}

	public static HapiGetTxnRecord getTxnRecord(final String txn) {
		return new HapiGetTxnRecord(txn);
	}

	public static HapiGetTxnRecord getTxnRecord(final TransactionID txnId) {
		return new HapiGetTxnRecord(txnId);
	}

	public static HapiGetContractInfo getContractInfo(final String contract) {
		return new HapiGetContractInfo(contract);
	}

	public static HapiGetContractInfo getContractInfo(final String contract, final boolean idPredefined) {
		return new HapiGetContractInfo(contract, idPredefined);
	}

	public static HapiGetContractBytecode getContractBytecode(final String contract) {
		return new HapiGetContractBytecode(contract);
	}

	public static HapiGetContractRecords getContractRecords(final String contract) {
		return new HapiGetContractRecords(contract);
	}

	/*  TODO: remove the ternary operator after complete EETs refactor
		Note to the reviewer:
		the bellow implementation of the contractCall() method with ternary operator provides for backward compatibility
		and interoperability with the EETs, which call the method with a direct String ABI.
		The " functionName.charAt(0) == '{' " will be removed when all the EETs are refactored to depend on the new Utils.getABIFor()
		logic.
	*/
	public static HapiContractCallLocal contractCallLocal(
			final String contract,
			final String functionName,
			final Object... params
	) {
		return functionName.charAt(0) == '{'
				? new HapiContractCallLocal(functionName, contract, params)
				: new HapiContractCallLocal(getABIFor(FUNCTION, functionName, contract), contract, params);
	}

	public static HapiContractCallLocal contractCallLocalFrom(final String details) {
		return fromDetails(details);
	}

	public static HapiContractCallLocal contractCallLocal(
			final String contract, final String abi, final Function<HapiApiSpec, Object[]> fn
	) {
		return new HapiContractCallLocal(abi, contract, fn);
	}

	public static HapiGetAccountBalance getAccountBalance(final String account) {
		return new HapiGetAccountBalance(account);
	}

	public static HapiGetAccountBalance getAliasedAccountBalance(final String sourceKey) {
		return new HapiGetAccountBalance(sourceKey, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiGetAccountBalance getAccountBalance(final Supplier<String> supplier) {
		return new HapiGetAccountBalance(supplier);
	}

	public static HapiGetTopicInfo getTopicInfo(final String topic) {
		return new HapiGetTopicInfo(topic);
	}

	public static HapiGetVersionInfo getVersionInfo() {
		return new HapiGetVersionInfo();
	}

	public static HapiGetExecTime getExecTime(final String... txnIds) {
		return new HapiGetExecTime(List.of(txnIds)).nodePayment(1234L);
	}

	public static HapiGetExecTime getExecTimeNoPayment(final String... txnIds) {
		return new HapiGetExecTime(List.of(txnIds));
	}

	public static HapiGetTokenInfo getTokenInfo(final String token) {
		return new HapiGetTokenInfo(token);
	}

	public static HapiGetScheduleInfo getScheduleInfo(final String schedule) {
		return new HapiGetScheduleInfo(schedule);
	}

	public static HapiGetTokenNftInfo getTokenNftInfo(final String token, final long serialNum) {
		return new HapiGetTokenNftInfo(token, serialNum);
	}

	public static HapiGetTokenNftInfos getTokenNftInfos(final String token, final long start, final long end) {
		return new HapiGetTokenNftInfos(token, start, end);
	}

	public static HapiGetAccountNftInfos getAccountNftInfos(final String account, final long start, final long end) {
		return new HapiGetAccountNftInfos(account, start, end);
	}
}
