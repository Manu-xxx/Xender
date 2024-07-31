/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.platform.event.EventPayload.PayloadOneOfType;
import com.hedera.hapi.platform.event.StateSignaturePayload;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.system.transaction.PayloadWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PayloadUtilsTest {

    @ParameterizedTest
    @MethodSource("buildArgumentsSwirldTransactions")
    void testSizeComparisonsSwirldTransactions(
            final OneOf<PayloadOneOfType> payload, final PayloadWrapper swirldTransaction) {
        assertEquals(PayloadUtils.getLegacyPayloadSize(payload), swirldTransaction.getSize());
        assertFalse(PayloadUtils.isSystemPayload(payload));
    }

    protected static Stream<Arguments> buildArgumentsSwirldTransactions() {
        final List<Arguments> arguments = new ArrayList<>();
        final Randotron randotron = Randotron.create();

        IntStream.range(0, 100).forEach(i -> {
            final Bytes payload = randotron.nextHashBytes();

            OneOf<PayloadOneOfType> oneOfPayload = new OneOf<>(PayloadOneOfType.APPLICATION_PAYLOAD, payload);
            arguments.add(Arguments.of(oneOfPayload, new PayloadWrapper(oneOfPayload)));
        });

        return arguments.stream();
    }

    @ParameterizedTest
    @MethodSource("buildArgumentsStateSignatureTransaction")
    void testSizeComparisonsStateSignatureTransaction(
            final OneOf<PayloadOneOfType> payload, final PayloadWrapper stateSignatureTransaction) {
        assertEquals(PayloadUtils.getLegacyPayloadSize(payload), stateSignatureTransaction.getSize());
        assertTrue(PayloadUtils.isSystemPayload(payload));
    }

    protected static Stream<Arguments> buildArgumentsStateSignatureTransaction() {
        final List<Arguments> arguments = new ArrayList<>();
        final Randotron randotron = Randotron.create();

        IntStream.range(0, 100).forEach(i -> {
            final StateSignaturePayload payload = StateSignaturePayload.newBuilder()
                    .hash(randotron.nextHashBytes())
                    .signature(randotron.nextSignatureBytes())
                    .build();
            final OneOf<PayloadOneOfType> oneOfPayload = new OneOf<>(PayloadOneOfType.STATE_SIGNATURE_PAYLOAD, payload);
            arguments.add(Arguments.of(oneOfPayload, new PayloadWrapper(oneOfPayload)));
        });

        return arguments.stream();
    }
}
