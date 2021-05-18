package com.hedera.services.state.merkle;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.FCMKey;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Objects;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;

/**
 * Represents the ID of {@link MerkleUniqueToken}
 */
public class MerkleUniqueTokenId extends AbstractMerkleLeaf implements FCMKey {

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x52dd6afda193e8bcL;

	static DomainSerdes serdes = new DomainSerdes();

	private EntityId tokenId = MISSING_ENTITY_ID;
	private int serialNumber;

	public MerkleUniqueTokenId() {
		/* No-op. */
	}

	/**
	 *
	 * @param tokenId The underlying token id.
	 * @param serialNumber Represents the serial num of the token.
	 */
	public MerkleUniqueTokenId(
			EntityId tokenId,
			int serialNumber
	) {
		this.tokenId = tokenId;
		this.serialNumber = serialNumber;
	}

	/* --- Object --- */

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleUniqueTokenId.class != o.getClass()) {
			return false;
		}

		var that = (MerkleUniqueTokenId) o;

		return Objects.equals(tokenId, that.tokenId) &&
				Objects.equals(this.serialNumber, that.serialNumber);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				tokenId,
				serialNumber);
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleUniqueTokenId.class)
				.add("tokenId", tokenId)
				.add("serialNumber", serialNumber)
				.toString();
	}

	public EntityId tokenId() {
		return tokenId;
	}

	public int serialNumber() {
		return serialNumber;
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
	public void deserialize(SerializableDataInputStream in, int i) throws IOException {
		tokenId = in.readSerializable();
		serialNumber = in.readInt();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(tokenId, true);
		out.writeInt(serialNumber);
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleUniqueTokenId copy() {
		return new MerkleUniqueTokenId(tokenId, serialNumber);
	}

}
