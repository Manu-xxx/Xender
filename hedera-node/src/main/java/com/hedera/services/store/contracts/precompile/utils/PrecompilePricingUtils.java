package com.hedera.services.store.contracts.precompile.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.pricing.AssetsLoader;
import com.hedera.services.store.contracts.precompile.Precompile;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.FeeBuilder;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.pricing.FeeSchedules.USD_TO_TINYCENTS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.SubType.DEFAULT;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;

public class PrecompilePricingUtils {

	static class CanonicalOperationsUnloadableException extends RuntimeException {
		public CanonicalOperationsUnloadableException(Exception e) {
			super("Canonical prices for precompiles are not available", e);
		}
	}

	/**
	 * If we lack an entry (because of a bad data load), return a value that cannot reasonably be paid.
	 * In this case $1 Million Dollars.
	 */
	static final long COST_PROHIBITIVE = 1_000_000L * 10_000_000_000L;
	private final HbarCentExchange exchange;
	Map<GasCostType, Long> canonicalOperationCostsInTinyCents;

	@Inject
	public PrecompilePricingUtils(
			AssetsLoader assetsLoader,
			final HbarCentExchange exchange) {
		this.exchange = exchange;

		canonicalOperationCostsInTinyCents = new EnumMap<>(GasCostType.class);
		Map<HederaFunctionality, Map<SubType, BigDecimal>> canonicalPrices;
		try {
			canonicalPrices = assetsLoader.loadCanonicalPrices();
		} catch (IOException e) {
			throw new CanonicalOperationsUnloadableException(e);
		}
		for (var costType : GasCostType.values()) {
			if (canonicalPrices.containsKey(costType.functionality)) {
				BigDecimal costInUSD = canonicalPrices.get(costType.functionality).get(costType.subtype);
				if (costInUSD != null) {
					canonicalOperationCostsInTinyCents.put(costType,
							costInUSD.multiply(USD_TO_TINYCENTS).longValue());
				}
			}
		}
	}

	private long getCanonicalPriceInTinyCents(GasCostType gasCostType) {
		return canonicalOperationCostsInTinyCents.getOrDefault(gasCostType, COST_PROHIBITIVE);
	}

	public long getMinimumPriceInTinybars(GasCostType gasCostType, Timestamp timestamp) {
		return FeeBuilder.getTinybarsFromTinyCents(exchange.rate(timestamp),
				getCanonicalPriceInTinyCents(gasCostType));
	}

	public static long gasFeeInTinybars(final TransactionBody.Builder txBody,
								  final Instant consensusTime,
								  final Precompile precompile,
								  final Provider<FeeCalculator> feeCalculator,
								  final StateView currentView) {
		final var signedTxn = SignedTransaction.newBuilder()
				.setBodyBytes(txBody.build().toByteString())
				.setSigMap(SignatureMap.getDefaultInstance())
				.build();
		final var txn = Transaction.newBuilder()
				.setSignedTransactionBytes(signedTxn.toByteString())
				.build();

		final var accessor = SignedTxnAccessor.uncheckedFrom(txn);
		precompile.addImplicitCostsIn(accessor);
		final var fees = feeCalculator.get().computeFee(accessor, EMPTY_KEY, currentView, consensusTime);
		return fees.getServiceFee() + fees.getNetworkFee() + fees.getNodeFee();
	}

	public enum GasCostType {
		UNRECOGNIZED(HederaFunctionality.UNRECOGNIZED, SubType.UNRECOGNIZED),
		TRANSFER_FUNGIBLE(CryptoTransfer, TOKEN_FUNGIBLE_COMMON),
		TRANSFER_NFT(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE),
		TRANSFER_FUNGIBLE_CUSTOM_FEES(CryptoTransfer, TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES),
		TRANSFER_NFT_CUSTOM_FEES(CryptoTransfer, TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES),
		MINT_FUNGIBLE(TokenMint, TOKEN_FUNGIBLE_COMMON),
		MINT_NFT(TokenMint, TOKEN_NON_FUNGIBLE_UNIQUE),
		BURN_FUNGIBLE(TokenBurn, TOKEN_FUNGIBLE_COMMON),
		BURN_NFT(TokenBurn, TOKEN_NON_FUNGIBLE_UNIQUE),
		ASSOCIATE(TokenAssociateToAccount, DEFAULT),
		DISSOCIATE(TokenDissociateFromAccount, DEFAULT),
		APPROVE(CryptoApproveAllowance, DEFAULT),
		DELETE_NFT_APPROVE(CryptoDeleteAllowance, DEFAULT);

		final HederaFunctionality functionality;
		final SubType subtype;

		GasCostType(HederaFunctionality functionality, SubType subtype) {
			this.functionality = functionality;
			this.subtype = subtype;
		}
	}
}
