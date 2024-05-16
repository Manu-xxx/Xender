/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import static com.swirlds.platform.event.DetGenerateUtils.generateRandomByteArray;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.common.test.fixtures.io.InputOutputStream;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.ConsensusData;
import com.swirlds.platform.system.events.DetailedConsensusEvent;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class DetailedConsensusEventTest {
    @BeforeAll
    public static void setUp() throws ConstructableRegistryException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructables("com.swirlds.platform");
        StaticSoftwareVersion.setSoftwareVersion(new BasicSoftwareVersion(1));
    }

    @AfterAll
    static void afterAll() {
        StaticSoftwareVersion.reset();
    }

    @Test
    public void serializeAndDeserializeConsensusEvent() throws IOException {
        DetailedConsensusEvent consensusEvent = generateConsensusEvent();
        try (final InputOutputStream io = new InputOutputStream()) {
            io.getOutput().writeSerializable(consensusEvent, true);
            io.startReading();

            final DetailedConsensusEvent deserialized = io.getInput().readSerializable();
            assertEquals(consensusEvent, deserialized);
        }
    }

    @Test
    public void EventImplGetHashTest() {
        DetailedConsensusEvent consensusEvent = generateConsensusEvent();
        EventImpl event = new EventImpl(consensusEvent);
        CryptographyHolder.get().digestSync(consensusEvent);
        Hash expectedHash = consensusEvent.getHash();
        CryptographyHolder.get().digestSync(event);
        assertEquals(expectedHash, event.getHash());
    }

    private DetailedConsensusEvent generateConsensusEvent() {
        Randotron random = Randotron.create(68651684861L);
        BaseEventHashedData hashedData = DetGenerateUtils.generateBaseEventHashedData(random);
        ConsensusData consensusData = DetGenerateUtils.generateConsensusEventData(random);
        return new DetailedConsensusEvent(
                new GossipEvent(
                hashedData,
                Bytes.wrap(generateRandomByteArray(random, SignatureType.RSA.signatureLength()))),
                consensusData);
    }
}
