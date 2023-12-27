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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MISSING_TOKEN_SYMBOL;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_EVM_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.RC_AND_ADDRESS_ENCODER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.standardized;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.stackIncludesActiveAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedFor;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy.UseTopLevelSigs;
import com.hedera.node.app.service.contract.impl.exec.scope.EitherOrVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ClassicCreatesCall extends AbstractHtsCall {
    /**
     * The mono-service stipulated gas cost for a token creation (remaining fee is collected by sent value)
     */
    private static final long FIXED_GAS_COST = 100_000L;

    @NonNull
    final TransactionBody syntheticCreate;

    private final VerificationStrategy verificationStrategy;
    private final AccountID spenderId;
    private final long nonGasCost;

    public ClassicCreatesCall(
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final TransactionBody syntheticCreate,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final Address spender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(systemContractGasCalculator, enhancement, false);
        this.syntheticCreate = requireNonNull(syntheticCreate);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        final var baseCost = gasCalculator.canonicalPriceInTinybars(syntheticCreate, spenderId);
        // The non-gas cost is a 20% surcharge on the HAPI TokenCreate price, minus the fee taken as gas
        this.nonGasCost = baseCost + (baseCost / 5) - gasCalculator.gasCostInTinybars(FIXED_GAS_COST);
    }

    private record LegacyActivation(long contractNum, Bytes pbjAddress, Address besuAddress) {}

    @Override
    public @NonNull PricedResult execute(@NonNull final MessageFrame frame) {
        if (frame.getValue().lessThan(Wei.of(nonGasCost))) {
            return completionWith(
                    FIXED_GAS_COST,
                    systemContractOperations().externalizePreemptedDispatch(syntheticCreate, INSUFFICIENT_TX_FEE),
                    RC_AND_ADDRESS_ENCODER.encodeElements((long) INSUFFICIENT_TX_FEE.protoOrdinal(), ZERO_ADDRESS));
        } else {
            operations().collectFee(spenderId, nonGasCost);
        }

        final var token = ((TokenCreateTransactionBody) syntheticCreate.data().value());
        if (token.symbol().isEmpty()) {
            return externalizeUnsuccessfulResult(MISSING_TOKEN_SYMBOL, gasCalculator.viewGasRequirement());
        }

        final var treasuryAccount =
                nativeOperations().getAccount(token.treasuryOrThrow().accountNumOrThrow());
        if (treasuryAccount == null) {
            return externalizeUnsuccessfulResult(INVALID_ACCOUNT_ID, gasCalculator.viewGasRequirement());
        }
        if (token.autoRenewAccount() == null) {
            return externalizeUnsuccessfulResult(INVALID_EXPIRATION_TIME, gasCalculator.viewGasRequirement());
        }

        // Choose a dispatch verification strategy based on whether the legacy activation address is active
        final var dispatchVerificationStrategy = verificationStrategyFor(frame);
        final var recordBuilder = systemContractOperations()
                .dispatch(syntheticCreate, dispatchVerificationStrategy, spenderId, ContractCallRecordBuilder.class);
        recordBuilder.status(standardized(recordBuilder.status()));

        final var customFees =
                ((TokenCreateTransactionBody) syntheticCreate.data().value()).customFees();
        final var tokenType =
                ((TokenCreateTransactionBody) syntheticCreate.data().value()).tokenType();
        final var status = recordBuilder.status();
        if (status != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder, FIXED_GAS_COST), status, false);
        } else {
            final var isFungible = tokenType == TokenType.FUNGIBLE_COMMON;
            ByteBuffer encodedOutput;

            if (isFungible && customFees.isEmpty()) {
                encodedOutput = CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements(
                                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                headlongAddressOf(recordBuilder.tokenID()));
            } else if (isFungible && !customFees.isEmpty()) {
                encodedOutput = CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements(
                                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                headlongAddressOf(recordBuilder.tokenID()));
            } else if (customFees.isEmpty()) {
                encodedOutput = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements(
                                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                headlongAddressOf(recordBuilder.tokenID()));
            } else {
                encodedOutput = CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements(
                                (long) ResponseCodeEnum.SUCCESS.protoOrdinal(),
                                headlongAddressOf(recordBuilder.tokenID()));
            }
            return gasOnly(successResult(encodedOutput, FIXED_GAS_COST, recordBuilder), status, false);
        }
    }

    private VerificationStrategy verificationStrategyFor(@NonNull final MessageFrame frame) {
        final var legacyActivation = legacyActivationIn(frame);

        // Choose a dispatch verification strategy based on whether the legacy
        // activation address is active (somewhere on the stack)
        return stackIncludesActiveAddress(frame, legacyActivation.besuAddress())
                ? new EitherOrVerificationStrategy(
                        verificationStrategy,
                        new ActiveContractVerificationStrategy(
                                legacyActivation.contractNum(),
                                legacyActivation.pbjAddress(),
                                false,
                                UseTopLevelSigs.NO))
                : verificationStrategy;
    }

    private LegacyActivation legacyActivationIn(@NonNull final MessageFrame frame) {
        final var literal = configOf(frame).getConfigData(ContractsConfig.class).keysLegacyActivations();
        final var contractNum = Long.parseLong(literal.substring(literal.indexOf("[") + 1, literal.indexOf("]")));
        final var pbjAddress = com.hedera.pbj.runtime.io.buffer.Bytes.wrap(asEvmAddress(contractNum));
        return new LegacyActivation(contractNum, pbjAddress, pbjToBesuAddress(pbjAddress));
    }

    // @TODO extract externalizeResult() calls into a single location on a higher level
    private PricedResult externalizeUnsuccessfulResult(ResponseCodeEnum responseCode, long gasRequirement) {
        final var result = gasOnly(FullResult.revertResult(responseCode, gasRequirement), responseCode, false);
        final var contractID = asEvmContractId(Address.fromHexString(HTS_EVM_ADDRESS));
        enhancement
                .systemOperations()
                .externalizeResult(
                        contractFunctionResultFailedFor(FIXED_GAS_COST, responseCode.toString(), contractID),
                        responseCode);
        return result;
    }
}
