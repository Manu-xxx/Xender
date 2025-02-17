/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ANOTHER_ED25519_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.AN_ED25519_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_SECP256K1_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.B_SECP256K1_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.YET_ANOTHER_ED25519_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy.UseTopLevelSigs;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.spi.key.KeyVerifier;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActiveContractVerificationStrategyTest {
    private static final long ACTIVE_NUMBER = 1234L;
    private static final long SOME_OTHER_NUMBER = 2345L;
    private static final Bytes ACTIVE_ADDRESS = Bytes.fromHex("1234");
    private static final Bytes OTHER_ADDRESS = Bytes.fromHex("abcd");

    private static final Key ACTIVE_ID_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().contractNum(ACTIVE_NUMBER))
            .build();
    private static final Key DELEGATABLE_ACTIVE_ID_KEY = Key.newBuilder()
            .delegatableContractId(ContractID.newBuilder().contractNum(ACTIVE_NUMBER))
            .build();
    private static final Key INACTIVE_ID_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().contractNum(SOME_OTHER_NUMBER))
            .build();
    private static final Key DELEGATABLE_INACTIVE_ID_KEY = Key.newBuilder()
            .delegatableContractId(ContractID.newBuilder().contractNum(SOME_OTHER_NUMBER))
            .build();
    private static final Key ACTIVE_ADDRESS_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().evmAddress(ACTIVE_ADDRESS))
            .build();
    private static final Key DELEGATABLE_ACTIVE_ADDRESS_KEY = Key.newBuilder()
            .delegatableContractId(ContractID.newBuilder().evmAddress(ACTIVE_ADDRESS))
            .build();
    private static final Key INACTIVE_ADDRESS_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder().evmAddress(OTHER_ADDRESS))
            .build();
    private static final Key DELEGATABLE_INACTIVE_ADDRESS_KEY = Key.newBuilder()
            .delegatableContractId(ContractID.newBuilder().evmAddress(OTHER_ADDRESS))
            .build();
    private static final Key CRYPTO_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("1234567812345678123456781234567812345678123456781234567812345678"))
            .build();
    private static final ContractID contractID =
            ContractID.newBuilder().contractNum(ACTIVE_NUMBER).build();

    @Mock
    private HandleContext context;

    @Test
    void validatesKeysAsExpectedWhenDelegatePermissionNotRequiredAndUsingTopLevelSigs() {
        final var subject =
                new ActiveContractVerificationStrategy(contractID, ACTIVE_ADDRESS, false, UseTopLevelSigs.YES);

        assertEquals(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(ACTIVE_ID_KEY));
        assertEquals(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(ACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(INACTIVE_ID_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(INACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(DELEGATABLE_ACTIVE_ID_KEY));
        assertEquals(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(DELEGATABLE_ACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(DELEGATABLE_INACTIVE_ID_KEY));
        assertEquals(
                VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(DELEGATABLE_INACTIVE_ADDRESS_KEY));
        assertEquals(
                VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION,
                subject.decideForPrimitive(CRYPTO_KEY));
    }

    @Test
    void validatesKeysAsExpectedWhenDelegatePermissionRequiredAndUsingTopLevelSigs() {
        final var subject =
                new ActiveContractVerificationStrategy(contractID, ACTIVE_ADDRESS, true, UseTopLevelSigs.YES);

        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(ACTIVE_ID_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(ACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(INACTIVE_ID_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(INACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(DELEGATABLE_ACTIVE_ID_KEY));
        assertEquals(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(DELEGATABLE_ACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(DELEGATABLE_INACTIVE_ID_KEY));
        assertEquals(
                VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(DELEGATABLE_INACTIVE_ADDRESS_KEY));
        assertEquals(
                VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION,
                subject.decideForPrimitive(CRYPTO_KEY));
    }

    @Test
    void validatesKeysAsExpectedWhenDelegatePermissionRequiredAndNotUsingTopLevelSigs() {
        final var subject =
                new ActiveContractVerificationStrategy(contractID, ACTIVE_ADDRESS, true, UseTopLevelSigs.NO);

        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(ACTIVE_ID_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(ACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(INACTIVE_ID_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(INACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(DELEGATABLE_ACTIVE_ID_KEY));
        assertEquals(VerificationStrategy.Decision.VALID, subject.decideForPrimitive(DELEGATABLE_ACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(DELEGATABLE_INACTIVE_ID_KEY));
        assertEquals(
                VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(DELEGATABLE_INACTIVE_ADDRESS_KEY));
        assertEquals(VerificationStrategy.Decision.INVALID, subject.decideForPrimitive(CRYPTO_KEY));
    }

    @Test
    void signatureTestApprovesEthSenderKeyWhenDelegating() {
        final var subject = mock(VerificationStrategy.class);
        doCallRealMethod().when(subject).asSignatureTestIn(context, A_SECP256K1_KEY);
        given(subject.decideForPrimitive(A_SECP256K1_KEY))
                .willReturn(VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION);

        final var test = subject.asSignatureTestIn(context, A_SECP256K1_KEY);
        assertTrue(test.test(A_SECP256K1_KEY));
    }

    @Test
    void signatureTestUsesContextVerificationWhenNotEthSenderKey() {
        final var keyVerifier = mock(KeyVerifier.class);
        final var verification = mock(SignatureVerification.class);
        final var subject = mock(VerificationStrategy.class);
        doCallRealMethod().when(subject).asSignatureTestIn(context, null);
        given(verification.passed()).willReturn(true);
        given(context.keyVerifier()).willReturn(keyVerifier);
        given(keyVerifier.verificationFor(B_SECP256K1_KEY)).willReturn(verification);
        given(subject.decideForPrimitive(B_SECP256K1_KEY))
                .willReturn(VerificationStrategy.Decision.DELEGATE_TO_CRYPTOGRAPHIC_VERIFICATION);

        final var test = subject.asSignatureTestIn(context, null);
        assertTrue(test.test(B_SECP256K1_KEY));
    }

    @Test
    void signatureTestApprovesAllValidKeyLists() {
        final var subject = mock(VerificationStrategy.class);
        doCallRealMethod().when(subject).asSignatureTestIn(context, null);
        given(subject.decideForPrimitive(AN_ED25519_KEY)).willReturn(VerificationStrategy.Decision.VALID);
        given(subject.decideForPrimitive(ANOTHER_ED25519_KEY)).willReturn(VerificationStrategy.Decision.VALID);
        given(subject.decideForPrimitive(YET_ANOTHER_ED25519_KEY)).willReturn(VerificationStrategy.Decision.VALID);

        final var test = subject.asSignatureTestIn(context, null);
        final var key = Key.newBuilder()
                .keyList(KeyList.newBuilder().keys(AN_ED25519_KEY, ANOTHER_ED25519_KEY, YET_ANOTHER_ED25519_KEY))
                .build();
        assertTrue(test.test(key));
    }

    @Test
    void signatureTestRejectsIncompleteKeyLists() {
        final var subject = mock(VerificationStrategy.class);
        doCallRealMethod().when(subject).asSignatureTestIn(context, null);
        given(subject.decideForPrimitive(AN_ED25519_KEY)).willReturn(VerificationStrategy.Decision.VALID);
        given(subject.decideForPrimitive(ANOTHER_ED25519_KEY)).willReturn(VerificationStrategy.Decision.INVALID);

        final var test = subject.asSignatureTestIn(context, null);
        final var key = Key.newBuilder()
                .keyList(KeyList.newBuilder().keys(AN_ED25519_KEY, ANOTHER_ED25519_KEY, YET_ANOTHER_ED25519_KEY))
                .build();
        assertFalse(test.test(key));
    }

    @Test
    void signatureTestApprovesSufficientThresholdKeys() {
        final var subject = mock(VerificationStrategy.class);
        doCallRealMethod().when(subject).asSignatureTestIn(context, null);
        given(subject.decideForPrimitive(AN_ED25519_KEY)).willReturn(VerificationStrategy.Decision.VALID);
        given(subject.decideForPrimitive(ANOTHER_ED25519_KEY)).willReturn(VerificationStrategy.Decision.INVALID);
        given(subject.decideForPrimitive(YET_ANOTHER_ED25519_KEY)).willReturn(VerificationStrategy.Decision.VALID);

        final var test = subject.asSignatureTestIn(context, null);
        final var key = Key.newBuilder()
                .thresholdKey(ThresholdKey.newBuilder()
                        .threshold(2)
                        .keys(KeyList.newBuilder().keys(AN_ED25519_KEY, ANOTHER_ED25519_KEY, YET_ANOTHER_ED25519_KEY))
                        .build())
                .build();
        assertTrue(test.test(key));
    }

    @Test
    void signatureTestRejectsInsufficientThresholdKeys() {
        final var subject = mock(VerificationStrategy.class);
        doCallRealMethod().when(subject).asSignatureTestIn(context, null);
        given(subject.decideForPrimitive(AN_ED25519_KEY)).willReturn(VerificationStrategy.Decision.VALID);
        given(subject.decideForPrimitive(ANOTHER_ED25519_KEY)).willReturn(VerificationStrategy.Decision.INVALID);
        given(subject.decideForPrimitive(YET_ANOTHER_ED25519_KEY)).willReturn(VerificationStrategy.Decision.INVALID);

        final var test = subject.asSignatureTestIn(context, null);
        final var key = Key.newBuilder()
                .thresholdKey(ThresholdKey.newBuilder()
                        .threshold(2)
                        .keys(KeyList.newBuilder().keys(AN_ED25519_KEY, ANOTHER_ED25519_KEY, YET_ANOTHER_ED25519_KEY))
                        .build())
                .build();
        assertFalse(test.test(key));
    }

    @Test
    void unsupportedKeyTypesAreNotPrimitive() {
        final var subject = mock(VerificationStrategy.class);
        doCallRealMethod().when(subject).asSignatureTestIn(context, null);

        final var aRsa3072Key = Key.newBuilder().rsa3072(Bytes.wrap("NONSENSE")).build();

        final var test = subject.asSignatureTestIn(context, null);
        assertFalse(test.test(aRsa3072Key));
    }
}
