/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.BigIntegerType;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class ExchangeRateSystemContract extends AbstractFullContract implements HederaSystemContract {

    private static final String PRECOMPILE_NAME = "ExchangeRate";
    private static final BigIntegerType WORD_DECODER = TypeFactory.create("uint256");

    // tinycentsToTinybars(uint256)
    public static final int TO_TINYBARS_SELECTOR = 0x2e3cff6a;
    // tinybarsToTinycents(uint256)
    public static final int TO_TINYCENTS_SELECTOR = 0x43a88229;

    public static final String EXCHANGE_RATE_SYSTEM_CONTRACT_ADDRESS = "0x168";
    private long gasRequirement;

    @Inject
    public ExchangeRateSystemContract(@NonNull final GasCalculator gasCalculator) {
        super(PRECOMPILE_NAME, gasCalculator);
    }

    @Override
    public long gasRequirement(Bytes input) {
        return gasRequirement;
    }

    @Override
    @NonNull
    public FullResult computeFully(@NonNull Bytes input, @NonNull MessageFrame messageFrame) {
        requireNonNull(input);
        requireNonNull(messageFrame);
        try {
            final var proxyWorldUpdater = (ProxyWorldUpdater) messageFrame.getWorldUpdater();
            gasRequirement = proxyWorldUpdater.contractsConfig().precompileExchangeRateGasCost();
            final var selector = input.getInt(0);
            final var amount = biValueFrom(input);
            final var activeRate = proxyWorldUpdater.currentExchangeRate();
            final var result =
                    switch (selector) {
                        case TO_TINYBARS_SELECTOR -> padded(
                                fromAToB(amount, activeRate.hbarEquiv(), activeRate.centEquiv()));
                        case TO_TINYCENTS_SELECTOR -> padded(
                                fromAToB(amount, activeRate.centEquiv(), activeRate.hbarEquiv()));
                        default -> null;
                    };
            requireNonNull(result);
            return new FullResult(PrecompileContractResult.success(result), gasRequirement);
        } catch (Exception ignore) {
            return new FullResult(
                    PrecompileContractResult.halt(Bytes.EMPTY, Optional.of(ExceptionalHaltReason.INVALID_OPERATION)),
                    gasRequirement);
        }
    }

    @NonNull
    private BigInteger fromAToB(
            @NonNull final BigInteger aAmount, @NonNull final int bEquiv, @NonNull final int aEquiv) {
        return aAmount.multiply(BigInteger.valueOf(bEquiv)).divide(BigInteger.valueOf(aEquiv));
    }

    @NonNull
    private BigInteger biValueFrom(@NonNull final Bytes input) {
        return WORD_DECODER.decode(input.slice(4).toArrayUnsafe());
    }

    @NonNull
    private Bytes padded(@NonNull final BigInteger result) {
        return Bytes32.leftPad(Bytes.wrap(result.toByteArray()));
    }
}
