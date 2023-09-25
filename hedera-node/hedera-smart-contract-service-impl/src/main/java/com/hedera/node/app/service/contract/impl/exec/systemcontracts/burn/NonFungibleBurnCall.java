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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.burn;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.TokenBurnRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.List;

public class NonFungibleBurnCall extends AbstractHtsCall implements BurnCall {

    private final List<Long> serialNo;
    private final TokenID tokenId;
    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategy verificationStrategy;
    private final org.hyperledger.besu.datatypes.Address spender;

    public NonFungibleBurnCall(
            final List<Long> serialNo,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final TokenID tokenId,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(enhancement);
        this.tokenId = requireNonNull(tokenId);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.spender = requireNonNull(spender);
        this.addressIdConverter = requireNonNull(addressIdConverter);
        this.serialNo = serialNo;
    }

    @Override
    public @NonNull PricedResult execute() {
        if (tokenId == null) {
            return reversionWith(INVALID_TOKEN_ID, 0L);
        }
        final var spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        final var recordBuilder = systemContractOperations()
                .dispatch(syntheticBurnNonFungible(), verificationStrategy, spenderId, TokenBurnRecordBuilder.class);
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), 0L));
        } else {
            final var encodedOutput = BurnTranslator.BURN_TOKEN_V1
                    .getOutputs()
                    .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
            return gasOnly(successResult(encodedOutput, recordBuilder.getNewTotalSupply()));
        }
    }

    private TransactionBody syntheticBurnNonFungible() {
        return TransactionBody.newBuilder()
                .tokenBurn(TokenBurnTransactionBody.newBuilder()
                        .token(tokenId)
                        .serialNumbers(serialNo)
                        .build())
                .build();
    }
}
