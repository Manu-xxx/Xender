package com.hedera.services.store.contracts.precompile.codec;

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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.List;

public record Dissociation(AccountID accountId, List<TokenID> tokenIds) {
	public static Dissociation singleDissociation(final AccountID accountId, final TokenID tokenId) {
		return new Dissociation(accountId, List.of(tokenId));
	}

	public static Dissociation multiDissociation(final AccountID accountId, final List<TokenID> tokenIds) {
		return new Dissociation(accountId, tokenIds);
	}
}
