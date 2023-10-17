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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;

// @Future remove to revert #9272 after modularization is completed
public class WipeCall extends AbstractHtsCall {

    private final VerificationStrategy verificationStrategy;
    private final TransactionBody transactionBody;
    private final AccountID sender;

    public WipeCall(@NonNull final HtsCallAttempt attempt, @NonNull final TransactionBody transactionBody) {
        super(attempt.enhancement());
        this.transactionBody = transactionBody;
        this.verificationStrategy = attempt.defaultVerificationStrategy();
        this.sender = attempt.addressIdConverter().convertSender(attempt.senderAddress());
    }

    @Override
    public @NonNull PricedResult execute() {
        // TODO - gas calculation
        final var recordBuilder = systemContractOperations()
                .dispatch(transactionBody, verificationStrategy, sender, SingleTransactionRecordBuilder.class);

        final var status = recordBuilder.status();
        final var encodedOutput =
                WipeTranslator.WIPE_FUNGIBLE_V1.getOutputs().encodeElements(BigInteger.valueOf(status.protoOrdinal()));

        if (status != ResponseCodeEnum.SUCCESS) {
            // This checks ensure mono behaviour
            if (status.equals(INVALID_TOKEN_ID)) {
                return gasOnly(successResult(encodedOutput, 0L));
            }
            if (status.equals(INVALID_ACCOUNT_ID)) {
                return gasOnly(successResult(encodedOutput, 0L));
            }
            if (status.equals(INVALID_NFT_ID)) {
                return gasOnly(successResult(encodedOutput, 0L));
            }
            return gasOnly(revertResult(status, 0L));
        } else {
            return gasOnly(successResult(encodedOutput, 0L));
        }
    }
}
