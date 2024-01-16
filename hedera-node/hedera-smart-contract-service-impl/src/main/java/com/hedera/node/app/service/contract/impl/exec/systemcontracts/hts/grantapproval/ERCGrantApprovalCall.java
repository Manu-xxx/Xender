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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsSystemContract.HTS_EVM_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmContractId;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.priorityAddressOf;
import static com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.contractFunctionResultFailedForProto;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbiConstants;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.LogBuilder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater.Enhancement;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ERCGrantApprovalCall extends AbstractGrantApprovalCall {

    // too many parameters
    @SuppressWarnings("java:S107")
    public ERCGrantApprovalCall(
            @NonNull final Enhancement enhancement,
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final AccountID sender,
            @NonNull final TokenID token,
            @NonNull final AccountID spenderId,
            @NonNull final BigInteger amount,
            @NonNull final TokenType tokenType) {
        super(gasCalculator, enhancement, verificationStrategy, sender, token, spenderId, amount, tokenType, false);
    }

    @NonNull
    @Override
    public PricedResult execute(@NonNull final MessageFrame frame) {
        if (token == null) {
            return reversionWith(INVALID_TOKEN_ID, gasCalculator.canonicalGasRequirement(DispatchType.APPROVE));
        }
        final var spenderNum = spenderId.accountNumOrThrow();
        final var spenderAccount = enhancement.nativeOperations().getAccount(spenderNum);
        final var body = callGrantApproval();
        if (spenderAccount == null && !isNftApprovalRevocation()) {
            final var gasRequirement = gasCalculator.canonicalGasRequirement(DispatchType.APPROVE);
            final var revertResult = FullResult.revertResult(INVALID_ALLOWANCE_SPENDER_ID, gasRequirement);
            final var result = gasOnly(revertResult, INVALID_ALLOWANCE_SPENDER_ID, false);

            final var contractID = asEvmContractId(Address.fromHexString(HTS_EVM_ADDRESS));
            final var encodedRc =
                    ReturnTypes.encodedRc(INVALID_ALLOWANCE_SPENDER_ID).array();
            final var contractFunctionResult = contractFunctionResultFailedForProto(
                    gasRequirement, INVALID_ALLOWANCE_SPENDER_ID.protoName(), contractID, Bytes.wrap(encodedRc));

            enhancement.systemOperations().externalizeResult(contractFunctionResult, INVALID_ALLOWANCE_SPENDER_ID);

            return result;
        }
        final var recordBuilder = systemContractOperations()
                .dispatch(body, verificationStrategy, senderId, ContractCallRecordBuilder.class);
        final var gasRequirement = gasCalculator.gasRequirement(body, DispatchType.APPROVE, senderId);
        final var status = recordBuilder.status();

        // TODO: use GrantApprovalLoggingUtils.logSuccessfulApprove() when
        // https://github.com/hashgraph/hedera-services/pull/10897 is merged
        final var tokenAddress = asLongZeroAddress(token.tokenNum());
        final var accountStore = readableAccountStore();
        final var ownerAddress = priorityAddressOf(requireNonNull(accountStore.getAccountById(senderId)));
        final var spenderAddress = isNftApprovalRevocation()
                ? ZERO_ADDRESS
                : priorityAddressOf(requireNonNull(accountStore.getAccountById(spenderId)));
        frame.addLog(LogBuilder.logBuilder()
                .forLogger(tokenAddress)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(ownerAddress)
                .forIndexedArgument(spenderAddress)
                .build());

        if (status != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(status, gasRequirement), status, false);
        } else {
            final var encodedOutput = tokenType.equals(TokenType.FUNGIBLE_COMMON)
                    ? GrantApprovalTranslator.ERC_GRANT_APPROVAL.getOutputs().encodeElements(true)
                    : GrantApprovalTranslator.ERC_GRANT_APPROVAL_NFT
                            .getOutputs()
                            .encodeElements();
            return gasOnly(successResult(encodedOutput, gasRequirement, recordBuilder), status, false);
        }
    }
}
