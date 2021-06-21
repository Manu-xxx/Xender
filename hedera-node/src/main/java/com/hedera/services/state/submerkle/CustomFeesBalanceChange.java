package com.hedera.services.state.submerkle;

/*-
 * ‌
 * Hedera Services Node
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
import com.hedera.services.ledger.HederaLedger;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import proto.CustomFeesOuterClass;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Process object that encapsulates a balance change, either ℏ or token unit.
 *
 * Includes an optional override for the {@link ResponseCodeEnum} to be used
 * in the case that the change is determined to result to an insufficient balance;
 * and a field to contain the new balance that will result from the change.
 * (This field is helpful to simplify work done in {@link HederaLedger}.)
 *
 * The {@code tokenId} and {@code accountId} fields are
 * temporary, needed to interact with the {@link com.hedera.services.ledger.accounts.BackingAccounts}
 * and {@link com.hedera.services.ledger.accounts.BackingTokenRels} components
 * whose APIs still use gRPC types.
 */
public class CustomFeesBalanceChange implements SelfSerializable {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8b56ce46e56a466L;

	static final TokenID NO_TOKEN_FOR_HBAR_ADJUST = TokenID.getDefaultInstance();

	private EntityId token;
	private EntityId account;
	private long units;


	private CustomFeesBalanceChange(final EntityId token, final AccountAmount aa) {
		this.token = token;
		this.account = EntityId.fromGrpcAccountId(aa.getAccountID());
		this.units = aa.getAmount();
	}

	public CustomFeesBalanceChange(final EntityId account, final long amount) {
		this.token = null;
		this.account = account;
		this.units = amount;
	}

	public CustomFeesBalanceChange(final EntityId account, final EntityId token, final long amount) {
		this.token = token;
		this.account = account;
		this.units = amount;
	}

	public boolean isForHbar() {
		return token == null;
	}

	public long units() {
		return units;
	}


	public static CustomFeesBalanceChange hbarAdjust(final AccountAmount aa) {
		return new CustomFeesBalanceChange(null, aa);
	}

	public static CustomFeesBalanceChange tokenAdjust(final EntityId token, final AccountAmount aa) {
		final var tokenChange = new CustomFeesBalanceChange(token, aa);
		return tokenChange;
	}

	/* NOTE: The object methods below are only overridden to improve readability of unit tests;
	this model object is not used in hash-based collections, so the performance of these
	methods doesn't matter. */

	@Override
	public boolean equals(final Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(CustomFeesBalanceChange.class)
				.add("token", token == null ? "ℏ" : token)
				.add("account", account)
				.add("units", units)
				.toString();
	}

	/* --- Helpers --- */

	public CustomFeesOuterClass.CustomFeeCharged toGrpc() {
		var grpc = CustomFeesOuterClass.CustomFeeCharged.newBuilder()
				.setFeeCollector(account.toGrpcAccountId())
				.setUnitsCharged(units);
		if (isForHbar()) {
			return grpc.build();
		}
		return grpc.setTokenId(token.toGrpcTokenId()).build();
	}

	public static List<CustomFeesBalanceChange> fromGrpc(CustomFeesOuterClass.CustomFeesCharged grpc) {
		return grpc.getCustomFeesChargedList()
				.stream()
				.map(i -> fromGrpc(i))
				.collect(Collectors.toList());
	}

	public static CustomFeesBalanceChange fromGrpc(CustomFeesOuterClass.CustomFeeCharged customFeesCharged) {
		AccountAmount aa = AccountAmount.newBuilder()
				.setAccountID(customFeesCharged.getFeeCollector())
				.setAmount(customFeesCharged.getUnitsCharged()).build();
		if (customFeesCharged.getTokenId() != null) {
			return tokenAdjust(EntityId.fromGrpcTokenId(customFeesCharged.getTokenId()), aa);
		}
		return hbarAdjust(aa);
	}

	public static CustomFeesOuterClass.CustomFeesCharged toGrpc(List<CustomFeesBalanceChange> balanceChanges) {
		return CustomFeesOuterClass.CustomFeesCharged.newBuilder()
				.addAllCustomFeesCharged(
						balanceChanges
								.stream()
								.map(i -> i.toGrpc())
								.collect(Collectors.toList()))
				.build();
	}

	/* SelfSerializable methods */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		account = in.readSerializable();
		token = in.readSerializable();
		units = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(account, true);
		out.writeSerializable(token, true);
		out.writeLong(units);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}
}
