/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.fees;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Default implementation of {@link ExchangeRateInfo}.
 */
public record ExchangeRateInfoImpl(@NonNull ExchangeRateSet exchangeRateSet) implements ExchangeRateInfo {

    public ExchangeRateInfoImpl {
        requireNonNull(exchangeRateSet, "exchangeRateSet must not be null");
        requireNonNull(exchangeRateSet.currentRate(), "exchangeRateSet.currentRate() must not be null");
        requireNonNull(
                exchangeRateSet.currentRateOrThrow().expirationTime(),
                "exchangeRateSet.currentRate().expirationTime() must not be null");
        requireNonNull(exchangeRateSet.nextRate(), "exchangeRateSet.nextRate() must not be null");
    }

    @NonNull
    @Override
    public ExchangeRateSet exchangeRates() {
        return exchangeRateSet;
    }

    @NonNull
    @Override
    public ExchangeRate activeRate(@NonNull Instant consensusTime) {
        return consensusTime.getEpochSecond()
                        > exchangeRateSet
                                .currentRateOrThrow()
                                .expirationTimeOrThrow()
                                .seconds()
                ? exchangeRateSet.nextRateOrThrow()
                : exchangeRateSet.currentRateOrThrow();
    }
}
