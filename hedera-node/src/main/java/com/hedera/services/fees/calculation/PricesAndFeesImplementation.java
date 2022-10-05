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
package com.hedera.services.fees.calculation;

import static com.hederahashgraph.fee.FeeBuilder.getTinybarsFromTinyCents;

import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.PricesAndFeesProvider;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.function.ToLongFunction;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PricesAndFeesImplementation implements PricesAndFeesProvider {

    private final HbarCentExchange exchange;
    private final FeeCalculator feeCalculator;
    private final UsagePricesProvider usagePrices;

    @Inject
    public PricesAndFeesImplementation(
            final HbarCentExchange exchange,
            final FeeCalculator feeCalculator,
            final UsagePricesProvider usagePrices) {
        this.exchange = exchange;
        this.usagePrices = usagePrices;
        this.feeCalculator = feeCalculator;
    }

    @Override
    public FeeData defaultPricesGiven(HederaFunctionality function, Timestamp at) {
        return usagePrices.defaultPricesGiven(function, at);
    }

    @Override
    public ExchangeRate rate(Timestamp at) {
        return exchange.rate(at);
    }

    @Override
    public long estimatedGasPriceInTinybars(HederaFunctionality function, Timestamp at) {
        return feeCalculator.estimatedGasPriceInTinybars(function, at);
    }

    public long currentGasPriceInTinycents(
            final Timestamp now, final HederaFunctionality function) {
        return currentFeeInTinycents(now, function, FeeComponents::getGas);
    }

    private long currentFeeInTinycents(
            final Timestamp now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        final var prices = usagePrices.defaultPricesGiven(function, now);

        /* Fee schedule prices are set in thousandths of a tinycent */
        return resourcePriceFn.applyAsLong(prices.getServicedata()) / 1000;
    }

    public long currentGasPrice(final Timestamp now, final HederaFunctionality function) {
        return currentPrice(now, function, FeeComponents::getGas);
    }

    public long currentPrice(
            final Timestamp now,
            final HederaFunctionality function,
            final ToLongFunction<FeeComponents> resourcePriceFn) {
        long feeInTinyCents = currentFeeInTinycents(now, function, resourcePriceFn);
        long feeInTinyBars = getTinybarsFromTinyCents(exchange.rate(now), feeInTinyCents);
        final var unscaledPrice = Math.max(1L, feeInTinyBars);

        final var maxMultiplier = Long.MAX_VALUE / feeInTinyBars;
        final var curMultiplier = 70L;
        return curMultiplier;
    }
}
