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
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static com.hedera.services.utils.MiscUtils.readableTransferList;

public class CurrencyAdjustments implements SelfSerializable {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8b06bd46e12a466L;

	private static final long[] NO_ADJUSTMENTS = new long[0];
	static final int MAX_NUM_ADJUSTMENTS = 25;

	long[] hbars = NO_ADJUSTMENTS;
	long[] accountCodes = NO_ADJUSTMENTS;

	public CurrencyAdjustments() {
		/* For RuntimeConstructable */
	}

	public CurrencyAdjustments(long[] amounts, long[] parties) {
		hbars = amounts;
		accountCodes = parties;
	}

	public boolean isEmpty() {
		return hbars.length == 0;
	}

	/* --- SelfSerializable --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		accountCodes = in.readLongArray(MAX_NUM_ADJUSTMENTS);
		hbars = in.readLongArray(MAX_NUM_ADJUSTMENTS);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLongArray(accountCodes);
		out.writeLongArray(hbars);
	}

	/* ---- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || CurrencyAdjustments.class != o.getClass()) {
			return false;
		}

		CurrencyAdjustments that = (CurrencyAdjustments) o;
		return Arrays.equals(accountCodes, that.accountCodes) && Arrays.equals(hbars, that.hbars);
	}

	@Override
	public int hashCode() {
		int result = Long.hashCode(RUNTIME_CONSTRUCTABLE_ID);
		result = result * 31 + Integer.hashCode(MERKLE_VERSION);
		result = result * 31 + Arrays.hashCode(accountCodes);
		return result * 31 + Arrays.hashCode(hbars);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("readable", readableTransferList(toGrpc()))
				.toString();
	}

	/* --- Helpers --- */
	public TransferList toGrpc() {
		var grpc = TransferList.newBuilder();
		IntStream.range(0, hbars.length)
				.mapToObj(i -> AccountAmount.newBuilder()
						.setAmount(hbars[i])
						.setAccountID(EntityNum.fromLong(accountCodes[i]).toGrpcAccountId()))
				.forEach(grpc::addAccountAmounts);
		return grpc.build();
	}

	public static CurrencyAdjustments fromChanges(final long[] balanceChanges, final long[] changedAccounts) {
		final var pojo = new CurrencyAdjustments();
		final int n = balanceChanges.length;
		if (n > 0) {
			pojo.hbars = balanceChanges;
			pojo.accountCodes = changedAccounts;
		}
		return pojo;
	}

	public static CurrencyAdjustments fromGrpc(List<AccountAmount> adjustments) {
		final var pojo = new CurrencyAdjustments();
		final int n = adjustments.size();
		if (n > 0) {
			final var amounts = new long[n];
			final long[] accounts = { };
			for (var i = 0; i < n; i++) {
				final var adjustment = adjustments.get(i);
				amounts[i] = adjustment.getAmount();
				ArrayUtils.add(accounts, adjustment.getAccountID().getAccountNum());
			}
			pojo.hbars = amounts;
			pojo.accountCodes = accounts;
		}
		return pojo;
	}

	public long[] getHbars() {
		return hbars;
	}

	public long[] getAccountCodes() {
		return accountCodes;
	}
}
