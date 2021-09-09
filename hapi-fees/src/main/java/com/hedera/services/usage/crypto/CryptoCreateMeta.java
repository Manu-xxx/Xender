package com.hedera.services.usage.crypto;

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

import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import static com.hedera.services.usage.TxnUsage.keySizeIfPresent;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;

public class CryptoCreateMeta {
	private final long baseSize;
	private final long lifeTime;
	private final int maxAutomaticAssociations;

	public CryptoCreateMeta(TransactionBody transactionBody) {
		var op = transactionBody.getCryptoCreateAccount();
		baseSize = getCryptoCreateTxnBaseSize(op);
		lifeTime = op.getAutoRenewPeriod().getSeconds();
		maxAutomaticAssociations = op.getMaxAutomaticTokenAssociations();
	}

	private long getCryptoCreateTxnBaseSize(CryptoCreateTransactionBody op) {
		long variableBytes = op.getMemoBytes().size();
		variableBytes += keySizeIfPresent(op, CryptoCreateTransactionBody::hasKey, CryptoCreateTransactionBody::getKey);
		if (op.hasProxyAccountID()) {
			variableBytes += BASIC_ENTITY_ID_SIZE;
		}
		return variableBytes;
	}

	public long getBaseSize() {
		return baseSize;
	}

	public long getLifeTime() {
		return lifeTime;
	}

	public int getMaxAutomaticAssociations() {
		return maxAutomaticAssociations;
	}
}
