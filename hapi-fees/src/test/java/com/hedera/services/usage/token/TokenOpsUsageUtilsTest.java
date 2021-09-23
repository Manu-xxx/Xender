package com.hedera.services.usage.token;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.StringValue;
import com.hedera.services.test.IdUtils;
import com.hedera.services.test.KeyUtils;
import com.hedera.services.usage.token.meta.TokenBurnMeta;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hedera.services.usage.token.meta.TokenMintMeta;
import com.hedera.services.usage.token.meta.TokenWipeMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenType;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.test.IdUtils.asAccount;
import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenOpsUsageUtilsTest {
	@Test
	void tokenCreateWithAutoRenewAccountWorks() {
		final var txn = givenTokenCreateWith(
				FUNGIBLE_COMMON, false, false, true, false);

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1062, tokenCreateMeta.getBaseSize());
		assertEquals(1_234_567L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_FUNGIBLE_COMMON, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(0, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	@Test
	void tokenCreateWithCustomFeesAndKeyWork() {
		final var txn = givenTokenCreateWith(
				FUNGIBLE_COMMON, true, true, false, true);

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1138, tokenCreateMeta.getBaseSize());
		assertEquals(1_111_111L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(32, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	@Test
	void tokenCreateWithCustomFeesWork() {
		final var txn = givenTokenCreateWith(
				FUNGIBLE_COMMON, false, true, false, true);

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1038, tokenCreateMeta.getBaseSize());
		assertEquals(1_111_111L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(32, tokenCreateMeta.getCustomFeeScheduleSize());
	}


	@Test
	void tokenCreateWithAutoRenewAcctNoCustomFeeKeyNoCustomFeesWorks() {
		final var txn = givenTokenCreateWith(NON_FUNGIBLE_UNIQUE,
				false, false, true, false);

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1062, tokenCreateMeta.getBaseSize());
		assertEquals(1_234_567L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(0, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	@Test
	void tokenCreateWithOutAutoRenewAcctAndCustomFeeKeyNoCustomFeesWorks() {
		final var txn = givenTokenCreateWith(NON_FUNGIBLE_UNIQUE,
				true, false, true, false);

		TokenCreateMeta tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1162, tokenCreateMeta.getBaseSize());
		assertEquals(1_234_567L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(0, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	@Test
	void tokenWipeFungibleCommonWorks() {
		final var txn = givenTokenWipeWith(FUNGIBLE_COMMON);

		TokenWipeMeta tokenWipeMeta = TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(txn);

		assertEquals(0, tokenWipeMeta.getSerialNumsCount());
		assertEquals(56, tokenWipeMeta.getTransferRecordDb());
	}

	@Test
	void tokenWipeNonFungibleUniqueWorks() {
		final var txn = givenTokenWipeWith(NON_FUNGIBLE_UNIQUE);

		TokenWipeMeta tokenWipeMeta = TOKEN_OPS_USAGE_UTILS.tokenWipeUsageFrom(txn);

		assertEquals(1, tokenWipeMeta.getSerialNumsCount());
		assertEquals(80, tokenWipeMeta.getTransferRecordDb());
	}

	@Test
	void tokenBurnFungibleCommonWorks() {
		final var txn = givenTokenBurnWith(FUNGIBLE_COMMON);

		TokenBurnMeta tokenBurnMeta = TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(txn);

		assertEquals(0, tokenBurnMeta.getSerialNumsCount());
		assertEquals(56, tokenBurnMeta.getTransferRecordDb());
	}

	@Test
	void tokenBurnNonFungibleUniqueWorks() {
		final var txn = givenTokenBurnWith(NON_FUNGIBLE_UNIQUE);

		TokenBurnMeta tokenBurnMeta = TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(txn);

		assertEquals(1, tokenBurnMeta.getSerialNumsCount());
		assertEquals(80, tokenBurnMeta.getTransferRecordDb());
	}


	@Test
	void tokenMintFungibleCommonWorks() {
		final var txn = givenTokenMintWith(FUNGIBLE_COMMON);

		TokenMintMeta tokenMintMeta = TOKEN_OPS_USAGE_UTILS.tokenMintUsageFrom(txn, TOKEN_FUNGIBLE_COMMON, 72000L);

		assertEquals(0, tokenMintMeta.getRbs());
		assertEquals(32, tokenMintMeta.getBpt());
		assertEquals(56, tokenMintMeta.getTransferRecordDb());
	}

	@Test
	void tokenMintNonFungibleUniqueWorks() {
		final var txn = givenTokenMintWith(NON_FUNGIBLE_UNIQUE);

		TokenMintMeta tokenMintMeta = TOKEN_OPS_USAGE_UTILS.tokenMintUsageFrom(txn, TOKEN_NON_FUNGIBLE_UNIQUE, 72000L);

		assertEquals(1296000, tokenMintMeta.getRbs());
		assertEquals(42, tokenMintMeta.getBpt());
		assertEquals(136, tokenMintMeta.getTransferRecordDb());
	}

	@Test
	void tokenCreateWithExpiryAndAutoRenewAcctAndCustomFeesAndKeyWorks() {
		final var txn = givenTokenCreateWith(NON_FUNGIBLE_UNIQUE,
				true, true, true, false);

		final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);

		assertEquals(1162, tokenCreateMeta.getBaseSize());
		assertEquals(1_234_567L, tokenCreateMeta.getLifeTime());
		assertEquals(TOKEN_NON_FUNGIBLE_UNIQUE_WITH_CUSTOM_FEES, tokenCreateMeta.getSubType());
		assertEquals(1, tokenCreateMeta.getNumTokens());
		assertEquals(0, tokenCreateMeta.getNftsTransfers());
		assertEquals(32, tokenCreateMeta.getCustomFeeScheduleSize());
	}

	@Test
	void tokenUpdateWithAutoRenewWorks() {
		final var txn = givenTokenUpdateWith(true, true, true);

		final var tokenUpdateMeta = TOKEN_OPS_USAGE_UTILS.tokenUpdateUsageFrom(txn);

		assertEquals(expiry, tokenUpdateMeta.getNewExpiry());
		assertEquals(now, tokenUpdateMeta.getNewEffectiveTxnStartTime());
		assertEquals(10, tokenUpdateMeta.getNewNameLen());
		assertEquals(10, tokenUpdateMeta.getNewSymLen());
		assertEquals(904, tokenUpdateMeta.getNewKeysLen());
		assertEquals(false, tokenUpdateMeta.hasTreasury());
		assertEquals(false, tokenUpdateMeta.getRemoveAutoRenewAccount());
		assertEquals(true, tokenUpdateMeta.hasAutoRenewAccount());
		assertEquals(0, tokenUpdateMeta.getNewAutoRenewPeriod());
	}

	@Test
	void tokenUpdateWithoutExpiryAndAutoRenewWorks() {
		final var txn = givenTokenUpdateWith(false, false, false);

		final var tokenUpdateMeta = TOKEN_OPS_USAGE_UTILS.tokenUpdateUsageFrom(txn);

		assertEquals(0, tokenUpdateMeta.getNewExpiry());
		assertEquals(now, tokenUpdateMeta.getNewEffectiveTxnStartTime());
		assertEquals(10, tokenUpdateMeta.getNewNameLen());
		assertEquals(10, tokenUpdateMeta.getNewSymLen());
		assertEquals(904, tokenUpdateMeta.getNewKeysLen());
		assertEquals(false, tokenUpdateMeta.hasTreasury());
		assertEquals(false, tokenUpdateMeta.getRemoveAutoRenewAccount());
		assertEquals(false, tokenUpdateMeta.hasAutoRenewAccount());
		assertEquals(0, tokenUpdateMeta.getNewAutoRenewPeriod());
	}


	private TransactionBody givenTokenUpdateWith(
			final boolean withAutoRenewAccount,
			final boolean withMemo,
			final boolean withExpiry) {
		final var builder = TokenUpdateTransactionBody.newBuilder()
				.setSymbol(symbol)
				.setName(name)
				.setKycKey(kycKey)
				.setAdminKey(adminKey)
				.setFreezeKey(freezeKey)
				.setSupplyKey(supplyKey)
				.setWipeKey(wipeKey)
				.setToken(tokenId);
		if(withAutoRenewAccount) {
			builder.setAutoRenewAccount(accountID);
		}
		if(withMemo) {
			builder.setMemo(StringValue.of("Some memo"));
		}
		if (withExpiry) {
			builder.setExpiry(Timestamp.newBuilder().setSeconds(expiry));
		}
		final var txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenUpdate(builder)
				.build();
		return txn;
	}


	private TransactionBody givenTokenCreateWith(
			final TokenType type,
			final boolean withCustomFeesKey,
			final boolean withCustomFees,
			final boolean withAutoRenewAccount,
			final boolean withInitialSupply) {
		final var builder = TokenCreateTransactionBody.newBuilder()
				.setTokenType(type)
				.setExpiry(Timestamp.newBuilder().setSeconds(expiry))
				.setSymbol(symbol)
				.setMemo(memo)
				.setName(name)
				.setKycKey(kycKey)
				.setAdminKey(adminKey)
				.setFreezeKey(freezeKey)
				.setSupplyKey(supplyKey)
				.setWipeKey(wipeKey);
		if(withInitialSupply) {
			builder.setInitialSupply(1000L);
		}
		if (withCustomFeesKey) {
			builder.setFeeScheduleKey(customFeeKey);
		}
		if (withCustomFees) {
			builder.addCustomFees(CustomFee.newBuilder()
					.setFeeCollectorAccountId(IdUtils.asAccount("0.0.1234"))
					.setFixedFee(FixedFee.newBuilder().setAmount(123)));
		}
		if (withAutoRenewAccount) {
			builder.setAutoRenewAccount(autoRenewAccount)
					.setAutoRenewPeriod(Duration.newBuilder().setSeconds(autoRenewPeriod));
		}
		final var txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenCreation(builder)
				.build();
		return txn;
	}

	private TransactionBody givenTokenWipeWith(
			final TokenType type) {
		final var op = TokenWipeAccountTransactionBody.newBuilder()
				.setToken(tokenId)
				.setAccount(accountID);
		if(type == FUNGIBLE_COMMON) {
			op.setAmount(100);
		} else {
			op.addAllSerialNumbers(List.of(1L));
		}
		final var txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenWipe(op.build())
				.build();
		return txn;
	}

	private TransactionBody givenTokenBurnWith(
			final TokenType type) {
		final var op = TokenBurnTransactionBody.newBuilder()
				.setToken(tokenId);
		if(type == FUNGIBLE_COMMON) {
			op.setAmount(100);
		} else {
			op.addAllSerialNumbers(List.of(1L));
		}
		final var txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenBurn(op.build())
				.build();
		return txn;
	}

	private TransactionBody givenTokenMintWith(
			final TokenType type) {
		final var op = TokenMintTransactionBody.newBuilder()
				.setToken(tokenId)
				;
		if(type == FUNGIBLE_COMMON) {
			op.setAmount(100);
		} else {
			op.addAllMetadata(List.of(
					ByteString.copyFromUtf8("NFT meta1"),
					ByteString.copyFromUtf8("NFT meta2")));
		}
		final var txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenMint(op.build())
				.build();
		return txn;
	}

	private static final Key kycKey = KeyUtils.A_COMPLEX_KEY;
	private static final Key adminKey = KeyUtils.A_THRESHOLD_KEY;
	private static final Key freezeKey = KeyUtils.A_KEY_LIST;
	private static final Key supplyKey = KeyUtils.B_COMPLEX_KEY;
	private static final Key wipeKey = KeyUtils.C_COMPLEX_KEY;
	private static final Key feeScheduleKey = KeyUtils.A_KEY_LIST;
	private static final Key customFeeKey = KeyUtils.A_THRESHOLD_KEY;
	private static final long expiry = 2_345_678L;
	private static final long autoRenewPeriod = 1_234_567L;
	private static final String symbol = "DUMMYTOKEN";
	private static final String name = "DummyToken";
	private static final String memo = "A simple test token create";
	private static final AccountID autoRenewAccount = asAccount("0.0.10001");
	private static final AccountID accountID = asAccount("0.0.10002");
	private static final TokenID tokenId = IdUtils.asToken("0.0.20001");

	private static final long now = 1_234_567L;
}
