package com.hedera.services.context.properties;

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

import com.hedera.services.config.HederaNumbers;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;

public class StaticPropertiesHolder {
	/* This will not be accessed concurrently, */
	public static final StaticPropertiesHolder STATIC_PROPERTIES = new StaticPropertiesHolder();

	private long shard = 0;
	private long realm = 0;

	public void setNumbersFrom(final HederaNumbers hederaNum) {
		shard = hederaNum.shard();
		realm = hederaNum.realm();
	}

	public long getShard() {
		return shard;
	}

	public long getRealm() {
		return realm;
	}

	public JContractIDKey scopedContractKeyWith(final long num) {
		return new JContractIDKey(shard, realm, num);
	}

	public AccountID scopedAccountWith(final long num) {
		return AccountID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setAccountNum(num)
				.build();
	}

	public FileID scopedFileWith(final long num) {
		return FileID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setFileNum(num)
				.build();
	}

	public TokenID scopedTokenWith(final long num) {
		return TokenID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setTokenNum(num)
				.build();
	}

	public ScheduleID scopedScheduleWith(final long num) {
		return ScheduleID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setScheduleNum(num)
				.build();
	}

	public String scopedIdLiteralWith(final long num) {
		return shard + "." + realm + "." + num;
	}
}
