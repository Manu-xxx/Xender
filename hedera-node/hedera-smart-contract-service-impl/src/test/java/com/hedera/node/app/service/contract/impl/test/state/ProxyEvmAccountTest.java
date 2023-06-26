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

package com.hedera.node.app.service.contract.impl.test.state;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuHash;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.state.EvmFrameState;
import com.hedera.node.app.service.contract.impl.state.ProxyEvmAccount;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProxyEvmAccountTest {
    private static final long ACCOUNT_NUM = 0x9abcdefabcdefbbbL;
    private static final Address EVM_ADDRESS = Address.fromHexString("abcabcabcabcabcabeeeeeee9abcdefabcdefbbb");
    private static final Bytes SOME_PRETEND_CODE = Bytes.wrap("<NOT-REALLY-CODE>");
    private static final Bytes SOME_PRETEND_CODE_HASH = Bytes.wrap("<NOT-REALLY-BYTECODE-HASH-12345>");
    private static final UInt256 SOME_KEY =
            UInt256.fromHexString("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
    private static final UInt256 SOME_VALUE =
            UInt256.fromHexString("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");

    @Mock
    private EvmFrameState hederaState;

    private ProxyEvmAccount subject;

    @BeforeEach
    void setUp() {
        subject = new ProxyEvmAccount(ACCOUNT_NUM, hederaState);
    }

    @Test
    void notTokenFacade() {
        assertFalse(subject.isTokenFacade());
    }

    @Test
    void accountHashNotSupported() {
        assertThrows(UnsupportedOperationException.class, subject::getAddressHash);
    }

    @Test
    void storageEntriesNotSupported() {
        assertThrows(UnsupportedOperationException.class, () -> subject.storageEntriesFrom(Bytes32.ZERO, 1));
    }

    @Test
    void returnsLongZeroAddressWithoutAnAlias() {
        given(hederaState.getAddress(ACCOUNT_NUM)).willReturn(EVM_ADDRESS);
        assertEquals(EVM_ADDRESS, subject.getAddress());
    }

    @Test
    void returnsNonce() {
        given(hederaState.getNonce(ACCOUNT_NUM)).willReturn(123L);
        assertEquals(123L, subject.getNonce());
    }

    @Test
    void returnsBalance() {
        final var value = Wei.of(123L);
        given(hederaState.getBalance(ACCOUNT_NUM)).willReturn(value);
        assertEquals(value, subject.getBalance());
    }

    @Test
    void returnsCode() {
        final var code = pbjToTuweniBytes(SOME_PRETEND_CODE);
        given(hederaState.getCode(ACCOUNT_NUM)).willReturn(code);
        assertEquals(code, subject.getCode());
    }

    @Test
    void returnsCodeHash() {
        final var hash = pbjToBesuHash(SOME_PRETEND_CODE_HASH);
        given(hederaState.getCodeHash(ACCOUNT_NUM)).willReturn(hash);
        assertEquals(hash, subject.getCodeHash());
    }

    @Test
    void getsStorageValue() {
        given(hederaState.getStorageValue(ACCOUNT_NUM, SOME_KEY)).willReturn(SOME_VALUE);
        assertEquals(SOME_VALUE, subject.getStorageValue(SOME_KEY));
    }

    @Test
    void getsOriginalStorageValue() {
        given(hederaState.getOriginalStorageValue(ACCOUNT_NUM, SOME_KEY)).willReturn(SOME_VALUE);
        assertEquals(SOME_VALUE, subject.getOriginalStorageValue(SOME_KEY));
    }

    @Test
    void delegatesSettingNonce() {
        subject.setNonce(123);

        verify(hederaState).setNonce(ACCOUNT_NUM, 123);
    }

    @Test
    void delegatesSettingCode() {
        final var code = ConversionUtils.pbjToTuweniBytes(SOME_PRETEND_CODE);

        subject.setCode(code);

        verify(hederaState).setCode(ACCOUNT_NUM, code);
    }

    @Test
    void delegatesSettingStorage() {
        subject.setStorageValue(SOME_KEY, SOME_VALUE);

        verify(hederaState).setStorageValue(ACCOUNT_NUM, SOME_KEY, SOME_VALUE);
    }

    @Test
    void doesNotSupportDirectBalanceMutation() {
        assertThrows(UnsupportedOperationException.class, () -> subject.setBalance(Wei.of(123)));
    }

    @Test
    void delegatesCheckingContract() {
        given(hederaState.isContract(ACCOUNT_NUM)).willReturn(true);
        assertTrue(subject.isContract());
    }

    @Test
    void isItselfMutable() {
        assertSame(subject, subject.getMutable());
    }
}
