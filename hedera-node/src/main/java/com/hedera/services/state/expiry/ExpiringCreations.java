package com.hedera.services.state.expiry;

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
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcmap.FCMap;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcScheduleId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

public class ExpiringCreations implements EntityCreator {
	private RecordCache recordCache;

	private final ExpiryManager expiries;
	private final GlobalDynamicProperties dynamicProperties;
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public ExpiringCreations(
			ExpiryManager expiries,
			GlobalDynamicProperties dynamicProperties,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		this.accounts = accounts;
		this.expiries = expiries;
		this.dynamicProperties = dynamicProperties;
	}

	@Override
	public void setRecordCache(RecordCache recordCache) {
		this.recordCache = recordCache;
	}

	@Override
	public ExpirableTxnRecord saveExpiringRecord(
			AccountID payer,
			ExpirableTxnRecord expiringRecord,
			long now,
			long submittingMember
	) {
		final long expiry = now + dynamicProperties.cacheRecordsTtl();
		expiringRecord.setExpiry(expiry);
		expiringRecord.setSubmittingMember(submittingMember);

		if (dynamicProperties.shouldKeepRecordsInState()) {
			final var key = MerkleEntityId.fromAccountId(payer);
			addToState(key, expiringRecord);
			expiries.trackRecordInState(payer, expiringRecord.getExpiry());
		} else {
			recordCache.trackForExpiry(expiringRecord);
		}

		return expiringRecord;
	}

	private void addToState(MerkleEntityId key, ExpirableTxnRecord record) {
		final var currentAccounts = accounts.get();
		final var mutableAccount = currentAccounts.getForModify(key);
		mutableAccount.records().offer(record);
		currentAccounts.replace(key, mutableAccount);
	}

	@Override
	public ExpirableTxnRecord.Builder buildExpiringRecord(long otherNonThresholdFees, ByteString hash,
			TxnAccessor accessor, Instant consensusTime, TxnReceipt receipt, ServicesContext ctx) {
		final long amount = ctx.charging().totalFeesChargedToPayer() + otherNonThresholdFees;
		final TransferList transfersList = ctx.ledger().netTransfersInTxn();
		final List<TokenTransferList> tokenTransferList = ctx.ledger().netTokenTransfersInTxn();
		final var currencyAdjustments = transfersList.getAccountAmountsCount() > 0
				? CurrencyAdjustments.fromGrpc(transfersList) : null;
		final var scheduleRef = accessor.isTriggeredTxn() ? fromGrpcScheduleId(accessor.getScheduleRef()) : null;

		var builder = ExpirableTxnRecord.newBuilder()
				.setReceipt(receipt)
				.setTxnHash(hash.toByteArray())
				.setTxnId(TxnId.fromGrpc(accessor.getTxnId()))
				.setConsensusTime(RichInstant.fromJava(consensusTime))
				.setMemo(accessor.getTxn().getMemo())
				.setFee(amount)
				.setTransferList(currencyAdjustments)
				.setScheduleRef(scheduleRef);

		return setTokensAndTokenAdjustments(builder, tokenTransferList);
	}

	public static ExpirableTxnRecord.Builder setTokensAndTokenAdjustments(ExpirableTxnRecord.Builder builder,
			List<TokenTransferList> tokenTransferList) {
		List<EntityId> tokens = new ArrayList<>();
		List<CurrencyAdjustments> tokenAdjustments = new ArrayList<>();
		if (!tokenTransferList.isEmpty()) {
			for (TokenTransferList tokenTransfers : tokenTransferList) {
				tokens.add(EntityId.fromGrpcTokenId(tokenTransfers.getToken()));
				tokenAdjustments.add(CurrencyAdjustments.fromGrpc(tokenTransfers.getTransfersList()));
			}
		}
		builder.setTokens(tokens)
				.setTokenAdjustments(tokenAdjustments);
		return builder;
	}

	@Override
	public ExpirableTxnRecord.Builder buildFailedExpiringRecord(TxnAccessor accessor, Instant consensusTime) {
		var txnId = accessor.getTxnId();

		return ExpirableTxnRecord.newBuilder()
				.setTxnId(TxnId.fromGrpc(txnId))
				.setReceipt(TxnReceipt.newBuilder().setStatus(FAIL_INVALID.name()).build())
				.setMemo(accessor.getTxn().getMemo())
				.setTxnHash(accessor.getHash().toByteArray())
				.setConsensusTime(RichInstant.fromJava(consensusTime))
				.setScheduleRef(accessor.isTriggeredTxn() ? fromGrpcScheduleId(accessor.getScheduleRef()) : null);
	}
}
