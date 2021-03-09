package com.hedera.services.bdd.spec.queries.crypto;

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

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.suites.crypto.CryptoTransferSuite;
import com.hedera.services.stream.proto.SingleAccountBalances;
import com.hedera.services.stream.proto.TokenUnitBalance;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.OwnedNfts;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBalance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTokenId;
import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiApiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.bdd.suites.crypto.CryptoTransferSuite.sdec;
import static java.util.stream.Collectors.joining;

public class HapiGetAccountBalance extends HapiQueryOp<HapiGetAccountBalance> {
	private static final Logger log = LogManager.getLogger(HapiGetAccountBalance.class);

	private Pattern DOT_DELIMTED_ACCOUNT = Pattern.compile("\\d+[.]\\d+[.]\\d+");
	private String entity;
	private Optional<AccountID> accountID = Optional.empty();
	private boolean exportAccount = false;
	Optional<Long> expected = Optional.empty();
	Optional<Supplier<String>> entityFn = Optional.empty();
	Optional<Function<HapiApiSpec, Function<Long, Optional<String>>>> expectedCondition = Optional.empty();

	List<Map.Entry<String, String>> expectedTokenBalances = Collections.EMPTY_LIST;

	public HapiGetAccountBalance(String entity) {
		this.entity = entity;
	}

	public HapiGetAccountBalance(Supplier<String> supplier) {
		this.entityFn = Optional.of(supplier);
	}

	public HapiGetAccountBalance hasTinyBars(long amount) {
		expected = Optional.of(amount);
		return this;
	}

	public HapiGetAccountBalance hasTinyBars(Function<HapiApiSpec, Function<Long, Optional<String>>> condition) {
		expectedCondition = Optional.of(condition);
		return this;
	}

	public HapiGetAccountBalance hasTokenBalance(String token, long amount) {
		if (expectedTokenBalances.isEmpty()) {
			expectedTokenBalances = new ArrayList<>();
		}
		expectedTokenBalances.add(new AbstractMap.SimpleImmutableEntry<>(token, amount + "-G"));
		return this;
	}

	public HapiGetAccountBalance hasTokenBalance(String token, long amount, int decimals) {
		if (expectedTokenBalances.isEmpty()) {
			expectedTokenBalances = new ArrayList<>();
		}
		expectedTokenBalances.add(new AbstractMap.SimpleImmutableEntry<>(token, amount + "-" + decimals));
		return this;
	}

	public HapiGetAccountBalance persists(boolean toExport) {
		exportAccount = toExport;
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoGetAccountBalance;
	}

	@Override
	protected HapiGetAccountBalance self() {
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		long actual = response.getCryptogetAccountBalance().getBalance();
		if (verboseLoggingOn) {
			log.info("Assets for '{}' (0.0.{}) include,\n  ℏ: {}\n  FTs: {}\n  NFTs: {}",
					entity,
					response.getCryptogetAccountBalance().getAccountID().getAccountNum(),
					readableHbar(actual),
					response.getCryptogetAccountBalance().getTokenBalancesList(),
					readableNftOwnership(response.getCryptogetAccountBalance().getOwnedNftsList()));
		}
		if (expectedCondition.isPresent()) {
			Function<Long, Optional<String>> condition = expectedCondition.get().apply(spec);
			Optional<String> failure = condition.apply(actual);
			if (failure.isPresent()) {
				Assert.fail("Bad balance! :: " + failure.get());
			}
		} else if (expected.isPresent()) {
			Assert.assertEquals("Wrong balance!", expected.get().longValue(), actual);
		}

		if (expectedTokenBalances.size() > 0) {
			Pair<Long, Integer> defaultTb = Pair.of(0L, 0);
			Map<TokenID, Pair<Long, Integer>> actualTokenBalances =
					response.getCryptogetAccountBalance().getTokenBalancesList()
							.stream()
							.collect(Collectors.toMap(
									TokenBalance::getTokenId,
									tb -> Pair.of(tb.getBalance(), tb.getDecimals())));
			for (Map.Entry<String, String> tokenBalance : expectedTokenBalances) {
				var tokenId = asTokenId(tokenBalance.getKey(), spec);
				String[] expectedParts = tokenBalance.getValue().split("-");
				Long expectedBalance = Long.valueOf(expectedParts[0]);
				Assert.assertEquals(String.format(
						"Wrong balance for token '%s'!", HapiPropertySource.asTokenString(tokenId)),
						expectedBalance,
						actualTokenBalances.getOrDefault(tokenId, defaultTb).getLeft());
				if (!"G".equals(expectedParts[1])) {
					Integer expectedDecimals = Integer.valueOf(expectedParts[1]);
					Assert.assertEquals(String.format(
							"Wrong decimals for token '%s'!", HapiPropertySource.asTokenString(tokenId)),
							expectedDecimals,
							actualTokenBalances.getOrDefault(tokenId, defaultTb).getRight());
				}
			}
		}

		if (exportAccount && accountID.isPresent()) {
			SingleAccountBalances.Builder sab = SingleAccountBalances.newBuilder();
			List<TokenUnitBalance> tokenUnitBalanceList = new ArrayList<>();
			tokenUnitBalanceList = response.getCryptogetAccountBalance().getTokenBalancesList()
					.stream()
					.map(a -> TokenUnitBalance.newBuilder()
							.setTokenId(a.getTokenId())
							.setBalance(a.getBalance())
							.build())
					.collect(Collectors.toList());
			sab.setAccountID(accountID.get())
					.setHbarBalance(response.getCryptogetAccountBalance().getBalance())
					.addAllTokenUnitBalances(tokenUnitBalanceList);
			spec.saveSingleAccountBalances(sab.build());
		}
	}

	private String readableNftOwnership(List<OwnedNfts> owned) {
		var sb = new StringBuilder();
		for (OwnedNfts ownedNfts : owned) {
			sb.append("\n    * ")
					.append(HapiPropertySource.asNftString(ownedNfts.getNftId()))
					.append("{")
					.append(ownedNfts.getSerialNoList().stream()
							.map(bs -> bs.toStringUtf8().trim())
							.collect(joining(", ")))
					.append("}");
		}
		return sb.toString();
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getAccountBalanceQuery(spec, payment, false);
		response = spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls).cryptoGetBalance(query);
		ResponseCodeEnum status = response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();
		if (status == ResponseCodeEnum.ACCOUNT_DELETED) {
			log.info(spec.logPrefix() + entity + " was actually deleted!");
		}
	}

	private String readableHbar(long tinybars) {
		return tinybars + "tℏ ("
				+ sdec((1.0 * tinybars) / ONE_HBAR, 2) + "ħ)";
	}

	private Query getAccountBalanceQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		if (entityFn.isPresent()) {
			entity = entityFn.get().get();
		}
		Consumer<CryptoGetAccountBalanceQuery.Builder> config;
		if (spec.registry().hasContractId(entity)) {
			config = b -> b.setContractID(spec.registry().getContractId(entity));
		} else {
			Matcher m = DOT_DELIMTED_ACCOUNT.matcher(entity);
			AccountID id = m.matches() ? HapiPropertySource.asAccount(entity) : spec.registry().getAccountID(entity);
			config = b -> b.setAccountID(id);
			accountID = Optional.of(id);
		}
		CryptoGetAccountBalanceQuery.Builder query = CryptoGetAccountBalanceQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment));
		config.accept(query);
		return Query.newBuilder().setCryptogetAccountBalance(query).build();
	}

	@Override
	protected boolean needsPayment() {
		return false;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper().add("account", entity);
	}
}
