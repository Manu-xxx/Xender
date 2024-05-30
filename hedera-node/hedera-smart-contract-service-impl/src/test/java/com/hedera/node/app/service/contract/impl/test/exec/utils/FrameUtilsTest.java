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

package com.hedera.node.app.service.contract.impl.test.exec.utils;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CallType.DIRECT_OR_PROXY_REDIRECT;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CallType.QUALIFIED_DELEGATE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CallType.UNQUALIFIED_DELEGATE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.HAPI_RECORD_BUILDER_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.TRACKER_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.accessTrackerFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.selfDestructBeneficiariesFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.stackIncludesActiveAddress;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONTRACTS_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_BUT_IS_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_LONG_ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.PERMITTED_ADDRESS_CALLER;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.PERMITTED_CALLERS_CONFIG;
import static com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.exec.operations.utils.OpUtils;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.TransferEventLoggingUtils;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker;
import com.hedera.node.app.service.contract.impl.state.ProxyEvmAccount;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.app.service.contract.impl.utils.OpcodeUtils;
import com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils;
import com.hedera.node.app.spi.workflows.record.DeleteCapableTransactionRecordBuilder;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FrameUtilsTest {
    private static final Set<Class<?>> toBeTested = new HashSet<>(Arrays.asList(
            FrameUtils.class,
            ConversionUtils.class,
            TransferEventLoggingUtils.class,
            OpUtils.class,
            OpcodeUtils.class,
            SynthTxnUtils.class,
            ReturnTypes.class));

    @Mock
    private MessageFrame frame;

    @Mock
    private MessageFrame initialFrame;

    @Mock
    private MutableAccount account;

    @Mock
    private WorldUpdater worldUpdater;

    @Mock
    private ProxyWorldUpdater proxyWorldUpdater;

    @Mock
    private FeatureFlags featureFlags;

    @Mock
    private ProxyEvmAccount proxyEvmAccount;

    private final Deque<MessageFrame> stack = new ArrayDeque<>();

    @Test
    void throwsInConstructor() {
        for (final var clazz : toBeTested) {
            assertFor(clazz);
        }
    }

    @Test
    void initialFrameIsNotDelegated() {
        stack.push(initialFrame);
        given(initialFrame.getMessageFrameStack()).willReturn(stack);
        assertFalse(FrameUtils.acquiredSenderAuthorizationViaDelegateCall(initialFrame));
    }

    @Test
    void singleFrameStackHasNoActiveAddress() {
        stack.add(frame);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertFalse(stackIncludesActiveAddress(frame, EIP_1014_ADDRESS));
    }

    @Test
    void detectsTargetAddressInTwoFrameStack() {
        stack.push(initialFrame);
        stack.add(frame);
        given(frame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertTrue(stackIncludesActiveAddress(frame, EIP_1014_ADDRESS));
    }

    @Test
    void detectsLackOfTargetAddressInTwoFrameStack() {
        stack.push(initialFrame);
        stack.add(frame);
        given(frame.getRecipientAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertFalse(stackIncludesActiveAddress(frame, EIP_1014_ADDRESS));
    }

    @Test
    void onlyExecutingFrameCanBeEvaluatedForDelegateSenderAuthorization() {
        stack.push(frame);
        stack.push(initialFrame);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertThrows(
                IllegalArgumentException.class, () -> FrameUtils.acquiredSenderAuthorizationViaDelegateCall(frame));
    }

    @Test
    void childOfParentExecutingItsOwnCodeDoesNotAcquireSenderAuthorizationViaDelegateCall() {
        given(initialFrame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(initialFrame.getContractAddress()).willReturn(EIP_1014_ADDRESS);
        stack.push(initialFrame);
        stack.push(frame);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertFalse(FrameUtils.acquiredSenderAuthorizationViaDelegateCall(frame));
    }

    @Test
    void unqualifiedDelegateDetectedValidationPass() {
        // given
        stack.push(initialFrame);
        stack.push(frame);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);

        given(frame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getContractAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(initialFrame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(initialFrame.getContractAddress()).willReturn(EIP_1014_ADDRESS);

        given(worldUpdater.get(EIP_1014_ADDRESS)).willReturn(account);
        given(account.getNonce()).willReturn(TOKEN_PROXY_ACCOUNT_NONCE);

        assertEquals(DIRECT_OR_PROXY_REDIRECT, FrameUtils.callTypeOf(frame));
    }

    @Test
    void unqualifiedDelegateDetectedValidationFailTokenNull() {
        // given
        givenNonInitialFrame();
        given(frame.getWorldUpdater()).willReturn(worldUpdater);

        given(frame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getContractAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);

        given(worldUpdater.get(EIP_1014_ADDRESS)).willReturn(null);

        assertEquals(UNQUALIFIED_DELEGATE, FrameUtils.callTypeOf(frame));
    }

    @Test
    void unqualifiedDelegateDetectedValidationPassWithPermittedCaller() {
        // given
        stack.push(initialFrame);
        stack.push(frame);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(frame.getWorldUpdater()).willReturn(worldUpdater);

        given(frame.getRecipientAddress()).willReturn(PERMITTED_ADDRESS_CALLER);
        given(frame.getContractAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(initialFrame.getRecipientAddress()).willReturn(PERMITTED_ADDRESS_CALLER);
        given(initialFrame.getContractAddress()).willReturn(PERMITTED_ADDRESS_CALLER);

        given(worldUpdater.get(PERMITTED_ADDRESS_CALLER)).willReturn(null);
        given(initialFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(PERMITTED_CALLERS_CONFIG);

        assertEquals(QUALIFIED_DELEGATE, FrameUtils.callTypeOf(frame));
    }

    @Test
    void detectDelegateCallToAccount() {
        // given
        stack.push(initialFrame);
        stack.push(frame);

        given(frame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getContractAddress()).willReturn(EIP_1014_ADDRESS);

        assertEquals(DIRECT_OR_PROXY_REDIRECT, FrameUtils.callTypeForAccountOf(frame));
    }

    @Test
    void detectRedirectToRegularAccount() {
        // given
        stack.push(initialFrame);
        stack.push(frame);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        given(frame.getMessageFrameStack()).willReturn(stack);

        given(frame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getContractAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        given(initialFrame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(initialFrame.getContractAddress()).willReturn(EIP_1014_ADDRESS);

        given(proxyWorldUpdater.getHederaAccount(EIP_1014_ADDRESS)).willReturn(proxyEvmAccount);
        given(proxyEvmAccount.isRegularAccount()).willReturn(true);

        assertEquals(DIRECT_OR_PROXY_REDIRECT, FrameUtils.callTypeForAccountOf(frame));
    }

    @Test
    void detectRedirectToNonRegularAccount() {
        // given
        stack.push(initialFrame);
        stack.push(frame);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);

        given(frame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(frame.getContractAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);

        given(proxyWorldUpdater.getHederaAccount(EIP_1014_ADDRESS)).willReturn(proxyEvmAccount);
        given(proxyEvmAccount.isRegularAccount()).willReturn(false);

        assertEquals(UNQUALIFIED_DELEGATE, FrameUtils.callTypeForAccountOf(frame));
    }

    @Test
    void childOfParentExecutingDelegateCodeDoesAcquireSenderAuthorizationViaDelegateCall() {
        given(initialFrame.getRecipientAddress()).willReturn(EIP_1014_ADDRESS);
        given(initialFrame.getContractAddress()).willReturn(NON_SYSTEM_LONG_ZERO_ADDRESS);
        stack.push(initialFrame);
        stack.push(frame);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertTrue(FrameUtils.acquiredSenderAuthorizationViaDelegateCall(frame));
    }

    @Test
    void getsContextVariablesFromInitialFrameIfStackEmpty() {
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(DEFAULT_CONFIG);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertSame(DEFAULT_CONFIG, configOf(frame));
    }

    @Test
    void getsContextVariablesFromBottomOfStackIfNotInitialFrame() {
        givenNonInitialFrame();
        given(initialFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(DEFAULT_CONFIG);
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertSame(DEFAULT_CONFIG, configOf(frame));
    }

    @Test
    void checksForAccessorAsExpected() {
        givenNonInitialFrame();
        given(frame.getMessageFrameStack()).willReturn(stack);
        final var tracker = new StorageAccessTracker();
        given(initialFrame.getContextVariable(TRACKER_CONTEXT_VARIABLE)).willReturn(tracker);
        assertSame(tracker, accessTrackerFor(frame));
    }

    @Test
    void checksForBeneficiaryMapAsExpected() {
        givenNonInitialFrame();
        given(frame.getMessageFrameStack()).willReturn(stack);
        final DeleteCapableTransactionRecordBuilder beneficiaries = mock(DeleteCapableTransactionRecordBuilder.class);
        given(initialFrame.getContextVariable(HAPI_RECORD_BUILDER_CONTEXT_VARIABLE))
                .willReturn(beneficiaries);
        assertSame(beneficiaries, selfDestructBeneficiariesFor(frame));
    }

    @Test
    void okIfFrameHasNoTracker() {
        given(frame.getMessageFrameStack()).willReturn(stack);
        assertNull(accessTrackerFor(frame));
    }

    @Test
    void checkContractRequired() {
        givenNonInitialFrame();
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(initialFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(DEFAULT_CONFIG);
        assertTrue(FrameUtils.contractRequired(frame, EIP_1014_ADDRESS, featureFlags));
        verify(featureFlags).isAllowCallsToNonContractAccountsEnabled(DEFAULT_CONTRACTS_CONFIG, null);
    }

    @Test
    void checkContractRequiredLongZero() {
        givenNonInitialFrame();
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(initialFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(DEFAULT_CONFIG);
        assertTrue(FrameUtils.contractRequired(frame, NON_SYSTEM_BUT_IS_LONG_ZERO_ADDRESS, featureFlags));
        verify(featureFlags)
                .isAllowCallsToNonContractAccountsEnabled(
                        DEFAULT_CONTRACTS_CONFIG,
                        NON_SYSTEM_BUT_IS_LONG_ZERO_ADDRESS
                                .toUnsignedBigInteger()
                                .longValueExact());
    }

    @Test
    void checkContractRequiredLongZeroTooBig() {
        givenNonInitialFrame();
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(initialFrame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(DEFAULT_CONFIG);
        assertTrue(FrameUtils.contractRequired(frame, Address.fromHexString("0xFFFFFFFFFFFFFFFF"), featureFlags));
        verify(featureFlags).isAllowCallsToNonContractAccountsEnabled(DEFAULT_CONTRACTS_CONFIG, null);
    }

    void givenNonInitialFrame() {
        stack.push(initialFrame);
        stack.addFirst(frame);
    }

    private static final String UNEXPECTED_THROW = "Unexpected `%s` was thrown in `%s` constructor!";
    private static final String NO_THROW = "No exception was thrown in `%s` constructor!";

    private void assertFor(final Class<?> clazz) {
        try {
            final var constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);

            constructor.newInstance();
        } catch (final InvocationTargetException expected) {
            final var cause = expected.getCause();
            assertTrue(cause instanceof UnsupportedOperationException, String.format(UNEXPECTED_THROW, cause, clazz));
            return;
        } catch (final Exception e) {
            Assertions.fail(String.format(UNEXPECTED_THROW, e, clazz));
        }
        Assertions.fail(String.format(NO_THROW, clazz));
    }
}
