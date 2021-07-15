package com.hedera.services.grpc.marshalling;

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

import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import static com.hedera.services.grpc.marshalling.AdjustmentUtils.adjust;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class HtsFeeAssessor {
	public ResponseCodeEnum assess(
			Id account,
			FcCustomFee htsFee,
			BalanceChangeManager changeManager
	) {
		final var collector = htsFee.getFeeCollectorAsId();
		final var fixedSpec = htsFee.getFixedFeeSpec();
		final var denominatingToken = fixedSpec.getTokenDenomination().asId();
		adjust(account, collector, denominatingToken, fixedSpec.getUnitsToCollect(), changeManager);
		return OK;
	}
}
