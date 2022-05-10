package com.hedera.services.txns.validation;

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

import com.hedera.services.context.NodeInfo;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class PureValidationTest {
	private static final Instant now = Instant.now();
	private static final long impossiblySmallSecs = Instant.MIN.getEpochSecond() - 1;
	private static final int impossiblySmallNanos = -1;
	private static final long impossiblyBigSecs = Instant.MAX.getEpochSecond() + 1;
	private static final int impossiblyBigNanos = 1_000_000_000;
	private static final NodeInfo nodeInfo = mock(NodeInfo.class);

	@Test
	void mapsSensibleTimestamp() {
		final var proto = TxnUtils.timestampFrom(now.getEpochSecond(), now.getNano());

		assertEquals(now, PureValidation.asCoercedInstant(proto));
	}

	@Test
	void coercesTooSmallTimestamp() {
		final var proto = TxnUtils.timestampFrom(impossiblySmallSecs, impossiblySmallNanos);

		assertEquals(Instant.MIN, PureValidation.asCoercedInstant(proto));
	}

	@Test
	void coercesTooBigTimestamp() {
		final var proto = TxnUtils.timestampFrom(impossiblyBigSecs, impossiblyBigNanos);

		assertEquals(Instant.MAX, PureValidation.asCoercedInstant(proto));
	}

	@Test
	void validatesStakedId() {
		final var stakedAccountID = asAccount("0.0.2");
		final var stakedNodeId = 0;
		final MerkleMap<EntityNum, MerkleAccount> accounts = mock(MerkleMap.class);
		given(accounts.get(EntityNum.fromAccountId(stakedAccountID))).willReturn(new MerkleAccount());

		assertTrue(PureValidation.isValidStakedId(stakedAccountID, stakedNodeId, accounts, nodeInfo));

		final var deletedAccount = new MerkleAccount();
		deletedAccount.setDeleted(true);
		given(accounts.get(EntityNum.fromAccountId(stakedAccountID))).willReturn(deletedAccount);
		assertFalse(PureValidation.isValidStakedId(stakedAccountID, stakedNodeId, accounts, nodeInfo));

		final var smartContract = new MerkleAccount();
		smartContract.setSmartContract(true);
		given(accounts.get(EntityNum.fromAccountId(stakedAccountID))).willReturn(smartContract);
		assertFalse(PureValidation.isValidStakedId(stakedAccountID, stakedNodeId, accounts, nodeInfo));
	}

	@Test
	void validatesStakedNodeId() {
		final var stakedAccountID = AccountID.getDefaultInstance();
		final var stakedNodeId = 2;

		final MerkleMap<EntityNum, MerkleAccount> accounts = mock(MerkleMap.class);
		final NodeInfo nodeInfo = mock(NodeInfo.class);

		given(nodeInfo.accountOf(stakedNodeId)).willReturn(asAccount("0.0.2"));
		assertTrue(PureValidation.isValidStakedId(stakedAccountID, stakedNodeId, accounts, nodeInfo));

		given(nodeInfo.accountOf(stakedNodeId)).willThrow(IllegalArgumentException.class);
		assertFalse(PureValidation.isValidStakedId(stakedAccountID, stakedNodeId, accounts, nodeInfo));
	}
}
