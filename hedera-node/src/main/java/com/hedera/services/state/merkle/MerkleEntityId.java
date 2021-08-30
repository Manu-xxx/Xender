package com.hedera.services.state.merkle;

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
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TopicID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.virtualmap.VirtualLongKey;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MerkleEntityId extends AbstractMerkleLeaf implements VirtualLongKey {
	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xd5dd2ebaa0bde03L;

	private long shard;
	private long realm;
	private long num;

	public MerkleEntityId() {
	}

	public MerkleEntityId(long shard, long realm, long num) {
		this.shard = shard;
		this.realm = realm;
		this.num = num;
	}

	public static MerkleEntityId fromAccountId(AccountID grpc) {
		return new MerkleEntityId(grpc.getShardNum(), grpc.getRealmNum(), grpc.getAccountNum());
	}

	public static MerkleEntityId fromTokenId(TokenID grpc) {
		return new MerkleEntityId(grpc.getShardNum(), grpc.getRealmNum(), grpc.getTokenNum());
	}

	public static MerkleEntityId fromTopicId(TopicID grpc) {
		return new MerkleEntityId(grpc.getShardNum(), grpc.getRealmNum(), grpc.getTopicNum());
	}

	public static MerkleEntityId fromContractId(ContractID grpc) {
		return new MerkleEntityId(grpc.getShardNum(), grpc.getRealmNum(), grpc.getContractNum());
	}

	public static MerkleEntityId fromScheduleId(ScheduleID grpc) {
		return new MerkleEntityId(grpc.getShardNum(), grpc.getRealmNum(), grpc.getScheduleNum());
	}

	/* --- MerkleLeaf --- */
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
		shard = in.readLong();
		realm = in.readLong();
		num = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(shard);
		out.writeLong(realm);
		out.writeLong(num);
	}

	/* --- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleEntityId.class != o.getClass()) {
			return false;
		}

		var that = (MerkleEntityId) o;
		return this.shard == that.shard && this.realm == that.realm && this.num == that.num;
	}

	@Override
	public int hashCode() {
		/* Until realms are implemented, only the entity number distinguishes this key from any other. */
		return (int) MiscUtils.perm64(num);
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleEntityId copy() {
		setImmutable(true);
		return new MerkleEntityId(shard, realm, num);
	}

	/* --- Bean --- */
	public long getShard() {
		return shard;
	}

	public void setShard(final long shard) {
		throwIfImmutable("Cannot change this entity's shard if it's immutable.");
		this.shard = shard;
	}

	public long getRealm() {
		return realm;
	}

	public void setRealm(final long realm) {
		throwIfImmutable("Cannot change this entity's realm if it's immutable.");
		this.realm = realm;
	}

	public long getNum() {
		return num;
	}

	public void setNum(final long num) {
		throwIfImmutable("Cannot change this entity's number if it's immutable.");
		this.num = num;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("shard", shard)
				.add("realm", realm)
				.add("entity", num)
				.toString();
	}

	public String toAbbrevString() {
		return String.format("%d.%d.%d", shard, realm, num);
	}

	public AccountID toAccountId() {
		return AccountID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setAccountNum(num)
				.build();
	}

	public TokenID toTokenId() {
		return TokenID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setTokenNum(num)
				.build();
	}

	public ScheduleID toScheduleId() {
		return ScheduleID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setScheduleNum(num)
				.build();
	}

	/* --- VirtualKey --- */

	@Override
	public void serialize(ByteBuffer buffer) throws IOException {
		// Skip the realm + shard for now as they are not used.
		buffer.putLong(num);
	}

	@Override
	public void deserialize(ByteBuffer buffer, int version) throws IOException {
		// Only the entity number is used for now.
		num = buffer.getLong();
	}

	@Override
	public boolean equals(ByteBuffer buffer, int version) throws IOException {
		return buffer.getLong() == num;
	}

	@Override
	public long getKeyAsLong() {
		return num;
	}
}
