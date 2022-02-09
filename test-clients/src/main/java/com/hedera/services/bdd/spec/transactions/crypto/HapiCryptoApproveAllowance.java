package com.hedera.services.bdd.spec.transactions.crypto;

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
import com.google.protobuf.BoolValue;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiCryptoApproveAllowance extends HapiTxnOp<HapiCryptoApproveAllowance> {
	static final Logger log = LogManager.getLogger(HapiCryptoApproveAllowance.class);

	private List<CryptoAllowances> cryptoAllowances = new ArrayList<>();
	private List<TokenAllowances> tokenAllowances = new ArrayList<>();
	private List<NftAllowances> nftAllowances = new ArrayList<>();

	public HapiCryptoApproveAllowance() {
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoApproveAllowance;
	}

	@Override
	protected HapiCryptoApproveAllowance self() {
		return this;
	}

	public HapiCryptoApproveAllowance addCryptoAllowance(String owner, String spender, long allowance) {
		cryptoAllowances.add(CryptoAllowances.from(owner, spender, allowance));
		return this;
	}

	public HapiCryptoApproveAllowance addTokenAllowance(String owner, String token, String spender, long allowance) {
		tokenAllowances.add(TokenAllowances.from(owner, token, spender, allowance));
		return this;
	}

	public HapiCryptoApproveAllowance addNftAllowance(String owner, String token, String spender,
			boolean approvedForAll,
			List<Long> serials) {
		nftAllowances.add(NftAllowances.from(owner, token, spender, approvedForAll, serials));
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		try {
			final CryptoGetInfoResponse.AccountInfo info = lookupInfo(spec, effectivePayer(spec));
			FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) -> {
				var ctx = ExtantCryptoContext.newBuilder()
						.setCurrentNumTokenRels(info.getTokenRelationshipsCount())
						.setCurrentExpiry(info.getExpirationTime().getSeconds())
						.setCurrentMemo(info.getMemo())
						.setCurrentKey(info.getKey())
						.setCurrentlyHasProxy(info.hasProxyAccountID())
						.setCurrentMaxAutomaticAssociations(info.getMaxAutomaticTokenAssociations())
						.setCurrentCryptoAllowances(info.getCryptoAllowancesList())
						.setCurrentTokenAllowances(info.getTokenAllowancesList())
						.setCurrentNftAllowances(info.getNftAllowancesList())
						.build();
				var baseMeta = new BaseTransactionMeta(_txn.getMemoBytes().size(), 0);
				var opMeta = new CryptoApproveAllowanceMeta(_txn.getCryptoApproveAllowance(),
						_txn.getTransactionID().getTransactionValidStart().getSeconds());
				var accumulator = new UsageAccumulator();
				cryptoOpsUsage.cryptoApproveAllowanceUsage(suFrom(svo), baseMeta, opMeta, ctx, accumulator);
				return AdapterUtils.feeDataFrom(accumulator);
			};
			return spec.fees().forActivityBasedOp(HederaFunctionality.CryptoApproveAllowance, metricsCalc, txn,
					numPayerKeys);
		} catch (Throwable ignore) {
			return HapiApiSuite.ONE_HBAR;
		}
	}

	private CryptoGetInfoResponse.AccountInfo lookupInfo(HapiApiSpec spec, String payer) throws Throwable {
		HapiGetAccountInfo subOp = getAccountInfo(payer).noLogging();
		Optional<Throwable> error = subOp.execFor(spec);
		if (error.isPresent()) {
			if (!loggingOff) {
				log.warn("Unable to look up current account info!", error.get());
			}
			throw error.get();
		}
		return subOp.getResponse().getCryptoGetInfo().getAccountInfo();
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		List<CryptoAllowance> callowances = new ArrayList<>();
		List<TokenAllowance> tallowances = new ArrayList<>();
		List<NftAllowance> nftallowances = new ArrayList<>();
		calculateAllowances(spec, callowances, tallowances, nftallowances);
		CryptoApproveAllowanceTransactionBody opBody = spec
				.txns()
				.<CryptoApproveAllowanceTransactionBody, CryptoApproveAllowanceTransactionBody.Builder>body(
						CryptoApproveAllowanceTransactionBody.class, b -> {
							b.addAllTokenAllowances(tallowances);
							b.addAllCryptoAllowances(callowances);
							b.addAllNftAllowances(nftallowances);
						});
		return b -> b.setCryptoApproveAllowance(opBody);
	}

	private void calculateAllowances(final HapiApiSpec spec,
			final List<CryptoAllowance> callowances,
			final List<TokenAllowance> tallowances,
			final List<NftAllowance> nftallowances) {
		for (var entry : cryptoAllowances) {
			final var builder = CryptoAllowance.newBuilder()
					.setSpender(spec.registry().getAccountID(entry.spender()))
					.setOwner(spec.registry().getAccountID(entry.owner()))
					.setAmount(entry.amount())
					.build();
			callowances.add(builder);
		}

		for (var entry : tokenAllowances) {
			final var builder = TokenAllowance.newBuilder()
					.setTokenId(spec.registry().getTokenID(entry.token()))
					.setSpender(spec.registry().getAccountID(entry.spender()))
					.setOwner(spec.registry().getAccountID(entry.owner()))
					.setAmount(entry.amount())
					.build();
			tallowances.add(builder);
		}
		for (var entry : nftAllowances) {
			final var builder = NftAllowance.newBuilder()
					.setTokenId(spec.registry().getTokenID(entry.token()))
					.setSpender(spec.registry().getAccountID(entry.spender()))
					.setApprovedForAll(BoolValue.of(entry.approvedForAll()))
					.setOwner(spec.registry().getAccountID(entry.owner()))
					.addAllSerialNumbers(entry.serials())
					.build();
			nftallowances.add(builder);
		}
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return Arrays.asList(
				spec -> spec.registry().getKey(effectivePayer(spec)));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls)::approveAllowances;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("cryptoAllowances", cryptoAllowances)
				.add("tokenAllowances", tokenAllowances)
				.add("nftAllowances", nftAllowances);
		return helper;
	}

	private record CryptoAllowances(String owner, String spender, Long amount) {
		static CryptoAllowances from(String owner, String spender, Long amount) {
			return new CryptoAllowances(owner, spender, amount);
		}
	}

	private record TokenAllowances(String owner, String token, String spender, long amount) {
		static TokenAllowances from(String owner, String token, String spender, long amount) {
			return new TokenAllowances(owner, token, spender, amount);
		}
	}

	private record NftAllowances(String owner, String token, String spender, boolean approvedForAll,
								 List<Long> serials) {
		static NftAllowances from(String owner, String token, String spender, boolean approvedForAll,
				List<Long> serials) {
			return new NftAllowances(owner, token, spender, approvedForAll, serials);
		}
	}
}
