package com.hedera.services.grpc.marshalling;

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
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.state.submerkle.CustomFee;
import com.hedera.services.state.submerkle.CustomFeesBalanceChange;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javafx.util.Pair;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collections;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

/**
 * Encapsulates the result of translating a gRPC CryptoTransfer into a list of
 * balance changes (ℏ or token unit), as well as the validity of these changes.
 *
 * Note that if the {@link ImpliedTransfersMeta#code()} is not {@code OK}, the
 * list of changes will always be empty.
 */
public class ImpliedTransfers {
	private final ImpliedTransfersMeta meta;
	private final List<BalanceChange> changes;
	private final List<Pair<EntityId, List<CustomFee>>> entityCustomFees;
	private final List<CustomFeesBalanceChange> customFeesBalanceChanges;

	private ImpliedTransfers(ImpliedTransfersMeta meta, List<BalanceChange> changes,
			List<Pair<EntityId, List<CustomFee>>> entityCustomFees,
			List<CustomFeesBalanceChange> customFeeBalanceChanges) {
		this.meta = meta;
		this.changes = changes;
		this.entityCustomFees = entityCustomFees;
		this.customFeesBalanceChanges = customFeeBalanceChanges;
	}

	public static ImpliedTransfers valid(
			int maxHbarAdjusts,
			int maxTokenAdjusts,
			List<BalanceChange> changes,
			List<Pair<EntityId, List<CustomFee>>> entityCustomFees,
			List<CustomFeesBalanceChange> customFeeBalanceChanges
	) {
		final var meta = new ImpliedTransfersMeta(maxHbarAdjusts, maxTokenAdjusts, OK, entityCustomFees);
		return new ImpliedTransfers(meta, changes, entityCustomFees, customFeeBalanceChanges);
	}

	public static ImpliedTransfers invalid(
			int maxHbarAdjusts,
			int maxTokenAdjusts,
			ResponseCodeEnum code
	) {
		final var meta = new ImpliedTransfersMeta(maxHbarAdjusts, maxTokenAdjusts, code, Collections.emptyList());
		return new ImpliedTransfers(meta, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
	}

	public ImpliedTransfersMeta getMeta() {
		return meta;
	}

	public List<BalanceChange> getAllBalanceChanges() {
		return changes;
	}

	public List<Pair<EntityId, List<CustomFee>>> getEntityCustomFees() {
		return entityCustomFees;
	}

	public List<CustomFeesBalanceChange> getCustomFeesBalanceChanges() {
		return customFeesBalanceChanges;
	}

	/* NOTE: The object methods below are only overridden to improve
			readability of unit tests; this model object is not used in hash-based
			collections, so the performance of these methods doesn't matter. */
	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(ImpliedTransfers.class)
				.add("meta", meta)
				.add("changes", changes)
				.add("entityCustomFees", entityCustomFees)
				.add("customFeesBalanceChanges", customFeesBalanceChanges)
				.toString();
	}
}
