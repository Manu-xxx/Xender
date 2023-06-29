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

package com.hedera.node.app.service.contract.impl.test.exec.gas;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmBlocks;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmCode;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomGasChargingTest {
    @Mock
    private HederaEvmAccount sender;

    @Mock
    private HederaEvmAccount relayer;

    @Mock
    private HederaEvmCode code;

    @Mock
    private HederaEvmBlocks blocks;

    @Mock
    private HederaWorldUpdater worldUpdater;

    @Mock
    private GasCalculator gasCalculator;

    private CustomGasCharging subject;

    @BeforeEach
    void setUp() {
        subject = new CustomGasCharging(gasCalculator);
    }

    @Test
    void staticCallsDoNotChargeGas() {
        final var chargingResult = subject.chargeForGas(
                sender, relayer, wellKnownContextWith(code, blocks, true), worldUpdater, wellKnownHapiCall());
        assertEquals(0, chargingResult.relayerAllowanceUsed());
        verifyNoInteractions(gasCalculator);
    }

    @Test
    void failsImmediatelyIfGasLimitBelowIntrinsicGas() {
        givenWellKnownIntrinsicGasCost();
        assertFailsWith(
                INSUFFICIENT_GAS,
                () -> subject.chargeForGas(
                        sender,
                        relayer,
                        wellKnownContextWith(code, blocks),
                        worldUpdater,
                        wellKnownRelayedHapiCallWithGasLimit(TestHelpers.INTRINSIC_GAS - 1)));
    }

    @Test
    void failsImmediatelyIfPayerBalanceBelowUpfrontCost() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownHapiCall();
        given(sender.getBalance()).willReturn(Wei.of(transaction.upfrontCostGiven(NETWORK_GAS_PRICE) - 1));
        assertFailsWith(
                INSUFFICIENT_PAYER_BALANCE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction));
    }

    @Test
    void deductsGasCostIfUpfrontCostIsAfforded() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownHapiCall();
        given(sender.hederaId()).willReturn(SENDER_ID);
        given(sender.getBalance()).willReturn(Wei.of(transaction.upfrontCostGiven(NETWORK_GAS_PRICE)));
        final var chargingResult =
                subject.chargeForGas(sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction);
        assertEquals(0, chargingResult.relayerAllowanceUsed());
        verify(worldUpdater).collectFee(SENDER_ID, transaction.gasCostGiven(NETWORK_GAS_PRICE));
    }

    @Test
    void requiresSufficientGasAllowanceIfUserOfferedPriceIsZero() {
        givenWellKnownIntrinsicGasCost();
        final var insufficientMaxAllowance = 123L;
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(0, insufficientMaxAllowance);
        assertFailsWith(
                INSUFFICIENT_TX_FEE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction));
    }

    @Test
    void requiresRelayerToHaveSufficientBalanceIfUserOfferedPriceIsZero() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(0, Long.MAX_VALUE);
        given(relayer.getBalance()).willReturn(Wei.of(transaction.gasCostGiven(NETWORK_GAS_PRICE) - 1));
        assertFailsWith(
                INSUFFICIENT_PAYER_BALANCE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction));
    }

    @Test
    void chargesRelayerOnlyIfUserOfferedPriceIsZero() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(0, Long.MAX_VALUE);
        final var gasCost = transaction.gasCostGiven(NETWORK_GAS_PRICE);
        given(relayer.getBalance()).willReturn(Wei.of(gasCost));
        given(relayer.hederaId()).willReturn(RELAYER_ID);
        final var chargingResult =
                subject.chargeForGas(sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction);
        assertEquals(gasCost, chargingResult.relayerAllowanceUsed());
        verify(worldUpdater).collectFee(RELAYER_ID, gasCost);
    }

    @Test
    void chargesSenderOnlyIfUserOfferedPriceIsAtLeastNetworkPrice() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE, 0);
        final var gasCost = transaction.gasCostGiven(NETWORK_GAS_PRICE);
        given(sender.getBalance()).willReturn(Wei.of(gasCost));
        given(sender.hederaId()).willReturn(SENDER_ID);
        final var chargingResult =
                subject.chargeForGas(sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction);
        assertEquals(0, chargingResult.relayerAllowanceUsed());
        verify(worldUpdater).collectFee(SENDER_ID, gasCost);
    }

    @Test
    void rejectsIfSenderCannotCoverOfferedGasCost() {
        givenWellKnownIntrinsicGasCost();
        final var transaction =
                wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE / 2, Long.MAX_VALUE);
        given(sender.getBalance()).willReturn(Wei.of(transaction.offeredGasCost() - 1));
        assertFailsWith(
                INSUFFICIENT_PAYER_BALANCE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction));
    }

    @Test
    void rejectsIfRelayerCannotCoverRemainingGasCost() {
        givenWellKnownIntrinsicGasCost();
        final var transaction =
                wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE / 2, Long.MAX_VALUE);
        given(sender.getBalance()).willReturn(Wei.of(transaction.offeredGasCost()));
        given(relayer.getBalance()).willReturn(Wei.ZERO);
        assertFailsWith(
                INSUFFICIENT_PAYER_BALANCE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction));
    }

    @Test
    void failsIfGasAllownaceLessThanRemainingGasCost() {
        givenWellKnownIntrinsicGasCost();
        final var transaction = wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE / 2, 0);
        assertFailsWith(
                INSUFFICIENT_TX_FEE,
                () -> subject.chargeForGas(
                        sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction));
    }

    @Test
    void chargesSenderAndRelayerIfBothSolventAndWilling() {
        givenWellKnownIntrinsicGasCost();
        final var transaction =
                wellKnownRelayedHapiCallWithUserGasPriceAndMaxAllowance(NETWORK_GAS_PRICE / 2, Long.MAX_VALUE);
        final var gasCost = transaction.gasCostGiven(NETWORK_GAS_PRICE);
        final var relayerGasCost = gasCost - transaction.offeredGasCost();
        given(sender.getBalance()).willReturn(Wei.of(gasCost));
        given(sender.hederaId()).willReturn(SENDER_ID);
        given(relayer.getBalance()).willReturn(Wei.of(gasCost));
        given(relayer.hederaId()).willReturn(RELAYER_ID);
        final var chargingResult =
                subject.chargeForGas(sender, relayer, wellKnownContextWith(code, blocks), worldUpdater, transaction);
        assertEquals(relayerGasCost, chargingResult.relayerAllowanceUsed());
        verify(worldUpdater).collectFee(SENDER_ID, transaction.offeredGasCost());
        verify(worldUpdater).collectFee(RELAYER_ID, relayerGasCost);
    }

    private void givenWellKnownIntrinsicGasCost() {
        givenWellKnownIntrinsicGasCost(false);
    }

    private void givenWellKnownIntrinsicGasCost(boolean isCreation) {
        given(gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, isCreation))
                .willReturn(TestHelpers.INTRINSIC_GAS);
    }
}
