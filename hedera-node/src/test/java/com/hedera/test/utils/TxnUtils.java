package com.hedera.test.utils;

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

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static com.hedera.test.factories.txns.CryptoTransferFactory.newSignedCryptoTransfer;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TxnUtils {
	public static TransferList withAdjustments(AccountID a, long A, AccountID b, long B, AccountID c, long C) {
		return TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(b).setAmount(B).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(c).setAmount(C).build())
				.build();
	}

	public static TransferList withAdjustments(
			AccountID a, long A,
			AccountID b, long B,
			AccountID c, long C,
			AccountID d, long D) {
		return TransferList.newBuilder()
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(b).setAmount(B).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(c).setAmount(C).build())
				.addAccountAmounts(AccountAmount.newBuilder().setAccountID(d).setAmount(D).build())
				.build();
	}

	public static List<TokenTransferList> withOwnershipChanges(
			TokenID a, AccountID aId, AccountID aCounterpartyId, long A,
			TokenID b, AccountID bId, AccountID bCounterpartyId, long B,
			TokenID c, AccountID cId, AccountID cCounterpartyId, long C
	) {
		return List.of(
				TokenTransferList.newBuilder()
						.setToken(a)
						.addNftTransfers(IdUtils.nftXfer(aId, aCounterpartyId, A))
						.build(),
				TokenTransferList.newBuilder()
						.setToken(b)
						.addNftTransfers(IdUtils.nftXfer(bId, bCounterpartyId, B))
						.build(),
				TokenTransferList.newBuilder()
						.setToken(c)
						.addNftTransfers(IdUtils.nftXfer(cId, cCounterpartyId, C))
						.build()
		);
	}

	public static TokenTransferList withNftAdjustments(
			TokenID a, AccountID aSenderId, AccountID aReceiverId, Long aSerialNumber,
			AccountID bSenderId, AccountID bReceiverId, Long bSerialNumber,
			AccountID cSenderId, AccountID cReceiverId, Long cSerialNumber
	) {
		return TokenTransferList.newBuilder()
						.setToken(a)
						.addNftTransfers(NftTransfer.newBuilder()
								.setSenderAccountID(aSenderId)
								.setReceiverAccountID(aReceiverId)
								.setSerialNumber(aSerialNumber))
						.addNftTransfers(NftTransfer.newBuilder()
								.setSenderAccountID(bSenderId)
								.setReceiverAccountID(bReceiverId)
								.setSerialNumber(bSerialNumber))
						.addNftTransfers(NftTransfer.newBuilder()
								.setSenderAccountID(cSenderId)
								.setReceiverAccountID(cReceiverId)
								.setSerialNumber(cSerialNumber))
						.build();
	}

	public static List<TokenTransferList> withTokenAdjustments(
			TokenID a, AccountID aId, long A,
			TokenID b, AccountID bId, long B,
			TokenID c, AccountID cId, long C
	) {
		return List.of(
				TokenTransferList.newBuilder()
						.setToken(a)
						.addTransfers(AccountAmount.newBuilder().setAccountID(aId).setAmount(A).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(b)
						.addTransfers(AccountAmount.newBuilder().setAccountID(bId).setAmount(B).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(c)
						.addTransfers(AccountAmount.newBuilder().setAccountID(cId).setAmount(C).build())
						.build()
		);
	}

	public static List<TokenTransferList> withTokenAdjustments(
			TokenID a, AccountID aId, long A,
			TokenID b, AccountID bId, long B,
			TokenID c, AccountID cId, long C,
			TokenID d, AccountID dId, long D
	) {
		return List.of(
				TokenTransferList.newBuilder()
						.setToken(a)
						.addTransfers(AccountAmount.newBuilder().setAccountID(aId).setAmount(A).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(b)
						.addTransfers(AccountAmount.newBuilder().setAccountID(bId).setAmount(B).build())
						.addTransfers(AccountAmount.newBuilder().setAccountID(cId).setAmount(C).build())
						.addTransfers(AccountAmount.newBuilder().setAccountID(aId).setAmount(A).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(c)
						.addTransfers(AccountAmount.newBuilder().setAccountID(cId).setAmount(C).build())
						.build(),
				TokenTransferList.newBuilder()
						.setToken(d)
						.addTransfers(AccountAmount.newBuilder().setAccountID(dId).setAmount(D).build())
						.build()
		);
	}

	public static List<TokenTransferList> withTokenAdjustments(
			TokenID a, TokenID b
	) {
		return List.of(
				TokenTransferList.newBuilder()
						.setToken(a)
						.build(),
				TokenTransferList.newBuilder()
						.setToken(b)
						.build()
		);
	}

	public static Transaction payerSponsoredTransfer(
			String payer,
			KeyTree payerKey,
			String beneficiary,
			long amount
	) throws Throwable {
		return newSignedCryptoTransfer()
				.payer(payer)
				.payerKt(payerKey)
				.transfers(tinyBarsFromTo(payer, beneficiary, amount))
				.get();
	}

	public static byte[] randomUtf8Bytes(int n) {
		byte[] data = new byte[n];
		int i = 0;
		while (i < n) {
			byte[] rnd = UUID.randomUUID().toString().getBytes();
			System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
			i += rnd.length;
		}
		return data;
	}

	public static ByteString randomUtf8ByteString(int n) {
		return ByteString.copyFrom(randomUtf8Bytes(n));
	}

	public static Timestamp timestampFrom(long secs, int nanos) {
		return Timestamp.newBuilder()
				.setSeconds(secs)
				.setNanos(nanos)
				.build();
	}

	public static Key.Builder nestKeys(Key.Builder builder, int additionalKeysToNest) {
		if (additionalKeysToNest == 0) {
			builder.setEd25519(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey().getEd25519());
			return builder;
		} else {
			var nestedBuilder = Key.newBuilder();
			nestKeys(nestedBuilder, additionalKeysToNest - 1);
			builder.setKeyList(KeyList.newBuilder().addKeys(nestedBuilder));
			return builder;
		}
	}

	public static JKey nestJKeys(int additionalKeysToNest) {
		if (additionalKeysToNest == 0) {
			return TxnHandlingScenario.SIMPLE_NEW_ADMIN_KT.asJKeyUnchecked();
		} else {
			final var descendantKeys = nestJKeys(additionalKeysToNest - 1);
			return new JKeyList(List.of(descendantKeys));
		}
	}


	public static void assertFailsWith(final Runnable something, final ResponseCodeEnum status) {
		final var ex = assertThrows(InvalidTransactionException.class, something::run);
		assertEquals(status, ex.getResponseCode());
	}

	public static <T extends SelfSerializable> void assertSerdeWorks(
			final T original,
			final Supplier<T> factory,
			final int version
	) throws IOException {
		final var baos = new ByteArrayOutputStream();
		final var out = new SerializableDataOutputStream(baos);
		original.serialize(out);
		;

		final var reconstruction = factory.get();

		final var bais = new ByteArrayInputStream(baos.toByteArray());
		final var in = new SerializableDataInputStream(bais);
		reconstruction.deserialize(in, version);

		assertEquals(original, reconstruction);
	}
}
