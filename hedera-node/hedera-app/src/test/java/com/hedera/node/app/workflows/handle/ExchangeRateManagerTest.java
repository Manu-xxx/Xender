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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.transaction.ExchangeRateSet.PROTOBUF;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ExchangeRateManagerTest {
    static final int hbarEquiv = 30_000;
    static final int centEquiv = 120_000;
    TimestampSeconds expirationTime =
            TimestampSeconds.newBuilder().setSeconds(150_000L).build();
    ExchangeRate.Builder someRate = ExchangeRate.newBuilder()
            .setHbarEquiv(hbarEquiv)
            .setCentEquiv(centEquiv)
            .setExpirationTime(expirationTime);
    ExchangeRateSet validRatesObj = ExchangeRateSet.newBuilder()
            .setCurrentRate(someRate)
            .setNextRate(someRate)
            .build();

    Bytes validRateBytes = Bytes.wrap(validRatesObj.toByteArray());
    ExchangeRateManager subject = new ExchangeRateManager();

    @Test
    void hasExpectedFields() throws IOException {
        // when
        subject.createUpdateExchangeRates(validRateBytes);
        // expect
        assertEquals(hbarEquiv, subject.getCurrHbarEquiv());
        assertEquals(hbarEquiv, subject.getNextHbarEquiv());
        assertEquals(centEquiv, subject.getCurrCentEquiv());
        assertEquals(centEquiv, subject.getNextCentEquiv());
        assertEquals(expirationTime.getSeconds(), subject.getCurrExpiry());
        assertEquals(expirationTime.getSeconds(), subject.getNextExpiry());
        assertEquals(PROTOBUF.parse(validRateBytes.toReadableSequentialData()), subject.getExchangeRateSet());
    }

    @Test
    void onlyCurrentRates() throws IOException {
        // given
        final var onlyCurrentRates =
                ExchangeRateSet.newBuilder().setCurrentRate(someRate).build();
        subject = new ExchangeRateManager();

        // when
        subject.createUpdateExchangeRates(Bytes.wrap(onlyCurrentRates.toByteArray()));

        // expect
        assertEquals(hbarEquiv, subject.getCurrHbarEquiv());
        assertEquals(0, subject.getNextHbarEquiv());
        assertEquals(centEquiv, subject.getCurrCentEquiv());
        assertEquals(0, subject.getNextCentEquiv());
        assertEquals(expirationTime.getSeconds(), subject.getCurrExpiry());
        assertEquals(0, subject.getNextExpiry());
    }

    @Test
    void onlyNextRates() throws IOException {
        // given
        final var onlyNextRates =
                ExchangeRateSet.newBuilder().setNextRate(someRate).build();
        subject = new ExchangeRateManager();

        // when
        subject.createUpdateExchangeRates(Bytes.wrap(onlyNextRates.toByteArray()));

        // expect
        assertEquals(0, subject.getCurrHbarEquiv());
        assertEquals(hbarEquiv, subject.getNextHbarEquiv());
        assertEquals(0, subject.getCurrCentEquiv());
        assertEquals(centEquiv, subject.getNextCentEquiv());
        assertEquals(0, subject.getCurrExpiry());
        assertEquals(expirationTime.getSeconds(), subject.getNextExpiry());
    }
}
