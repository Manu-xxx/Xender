package com.hedera.services.usage.token.meta;

/*-
 * ‌
 * Hedera Services API Fees
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
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class TokenAssociateMeta extends TokenUntypedMetaBase {
	private int numOfTokens;
	private long relativeLifeTime;
	public TokenAssociateMeta(final int bpt,
			final int numOfTokens) {
		super(bpt);
		this.numOfTokens = numOfTokens;
		this.relativeLifeTime = 0;
	}

	public void setRelativeLifeTime(final long relativeLifeTime) {
		this.relativeLifeTime = relativeLifeTime;
	}

	public long getRelativeLifeTime() { return relativeLifeTime;}

	public int getNumOfTokens() { return numOfTokens; }

	@Override
	public boolean equals(Object obj) {
		return EqualsBuilder.reflectionEquals(this, obj);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper()
				.add("numOfTokens", numOfTokens)
				.add("relativeLifeTime", relativeLifeTime);
	}
}
