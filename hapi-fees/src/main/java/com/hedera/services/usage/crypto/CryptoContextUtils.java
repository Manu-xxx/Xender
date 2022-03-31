package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services API Fees
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

import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.GrantedCryptoAllowance;
import com.hederahashgraph.api.proto.java.GrantedNftAllowance;
import com.hederahashgraph.api.proto.java.GrantedTokenAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class CryptoContextUtils {
	private CryptoContextUtils() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static Map<Long, Long> convertToCryptoMapFromGranted(final List<GrantedCryptoAllowance> allowances) {
		Map<Long, Long> allowanceMap = new HashMap<>();
		for (var a : allowances) {
			allowanceMap.put(a.getSpender().getAccountNum(), a.getAmount());
		}
		return allowanceMap;
	}

	public static Map<AllowanceId, Long> convertToTokenMapFromGranted(
			final List<GrantedTokenAllowance> allowances) {
		Map<AllowanceId, Long> allowanceMap = new HashMap<>();
		for (var a : allowances) {
			allowanceMap.put(new AllowanceId(a.getTokenId().getTokenNum(),
					a.getSpender().getAccountNum()), a.getAmount());
		}
		return allowanceMap;
	}

	public static Set<AllowanceId> convertToNftMapFromGranted(
			final List<GrantedNftAllowance> allowances) {
		Set<AllowanceId> approveForAllAllowances = new TreeSet<>();
		for (var a : allowances) {
			approveForAllAllowances.add(new AllowanceId(a.getTokenId().getTokenNum(),
							a.getSpender().getAccountNum()));
		}
		return approveForAllAllowances;
	}

	public static Map<Long, Long> convertToCryptoMap(final List<CryptoAllowance> allowances) {
		Map<Long, Long> allowanceMap = new HashMap<>();
		for (var a : allowances) {
			allowanceMap.put(a.getSpender().getAccountNum(), a.getAmount());
		}
		return allowanceMap;
	}

	public static Map<AllowanceId, Long> convertToTokenMap(
			final List<TokenAllowance> allowances) {
		Map<AllowanceId, Long> allowanceMap = new HashMap<>();
		for (var a : allowances) {
			allowanceMap.put(new AllowanceId(a.getTokenId().getTokenNum(),
					a.getSpender().getAccountNum()), a.getAmount());
		}
		return allowanceMap;
	}

	public static Map<AllowanceId, AllowanceDetails> convertToNftMap(
			final List<NftAllowance> allowances) {
		Map<AllowanceId, AllowanceDetails> allowanceMap =
				new HashMap<>();
		for (var a : allowances) {
			allowanceMap.put(new AllowanceId(a.getTokenId().getTokenNum(),
							a.getSpender().getAccountNum()),
					new AllowanceDetails(a.getApprovedForAll().getValue(),
							a.getSerialNumbersList()));
		}
		return allowanceMap;
	}

	public static int countSerials(final List<NftAllowance> nftAllowancesList) {
		int totalSerials = 0;
		for (var allowance : nftAllowancesList) {
			totalSerials += allowance.getSerialNumbersCount();
		}
		return totalSerials;
	}

	static int getNewSerials(
			final Map<AllowanceId, AllowanceDetails> newAllowances) {
		int counter = 0;
		for (var a : newAllowances.entrySet()) {
			counter += a.getValue().serialNums().size();
		}
		return counter;
	}


	static int getChangedCryptoKeys(final Set<Long> newKeys, final Set<Long> existingKeys) {
		int counter = 0;
		for (var key : newKeys) {
			if (!existingKeys.contains(key)) {
				counter++;
			}
		}
		return counter;
	}

	static int getChangedTokenKeys(final Set<AllowanceId> newKeys,
			final Set<AllowanceId> existingKeys) {
		int counter = 0;
		for (var key : newKeys) {
			if (!existingKeys.contains(key)) {
				counter++;
			}
		}
		return counter;
	}
}
