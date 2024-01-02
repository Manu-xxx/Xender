/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.contracts.operations;

import static com.hedera.node.app.service.evm.contracts.operations.HederaEvmOperationsUtilV038.EVM_VERSION_0_46;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.TOO_MANY_STACK_ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import java.util.function.BiPredicate;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.OverflowException;
import org.hyperledger.besu.evm.internal.UnderflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaExtCodeHashOperationV038Test {
    private final String EVM_VERSION_0_38 = "v0.38";

    @Mock
    private AbstractLedgerEvmWorldUpdater<?, ?> worldUpdater;

    @Mock
    private Account account;

    @Mock
    private GasCalculator gasCalculator;

    @Mock
    private MessageFrame mf;

    @Mock
    private EVM evm;

    @Mock
    private EvmProperties evmProperties;

    @Mock
    private BiPredicate<Address, MessageFrame> addressValidator;

    private HederaExtCodeHashOperationV038 subject;

    private final String ETH_ADDRESS = "0xc257274276a4e539741ca11b590b9447b26a8051";
    private final Address ETH_ADDRESS_INSTANCE = Address.fromHexString(ETH_ADDRESS);
    private final long OPERATION_COST = 1_000L;
    private final long WARM_READ_COST = 100L;
    private final long ACTUAL_COST = OPERATION_COST + WARM_READ_COST;

    @BeforeEach
    void setUp() {
        subject = new HederaExtCodeHashOperationV038(gasCalculator, addressValidator, a -> false, evmProperties);
        given(gasCalculator.extCodeHashOperationGasCost()).willReturn(OPERATION_COST);
        given(gasCalculator.getWarmStorageReadCost()).willReturn(WARM_READ_COST);
    }

    @Test
    void executeResolvesToInvalidSolidityAddress() {
        given(mf.popStackItem()).willReturn(ETH_ADDRESS_INSTANCE);
        given(addressValidator.test(any(), any())).willReturn(false);
        given(evmProperties.evmVersion()).willReturn(EVM_VERSION_0_38);

        var opResult = subject.execute(mf, evm);

        assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, opResult.getHaltReason());
        assertEquals(ACTUAL_COST, opResult.getGasCost());
    }

    @Test
    void executeResolvesToInvalidSolidityAddressAndAllowCallsToNonContractAccountsDisabled() {
        given(mf.popStackItem()).willReturn(ETH_ADDRESS_INSTANCE);
        given(addressValidator.test(any(), any())).willReturn(false);
        given(evmProperties.evmVersion()).willReturn(EVM_VERSION_0_46);
        given(evmProperties.allowCallsToNonContractAccounts()).willReturn(false);

        var opResult = subject.execute(mf, evm);

        assertEquals(HederaExceptionalHaltReason.INVALID_SOLIDITY_ADDRESS, opResult.getHaltReason());
        assertEquals(ACTUAL_COST, opResult.getGasCost());
    }

    @Test
    void executeResolvesToInsufficientGas() {
        givenMessageFrameWithRemainingGas(ACTUAL_COST - 1L);
        given(addressValidator.test(any(), any())).willReturn(true);
        given(evmProperties.evmVersion()).willReturn(EVM_VERSION_0_38);

        var opResult = subject.execute(mf, evm);

        assertEquals(ExceptionalHaltReason.INSUFFICIENT_GAS, opResult.getHaltReason());
        assertEquals(ACTUAL_COST, opResult.getGasCost());
    }

    @Test
    void executeHappyPathWithEmptyAccount() {
        givenMessageFrameWithRemainingGas(ACTUAL_COST + 1L);
        given(account.isEmpty()).willReturn(true);
        given(addressValidator.test(any(), any())).willReturn(true);
        given(evmProperties.evmVersion()).willReturn(EVM_VERSION_0_38);

        var opResult = subject.execute(mf, evm);

        assertEquals(ACTUAL_COST, opResult.getGasCost());
    }

    @Test
    void executeHappyPathWithPrecompileAccount() {
        // given
        subject = new HederaExtCodeHashOperationV038(gasCalculator, addressValidator, a -> true, evmProperties);
        given(mf.popStackItem()).willReturn(ETH_ADDRESS_INSTANCE);
        // when
        var opResult = subject.execute(mf, evm);
        // then
        assertEquals(ACTUAL_COST, opResult.getGasCost());
        verify(mf).pushStackItem(UInt256.ZERO);
    }

    @Test
    void executeHappyPathWithAccount() {
        givenMessageFrameWithRemainingGas(ACTUAL_COST + 1L);
        given(account.getCodeHash()).willReturn(Hash.hash(Bytes.of(1)));
        given(addressValidator.test(any(), any())).willReturn(true);
        given(evmProperties.evmVersion()).willReturn(EVM_VERSION_0_38);

        var opResult = subject.execute(mf, evm);

        assertEquals(ACTUAL_COST, opResult.getGasCost());
    }

    @Test
    void executeWithGasRemainingAsActualCost() {
        givenMessageFrameWithRemainingGas(ACTUAL_COST);
        given(account.isEmpty()).willReturn(false);
        given(account.getCodeHash()).willReturn(Hash.hash(Bytes.of(1)));
        given(addressValidator.test(any(), any())).willReturn(true);
        given(evmProperties.evmVersion()).willReturn(EVM_VERSION_0_38);

        var opResult = subject.execute(mf, evm);

        assertEquals(ACTUAL_COST, opResult.getGasCost());
    }

    @Test
    void executeThrowsInsufficientStackItems() {
        given(mf.popStackItem()).willThrow(UnderflowException.class);

        var opResult = subject.execute(mf, evm);

        assertEquals(INSUFFICIENT_STACK_ITEMS, opResult.getHaltReason());
        assertEquals(ACTUAL_COST, opResult.getGasCost());
    }

    @Test
    void executeThrowsTooManyStackItems() {
        // given
        subject = new HederaExtCodeHashOperationV038(gasCalculator, addressValidator, a -> true, evmProperties);
        given(mf.popStackItem()).willReturn(ETH_ADDRESS_INSTANCE);
        doThrow(OverflowException.class).when(mf).pushStackItem(any(Bytes.class));
        // when
        var opResult = subject.execute(mf, evm);
        // then
        assertEquals(TOO_MANY_STACK_ITEMS, opResult.getHaltReason());
        assertEquals(ACTUAL_COST, opResult.getGasCost());
    }

    private void givenMessageFrameWithRemainingGas(long gas) {
        given(mf.popStackItem()).willReturn(ETH_ADDRESS_INSTANCE);
        given(mf.getWorldUpdater()).willReturn(worldUpdater);
        given(mf.warmUpAddress(ETH_ADDRESS_INSTANCE)).willReturn(true);
        given(mf.getRemainingGas()).willReturn(gas);
        given(worldUpdater.get(ETH_ADDRESS_INSTANCE)).willReturn(account);
    }
}
