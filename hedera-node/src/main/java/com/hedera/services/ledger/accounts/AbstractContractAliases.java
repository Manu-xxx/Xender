package com.hedera.services.ledger.accounts;

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

import com.google.common.primitives.Longs;
import org.hyperledger.besu.datatypes.Address;

import java.util.Arrays;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

public abstract class AbstractContractAliases implements ContractAliases {
	/* A placeholder to store the 12-byte prefix (4-byte shard and 8-byte realm) that marks an EVM
	 * address as a "mirror" address that follows immediately from a <shard>.<realm>.<num> id. */
	private static byte[] mirrorPrefix = null;

	protected boolean isMirror(final Address address) {
		return isMirror(address.toArrayUnsafe());
	}

	protected boolean isMirror(final byte[] address) {
		if (mirrorPrefix == null) {
			mirrorPrefix = new byte[12];
			System.arraycopy(Longs.toByteArray(STATIC_PROPERTIES.getShard()), 4, mirrorPrefix, 0, 4);
			System.arraycopy(Longs.toByteArray(STATIC_PROPERTIES.getRealm()), 0, mirrorPrefix, 4, 8);
		}
		return Arrays.equals(mirrorPrefix, 0, 12, address, 0, 12);
	}
}
