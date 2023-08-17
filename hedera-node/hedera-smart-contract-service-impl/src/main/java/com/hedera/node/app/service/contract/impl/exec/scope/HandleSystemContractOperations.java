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

package com.hedera.node.app.service.contract.impl.exec.scope;

import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenRelationship;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.utils.SystemContractUtils.ResultStatus;
import com.hedera.node.app.spi.workflows.HandleContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import javax.inject.Inject;

/**
 * Provides the "extended" scope a Hedera system contract needs to perform its operations.
 *
 * <p>This lets an EVM smart contract make atomic changes scoped to a message frame, even though
 * these changes involve state that it cannot mutate directly via the {@code ContractService}'s
 * {@code WritableStates}.
 */
@TransactionScope
public class HandleSystemContractOperations implements SystemContractOperations {
    private final HandleContext context;

    @Inject
    public HandleSystemContractOperations(@NonNull final HandleContext context) {
        this.context = Objects.requireNonNull(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Nft getNftAndExternalizeResult(
            @NonNull final NftID id,
            final long callingContractNumber,
            @NonNull final ResultTranslator<Nft> translator) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Token getTokenAndExternalizeResult(
            final long number, final long callingContractNumber, @NonNull final ResultTranslator<Token> translator) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Account getAccountAndExternalizeResult(
            final long number, final long callingContractNumber, @NonNull final ResultTranslator<Account> translator) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable TokenRelationship getRelationshipAndExternalizeResult(
            final long accountNumber,
            final long tokenNumber,
            final long callingContractNumber,
            @NonNull final ResultTranslator<TokenRelationship> translator) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull ResponseCodeEnum dispatch(
            @NonNull final TransactionBody syntheticTransaction, @NonNull final VerificationStrategy strategy) {
        throw new AssertionError("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void externalizeResult(@NonNull final ContractFunctionResult result, final ResultStatus status) {
        final var childRecordBuilder = context.addChildRecordBuilder(ContractCallRecordBuilder.class);
        childRecordBuilder
                .contractID(result.contractID())
                .status(status == ResultStatus.IS_ERROR ? FAIL_INVALID : SUCCESS)
                .contractCallResult(result);
    }
}
