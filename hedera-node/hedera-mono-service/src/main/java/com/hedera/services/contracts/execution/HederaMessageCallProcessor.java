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
package com.hedera.services.contracts.execution;

import static com.hedera.services.contracts.operation.HederaExceptionalHaltReason.FAILURE_DURING_LAZY_ACCOUNT_CREATE;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.INSUFFICIENT_GAS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.CODE_EXECUTING;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.EXCEPTIONAL_HALT;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.REVERT;

import com.hedera.services.contracts.execution.traceability.ContractActionType;
import com.hedera.services.contracts.execution.traceability.HederaOperationTracer;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.services.txns.crypto.AutoCreationLogic;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.precompile.PrecompileContractRegistry;
import org.hyperledger.besu.evm.precompile.PrecompiledContract;
import org.hyperledger.besu.evm.processor.MessageCallProcessor;
import org.hyperledger.besu.evm.tracing.OperationTracer;

/** Overrides Besu precompiler handling, so we can break model layers in Precompile execution */
public class HederaMessageCallProcessor extends MessageCallProcessor {
    private static final String INVALID_TRANSFER_MSG = "Transfer of Value to Hedera Precompile";
    private static final Optional<ExceptionalHaltReason> ILLEGAL_STATE_CHANGE =
            Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE);
    public static final Bytes INVALID_TRANSFER =
            Bytes.of(INVALID_TRANSFER_MSG.getBytes(StandardCharsets.UTF_8));

    private final Map<Address, PrecompiledContract> hederaPrecompiles;
    private AutoCreationLogic autoCreationLogic;
    private RecordsHistorian recordsHistorian;

    public HederaMessageCallProcessor(
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList) {
        super(evm, precompiles);
        hederaPrecompiles = new HashMap<>();
        hederaPrecompileList.forEach((k, v) -> hederaPrecompiles.put(Address.fromHexString(k), v));
    }

    public HederaMessageCallProcessor(
            final EVM evm,
            final PrecompileContractRegistry precompiles,
            final Map<String, PrecompiledContract> hederaPrecompileList,
            final AutoCreationLogic autoCreationLogic,
            final RecordsHistorian recordsHistorian) {
        super(evm, precompiles);
        hederaPrecompiles = new HashMap<>();
        hederaPrecompileList.forEach((k, v) -> hederaPrecompiles.put(Address.fromHexString(k), v));
        this.autoCreationLogic = autoCreationLogic;
        this.recordsHistorian = recordsHistorian;
    }

    @Override
    public void start(final MessageFrame frame, final OperationTracer operationTracer) {
        MessageFrame.State nonPrecompileResultState = null;
        final var hederaPrecompile = hederaPrecompiles.get(frame.getContractAddress());
        if (hederaPrecompile != null) {
            executeHederaPrecompile(hederaPrecompile, frame, operationTracer);
        } else {
            if (frame.getValue().greaterThan(Wei.ZERO)) {
                final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
                if (updater.isTokenAddress(frame.getRecipientAddress())) {
                    frame.setExceptionalHaltReason(ILLEGAL_STATE_CHANGE);
                    frame.setState(MessageFrame.State.EXCEPTIONAL_HALT);
                } else if (updater.get(frame.getRecipientAddress()) == null) {
                    // can be reached only when EVM_VERSION >= 0.32
                    executeLazyCreate(frame, updater, operationTracer);
                }
            }
            if (frame.getState() != EXCEPTIONAL_HALT) {
                super.start(frame, operationTracer);
            }
            nonPrecompileResultState = frame.getState();
        }
        if (nonPrecompileResultState != EXCEPTIONAL_HALT
                && nonPrecompileResultState != CODE_EXECUTING) {
            // Pre-compile execution doesn't set the state to CODE_EXECUTING after start()
            ((HederaOperationTracer) operationTracer)
                    .tracePrecompileResult(
                            frame,
                            hederaPrecompile != null
                                    ? ContractActionType.SYSTEM
                                    : ContractActionType.PRECOMPILE);
        }
    }

    void executeHederaPrecompile(
            final PrecompiledContract contract,
            final MessageFrame frame,
            final OperationTracer operationTracer) {
        final long gasRequirement;
        final Bytes output;
        if (contract instanceof HTSPrecompiledContract htsPrecompile) {
            final var costedResult = htsPrecompile.computeCosted(frame.getInputData(), frame);
            output = costedResult.getValue();
            gasRequirement = costedResult.getKey();
        } else {
            output = contract.computePrecompile(frame.getInputData(), frame).getOutput();
            gasRequirement = contract.gasRequirement(frame.getInputData());
        }
        operationTracer.tracePrecompileCall(frame, gasRequirement, output);
        if (frame.getState() == REVERT) {
            return;
        }
        if (frame.getRemainingGas() < gasRequirement) {
            frame.decrementRemainingGas(frame.getRemainingGas());
            frame.setExceptionalHaltReason(Optional.of(INSUFFICIENT_GAS));
            frame.setState(EXCEPTIONAL_HALT);
        } else if (output != null) {
            frame.decrementRemainingGas(gasRequirement);
            frame.setOutputData(output);
            frame.setState(COMPLETED_SUCCESS);
        } else {
            frame.setState(EXCEPTIONAL_HALT);
        }
    }

    private void executeLazyCreate(
            final MessageFrame frame,
            final HederaStackedWorldStateUpdater updater,
            final OperationTracer operationTracer) {
        final var syntheticBalanceChange =
                BalanceChange.changingHbar(
                        AccountAmount.newBuilder()
                                .setAccountID(
                                        AccountID.newBuilder()
                                                .setAlias(
                                                        ByteStringUtils.wrapUnsafely(
                                                                frame.getRecipientAddress()
                                                                        .toArrayUnsafe()))
                                                .build())
                                .build(),
                        null);
        final var lazyCreateResult =
                autoCreationLogic.create(syntheticBalanceChange, updater.trackingAccounts(), null);
        if (lazyCreateResult.getLeft() != ResponseCodeEnum.OK) {
            haltFrameAndTraceCreationResult(
                    frame, operationTracer, FAILURE_DURING_LAZY_ACCOUNT_CREATE);
        } else {
            final var creationFeeInTinybars = lazyCreateResult.getRight();
            final var creationFeeInGas = creationFeeInTinybars / frame.getGasPrice().toLong();
            if (frame.getRemainingGas() < creationFeeInGas) {
                // ledgers won't be committed on unsuccessful frame,
                // all we need to do is clear the alias link
                autoCreationLogic.reclaimPendingAliases();
                haltFrameAndTraceCreationResult(frame, operationTracer, INSUFFICIENT_GAS);
            } else {
                frame.decrementRemainingGas(creationFeeInGas);
                // track auto-creation preceding child record
                autoCreationLogic.submitRecordsTo(recordsHistorian);
                // track the lazy account so it is accessible to the EVM
                updater.trackLazilyCreatedAccount(
                        EntityIdUtils.asTypedEvmAddress(syntheticBalanceChange.accountId()));
            }
        }
    }

    private void haltFrameAndTraceCreationResult(
            final MessageFrame frame,
            final OperationTracer operationTracer,
            final ExceptionalHaltReason haltReason) {
        frame.decrementRemainingGas(frame.getRemainingGas());
        frame.setState(EXCEPTIONAL_HALT);
        operationTracer.traceAccountCreationResult(frame, Optional.of(haltReason));
    }
}
