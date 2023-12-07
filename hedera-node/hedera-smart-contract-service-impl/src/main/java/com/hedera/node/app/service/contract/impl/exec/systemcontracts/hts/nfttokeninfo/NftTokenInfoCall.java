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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.nfttokeninfo;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.TokenTupleUtils.nftTokenInfoTupleFor;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.nfttokeninfo.NftTokenInfoTranslator.NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof.OwnerOfCall.getOwnerAccountNum;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.FullResult;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractNonRevertibleTokenViewCall;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.tuweni.bytes.Bytes;

public class NftTokenInfoCall extends AbstractNonRevertibleTokenViewCall {
    private final Configuration configuration;
    private final boolean isStaticCall;
    private final long serialNumber;

    public NftTokenInfoCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            final boolean isStaticCall,
            @Nullable final Token token,
            final long serialNumber,
            @NonNull final Configuration configuration) {
        super(gasCalculator, enhancement, token);
        this.configuration = requireNonNull(configuration);
        this.serialNumber = serialNumber;
        this.isStaticCall = isStaticCall;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull FullResult resultOfViewingToken(@NonNull final Token token) {
        requireNonNull(token);
        final var nft = enhancement
                .nativeOperations()
                .getNft(token.tokenIdOrElse(ZERO_TOKEN_ID).tokenNum(), serialNumber);
        final var status = nft != null ? SUCCESS : ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
        return fullResultsFor(status, gasCalculator.viewGasRequirement(), token, nft);
    }

    @Override
    protected @NonNull FullResult viewCallResultWith(
            @NonNull final ResponseCodeEnum status, final long gasRequirement) {
        return fullResultsFor(status, gasRequirement, Token.DEFAULT, null);
    }

    private @NonNull FullResult fullResultsFor(
            @NonNull final ResponseCodeEnum status,
            final long gasRequirement,
            @NonNull final Token token,
            @Nullable final Nft nft) {
        requireNonNull(status);
        requireNonNull(token);

        // @Future remove to revert #9074 after modularization is completed
        if (isStaticCall && (status != SUCCESS || nft == null)) {
            return revertResult(status, gasCalculator.viewGasRequirement());
        }

        final var nonNullNft = nft != null ? nft : Nft.DEFAULT;
        final var ownerAccount = nativeOperations().getAccount(getOwnerAccountNum(nonNullNft, token));
        final var ledgerConfig = configuration.getConfigData(LedgerConfig.class);
        final var ledgerId = Bytes.wrap(ledgerConfig.id().toByteArray()).toString();
        return successResult(
                NON_FUNGIBLE_TOKEN_INFO
                        .getOutputs()
                        .encodeElements(
                                status.protoOrdinal(),
                                nftTokenInfoTupleFor(token, nonNullNft, serialNumber, ledgerId, ownerAccount)),
                gasRequirement);
    }
}
