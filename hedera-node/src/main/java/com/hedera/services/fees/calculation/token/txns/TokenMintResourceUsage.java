package com.hedera.services.fees.calculation.token.txns;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.token.TokenMintUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;

import java.util.Optional;
import java.util.function.BiFunction;

public class TokenMintResourceUsage implements TxnResourceUsageEstimator {
	static BiFunction<TransactionBody, SigUsage, TokenMintUsage> factory = TokenMintUsage::newEstimate;

	@Override
	public boolean applicableTo(TransactionBody txn) {
		return txn.hasTokenMint();
	}

	@Override
	public FeeData usageGiven(TransactionBody txn, SigValueObj svo, StateView view) throws InvalidTxBodyException {
		Optional<TokenType> tokenType = view.tokenType(txn.getTokenMint().getToken());

		SubType subType;
		switch (tokenType.get()) {
			case FUNGIBLE_COMMON:
				subType = SubType.TOKEN_FUNGIBLE_COMMON;
				break;
			case NON_FUNGIBLE_UNIQUE:
				subType = SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
				break;
			default:
				subType = SubType.DEFAULT;
		}

		var sigUsage = new SigUsage(svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());
		var estimate = factory.apply(txn, sigUsage).givenSubType(subType);

		return estimate.get();
	}
}
