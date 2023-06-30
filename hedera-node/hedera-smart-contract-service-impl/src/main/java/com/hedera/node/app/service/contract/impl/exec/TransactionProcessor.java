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

package com.hedera.node.app.service.contract.impl.exec;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.isEvmAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCharging;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameBuilder;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameRunner;
import com.hedera.node.app.service.contract.impl.hevm.*;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.spi.workflows.HandleException;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;

/**
 * Modeled after the Besu {@code MainnetTransactionProcessor}, so that all four HAPI
 * contract operations ({@code ContractCall}, {@code ContractCreate}, {@code EthereumTransaction},
 * {@code ContractCallLocal}) can reduce to a single code path.
 */
public class TransactionProcessor {
    private final FrameBuilder frameBuilder;
    private final FrameRunner frameRunner;
    private final CustomGasCharging gasCharging;
    private final CustomMessageCallProcessor messageCall;
    private final ContractCreationProcessor contractCreation;

    public TransactionProcessor(
            @NonNull final FrameBuilder frameBuilder,
            @NonNull final FrameRunner frameRunner,
            @NonNull final CustomGasCharging gasCharging,
            @NonNull final CustomMessageCallProcessor messageCall,
            @NonNull final ContractCreationProcessor contractCreation) {
        this.frameBuilder = requireNonNull(frameBuilder);
        this.frameRunner = requireNonNull(frameRunner);
        this.gasCharging = requireNonNull(gasCharging);
        this.messageCall = requireNonNull(messageCall);
        this.contractCreation = requireNonNull(contractCreation);
    }

    public HederaEvmTransactionResult processTransaction(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater updater,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaTracer tracer,
            @NonNull final Configuration config) {
        try {
            // Compute the sender, relayer, and to address (will throw if invalid)
            final var parties = computeInvolvedParties(transaction, updater, config);
            if (transaction.isEthereumTransaction()) {
                parties.sender().incrementNonce();
            }

            // Charge gas and return intrinsic gas and relayer allowance used (will throw on failure)
            final var gasCharges =
                    gasCharging.chargeForGas(parties.sender(), parties.relayer(), context, updater, transaction);

            // Build the initial frame for the transaction
            final var initialFrame = frameBuilder.buildInitialFrameWith(
                    transaction,
                    updater,
                    context,
                    config,
                    parties.sender().getAddress(),
                    parties.toAddress(),
                    gasCharges.intrinsicGas());
            // Return the result of running the frame to completion
            return frameRunner.runToCompletion(
                    transaction.gasLimit(), initialFrame, tracer, messageCall, contractCreation);
        } catch (final HandleException failure) {
            return HederaEvmTransactionResult.abortFor(failure.getStatus());
        }
    }

    private record InvolvedParties(
            @NonNull HederaEvmAccount sender, @Nullable HederaEvmAccount relayer, @NonNull Address toAddress) {}

    private InvolvedParties computeInvolvedParties(
            @NonNull final HederaEvmTransaction transaction,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final Configuration config) {

        final var sender = worldUpdater.getHederaAccount(transaction.senderId());
        validateTrue(sender != null, INVALID_ACCOUNT_ID);
        HederaEvmAccount relayer = null;
        if (transaction.isEthereumTransaction()) {
            relayer = worldUpdater.getHederaAccount(requireNonNull(transaction.relayerId()));
            validateTrue(relayer != null, INVALID_ACCOUNT_ID);
        }
        if (transaction.isCreate()) {
            throw new AssertionError("Not implemented");
        } else {
            final var to = worldUpdater.getHederaAccount(transaction.contractIdOrThrow());
            if (maybeLazyCreate(transaction, to, config)) {
                validateTrue(transaction.hasValue(), INVALID_CONTRACT_ID);
                final var alias = transaction.contractIdOrThrow().evmAddressOrThrow();
                validateTrue(isEvmAddress(alias), INVALID_CONTRACT_ID);
                return new InvolvedParties(sender, relayer, pbjToBesuAddress(alias));
            } else {
                validateTrue(to != null, INVALID_CONTRACT_ID);
                return new InvolvedParties(sender, relayer, requireNonNull(to).getAddress());
            }
        }
    }

    private boolean maybeLazyCreate(
            @NonNull final HederaEvmTransaction transaction,
            @Nullable final HederaEvmAccount to,
            @NonNull final Configuration config) {
        return to == null && transaction.isEthereumTransaction() && messageCall.isImplicitCreationEnabled(config);
    }
}
