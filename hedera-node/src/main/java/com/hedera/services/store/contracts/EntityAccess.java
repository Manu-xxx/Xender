package com.hedera.services.store.contracts;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

import static com.hedera.services.store.contracts.WorldLedgers.NULL_WORLD_LEDGERS;

public interface EntityAccess {
	/**
	 * Provides a {@link WorldLedgers} whose {@link com.hedera.services.ledger.TransactionalLedger} instances commit
	 * directly to the Hedera world state. Only makes sense for a mutable {@link EntityAccess} implementation.
	 *
	 * @return the world state ledgers if this implementation is mutable (null ledgers otherwise)
	 */
	default WorldLedgers worldLedgers() {
		return NULL_WORLD_LEDGERS;
	}

	/* --- Account access --- */
	void spawn(AccountID id, long balance, HederaAccountCustomizer customizer);

	void customize(AccountID id, HederaAccountCustomizer customizer);

	void adjustBalance(AccountID id, long adjustment);

	long getAutoRenew(AccountID id);

	long getBalance(AccountID id);

	long getExpiry(AccountID id);

	JKey getKey(AccountID id);

	String getMemo(AccountID id);

	EntityId getProxy(AccountID id);

	boolean isDeleted(AccountID id);

	boolean isDetached(AccountID id);

	boolean isExtant(AccountID id);

	/* --- Storage access --- */
	void putStorage(AccountID id, UInt256 key, UInt256 value);

	UInt256 getStorage(AccountID id, UInt256 key);

	/* --- Bytecode access --- */
	void storeCode(AccountID id, Bytes code);

	Bytes fetchCode(AccountID id);
}
