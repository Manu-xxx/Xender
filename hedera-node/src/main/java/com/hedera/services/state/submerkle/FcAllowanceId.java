/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;

public class FcAllowanceId implements SelfSerializable {
	static final int RELEASE_023X_VERSION = 1;
	static final int CURRENT_VERSION = RELEASE_023X_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xf55baa544950f139L;

	private EntityNum tokenNum;
	private EntityNum spenderNum;

	public FcAllowanceId() {
		/* RuntimeConstructable */
	}

	FcAllowanceId(final EntityNum tokenNum, final EntityNum spenderNum) {
		this.tokenNum = tokenNum;
		this.spenderNum = spenderNum;
	}

	@Override
	public void deserialize(final SerializableDataInputStream din, final int i) throws IOException {
		tokenNum = EntityNum.fromLong(din.readLong());
		spenderNum = EntityNum.fromLong(din.readLong());
	}

	@Override
	public void serialize(final SerializableDataOutputStream dos) throws IOException {
		dos.writeLong(tokenNum.intValue());
		dos.writeLong(spenderNum.intValue());
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || !obj.getClass().equals(FcAllowanceId.class)) {
			return false;
		}

		final var that = (FcAllowanceId) obj;
		return new EqualsBuilder()
				.append(tokenNum, that.tokenNum)
				.append(spenderNum, that.spenderNum)
				.isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder()
				.append(tokenNum)
				.append(spenderNum)
				.toHashCode();
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.omitNullValues()
				.add("tokenNum", tokenNum.longValue())
				.add("spenderNum", spenderNum.longValue())
				.toString();
	}

	public EntityNum getTokenNum() {
		return tokenNum;
	}

	public EntityNum getSpenderNum() {
		return spenderNum;
	}

	public static FcAllowanceId from(final EntityNum tokenNum, final EntityNum spenderNum) {
		return new FcAllowanceId(tokenNum, spenderNum);
	}
}
