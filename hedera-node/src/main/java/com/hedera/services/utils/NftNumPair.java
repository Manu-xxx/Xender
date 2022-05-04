package com.hedera.services.utils;

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

import com.hedera.services.store.models.NftId;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.Objects;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

public record NftNumPair(long tokenNum, long serialNum) {
	public static final NftNumPair MISSING_NFT_NUM_PAIR = new NftNumPair(0, 0);

	public TokenID tokenId() {
		return TokenID.newBuilder()
				.setShardNum(STATIC_PROPERTIES.getShard())
				.setRealmNum(STATIC_PROPERTIES.getRealm())
				.setTokenNum(tokenNum)
				.build();
	}

	public NftId nftId() {
		return NftId.withDefaultShardRealm(tokenNum, serialNum);
	}

	public EntityNumPair asEntityNumPair() {
		return EntityNumPair.fromLongs(tokenNum, serialNum);
	}

	public static NftNumPair fromLongs(final long tokenNum, final long serialNum) {
		return new NftNumPair(tokenNum, serialNum);
	}

	@Override
	public String toString() {
		return String.format("%d.%d.%d.%d",
				STATIC_PROPERTIES.getShard(),
				STATIC_PROPERTIES.getRealm(),
				tokenNum,
				serialNum);
	}

	@Override
	public int hashCode() {
		return  Objects.hash(tokenNum, serialNum);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || NftNumPair.class != o.getClass()) {
			return false;
		}

		var that = (NftNumPair) o;

		return this.tokenNum == that.tokenNum &&
				this.serialNum == that.serialNum;
	}
}
