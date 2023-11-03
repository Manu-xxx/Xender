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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.mono.store.contracts.precompile.AbiConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

// @Future remove to revert #9214 after modularization is completed
public class SetApprovalForAllCall extends AbstractHtsCall {

    private final VerificationStrategy verificationStrategy;
    private final TransactionBody transactionBody;
    private final AccountID sender;
    private final DispatchGasCalculator dispatchGasCalculator;
    private final Address token;
    private final Address spender;
    private final boolean approved;

    public SetApprovalForAllCall(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final TransactionBody transactionBody,
            @NonNull final DispatchGasCalculator gasCalculator) {
        super(attempt.systemContractGasCalculator(), attempt.enhancement());
        this.transactionBody = transactionBody;
        this.dispatchGasCalculator = gasCalculator;
        this.verificationStrategy = attempt.defaultVerificationStrategy();
        this.sender = attempt.addressIdConverter().convertSender(attempt.senderAddress());

        final var call = SET_APPROVAL_FOR_ALL.decodeCall(attempt.inputBytes());

        this.token = fromHeadlongAddress(call.get(0));
        this.spender = fromHeadlongAddress(call.get(1));
        this.approved = call.get(2);
    }

    @Override
    public @NonNull PricedResult execute(final MessageFrame frame) {
        final var recordBuilder = systemContractOperations()
                .dispatch(transactionBody, verificationStrategy, sender, SingleTransactionRecordBuilder.class);

        final var gasRequirement =
                dispatchGasCalculator.gasRequirement(transactionBody, gasCalculator, enhancement, sender);

        final var status = recordBuilder.status();
        if (status != ResponseCodeEnum.SUCCESS) {
            // This checks ensure mono behaviour
            if (status.equals(INVALID_ALLOWANCE_SPENDER_ID)) {
                return reversionWith(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, gasRequirement);
            }
            if (status.equals(INVALID_TOKEN_ID)) {
                return completionWith(INVALID_TOKEN_ID, gasRequirement);
            }
            return reversionWith(status, gasRequirement);
        } else {
            frame.addLog(getLogForSetApprovalForAll(token));

            return completionWith(status, gasRequirement);
        }
    }

    private Log getLogForSetApprovalForAll(final Address logger) {
        return EncodingFacade.LogBuilder.logBuilder()
                .forLogger(logger)
                .forEventSignature(AbiConstants.APPROVAL_FOR_ALL_EVENT)
                .forIndexedArgument(asLongZeroAddress(sender.accountNum()))
                .forIndexedArgument(spender)
                .forDataItem(approved)
                .build();
    }
}
