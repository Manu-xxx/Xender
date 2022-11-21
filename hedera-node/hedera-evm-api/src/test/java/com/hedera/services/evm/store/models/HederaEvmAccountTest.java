/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.evm.store.models;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HederaEvmAccountTest {

    private static final byte[] mockCreate2Addr = unhex("aaaaaaaaaaaaaaaaaaaaaaaa9abcdefabcdefbbb");
    private final Address accountAddress =
            Address.wrap(Bytes.fromHexString("0x00000000000000000000000000000000000005cc"));

    private HederaEvmAccount subject;

    @BeforeEach
    void setUp() {
        subject = new HederaEvmAccount(accountAddress);
    }

    @Test
    void canonicalAddressIs20ByteAliasIfPresent() {
        subject.setAlias(ByteString.copyFrom(mockCreate2Addr));
        assertEquals(Address.wrap(Bytes.wrap(mockCreate2Addr)), subject.canonicalAddress());
    }

    @Test
    void canonicalAddressIsEVMAddressIfCorrectAlias() {
        // default truffle address #0
        subject.setAlias(
                ByteString.copyFrom(
                        Hex.decode(
                                "3a2103af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(
                Address.wrap(Bytes.fromHexString("627306090abaB3A6e1400e9345bC60c78a8BEf57")),
                subject.canonicalAddress());
    }

    @Test
    void invalidCanonicalAddresses() {
        // bogus alias
        subject.setAlias(ByteString.copyFromUtf8("This alias is invalid"));
        assertEquals(accountAddress, subject.canonicalAddress());

        // incorrect starting bytes for ECDSA
        subject.setAlias(
                ByteString.copyFrom(
                        Hex.decode(
                                "ffff03af80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(accountAddress, subject.canonicalAddress());

        // incorrect ECDSA key
        subject.setAlias(
                ByteString.copyFrom(
                        Hex.decode(
                                "3a21ffaf80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d")));
        assertEquals(accountAddress, subject.canonicalAddress());
    }

    @Test
    void toStringAsExpected() {
        final var desired = "HederaEvmAccount{alias=}";

        // expect:
        assertEquals(desired, subject.toString());
    }

    @Test
    void accountEqualsCheck() {
        // setup:
        var account = new HederaEvmAccount(accountAddress);
        account.setAlias(ByteString.copyFrom(mockCreate2Addr));

        subject.setAlias(ByteString.copyFrom(mockCreate2Addr));

        // when:
        var actualResult = subject.equals(account);

        assertTrue(actualResult);
    }

    @Test
    void accountHashCodeCheck() {
        // setup:
        subject.setAlias(ByteString.copyFrom(mockCreate2Addr));
        var otherSubject = new HederaEvmAccount(accountAddress);
        otherSubject.setAlias(ByteString.copyFrom(mockCreate2Addr));

        // when:
        var actualResult = subject.hashCode();

        // expect:
        assertEquals(otherSubject.hashCode(), actualResult);
    }
}
