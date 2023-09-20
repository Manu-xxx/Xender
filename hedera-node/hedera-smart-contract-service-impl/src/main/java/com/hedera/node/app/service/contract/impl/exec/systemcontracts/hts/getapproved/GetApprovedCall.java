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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.ERC_GET_APPROVED;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator.HAPI_GET_APPROVED;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractTokenViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class GetApprovedCall extends AbstractTokenViewCall {

    private final long serialNo;
    private final boolean isErcCall;

    public GetApprovedCall(
            @NonNull HederaWorldUpdater.Enhancement enhancement,
            @Nullable Token token,
            final long serialNo,
            final boolean isErcCall) {
        super(enhancement, token);
        this.serialNo = serialNo;
        this.isErcCall = isErcCall;
    }

    @Override
    protected @NonNull HederaSystemContract.FullResult resultOfViewingToken(@NonNull Token token) {
        requireNonNull(token);
        // TODO - gas calculation
        if (token.tokenType() != TokenType.NON_FUNGIBLE_UNIQUE) {
            return revertResult(INVALID_TOKEN_ID, 0L);
        }
        var spenderNum = nativeOperations()
                .getNft(token.tokenId().tokenNum(), serialNo)
                .spenderId()
                .accountNumOrThrow();
        var spender = nativeOperations().getAccount(spenderNum);
        return isErcCall
                ? successResult(ERC_GET_APPROVED.getOutputs().encodeElements(headlongAddressOf(spender)), 0L)
                : successResult(
                        HAPI_GET_APPROVED.getOutputs().encodeElements(SUCCESS.getNumber(), headlongAddressOf(spender)),
                        0L);
    }
}
