package com.hedera.services.store.contracts.precompile;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

public record MintWrapper(BigInteger amount, TokenID tokenType, List<ByteString> metadata) {
	private static final BigInteger NONFUNGIBLE_MINT_AMOUNT = BigInteger.valueOf(-1);
	private static final List<ByteString> FUNGIBLE_MINT_METADATA = Collections.emptyList();

	public static MintWrapper forNonFungible(final TokenID tokenType, final List<ByteString> metadata) {
		return new MintWrapper(NONFUNGIBLE_MINT_AMOUNT, tokenType, metadata);
	}

	public static MintWrapper forFungible(final TokenID tokenType, final BigInteger amount) {
		return new MintWrapper(amount, tokenType, FUNGIBLE_MINT_METADATA);
	}

	public TokenType type() {
		return (amount.compareTo(NONFUNGIBLE_MINT_AMOUNT) == 0) ? NON_FUNGIBLE_UNIQUE : FUNGIBLE_COMMON;
	}
}
