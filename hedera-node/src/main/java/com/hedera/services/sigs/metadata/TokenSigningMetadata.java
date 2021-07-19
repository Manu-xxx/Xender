package com.hedera.services.sigs.metadata;

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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleToken;

import java.util.Optional;

/**
 * Represents metadata about the signing attributes of a Hedera token.
 *
 * @author Michael Tinker
 */
public class TokenSigningMetadata {
	private final Optional<JKey> adminKey;
	private final Optional<JKey> kycKey;
	private final Optional<JKey> wipeKey;
	private final Optional<JKey> freezeKey;
	private final Optional<JKey> supplyKey;
	private final Optional<JKey> feeScheduleKey;

	private TokenSigningMetadata(
			Optional<JKey> adminKey,
			Optional<JKey> kycKey,
			Optional<JKey> wipeKey,
			Optional<JKey> freezeKey,
			Optional<JKey> supplyKey,
			Optional<JKey> feeScheduleKey
	) {
		this.adminKey = adminKey;
		this.kycKey = kycKey;
		this.wipeKey = wipeKey;
		this.freezeKey = freezeKey;
		this.supplyKey = supplyKey;
		this.feeScheduleKey = feeScheduleKey;
	}

	public static TokenSigningMetadata from(MerkleToken token) {
		return new TokenSigningMetadata(
				token.adminKey(),
				token.kycKey(),
				token.wipeKey(),
				token.freezeKey(),
				token.supplyKey(),
				token.feeScheduleKey());
	}

	public Optional<JKey> adminKey() {
		return adminKey;
	}

	public Optional<JKey> optionalFreezeKey() {
		return freezeKey;
	}

	public Optional<JKey> optionalKycKey() {
		return kycKey;
	}

	public Optional<JKey> optionalWipeKey() {
		return wipeKey;
	}

	public Optional<JKey> optionalSupplyKey() {
		return supplyKey;
	}

	public Optional<JKey> optionalFeeScheduleKey() {
		return feeScheduleKey;
	}
}
