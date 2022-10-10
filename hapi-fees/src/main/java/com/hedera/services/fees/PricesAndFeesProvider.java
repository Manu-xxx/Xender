/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.fees;

// import com.hedera.services.precompile.Precompile;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;

public interface PricesAndFeesProvider {
    FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at);

    ExchangeRate rate(Timestamp at);

    long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at);

    //    long currentMultiplier(TxnAccessor accessor);

    //    FeeObject estimatePayment(Query query, FeeData usagePrices, StateView view, Timestamp at,
    // ResponseType type);

    //    long gasFeeInTinybars(final Instant consensusTime, final Precompile precompile);
}
