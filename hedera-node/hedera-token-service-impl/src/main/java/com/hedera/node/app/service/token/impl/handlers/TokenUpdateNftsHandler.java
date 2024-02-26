/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.node.app.hapi.fees.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenUpdateNftsTransactionBody;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.validators.TokenUpdateNftValidator;
import com.hedera.node.app.service.token.records.TokenUpdateNftRecordBuilder;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides the state transition for an NFT collection update.
 */
@Singleton
public class TokenUpdateNftsHandler implements TransactionHandler {

    private final TokenUpdateNftValidator tokenUpdateNftValidator;

    @Inject
    public TokenUpdateNftsHandler(@NonNull final TokenUpdateNftValidator tokenUpdateNftValidator) {
        this.tokenUpdateNftValidator = tokenUpdateNftValidator;
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.tokenUpdateNftsOrThrow();
        validateTruePreCheck(op.hasToken(), INVALID_TOKEN_ID);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().tokenUpdateNftsOrThrow();
        pureChecks(context.body());
        final var tokenId = op.tokenOrThrow();
        final var tokenStore = context.createStore(ReadableTokenStore.class);
        final var tokenMetadata = tokenStore.getTokenMeta(tokenId);
        if (tokenMetadata == null) throw new PreCheckException(INVALID_TOKEN_ID);
    }

    @Override
    public void handle(@NonNull HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var tokenUpdateNftsTransactionBody = txn.tokenUpdateNftsOrThrow();
        final var tokenNftId = tokenUpdateNftsTransactionBody.tokenOrThrow();

        final var validationResult = tokenUpdateNftValidator.validateSemantics(context, tokenUpdateNftsTransactionBody);
        final var token = validationResult.token();
        final var nftStore = context.writableStore(WritableNftStore.class);

        // Wrap in Set to de-duplicate serial numbers
        final var nftSerialNums = new ArrayList<>(new LinkedHashSet<>(tokenUpdateNftsTransactionBody.serialNumbers()));
        var nftCount = nftSerialNums.size();
        validateTrue(nftCount <= nftStore.sizeOfState(), INVALID_TRANSACTION_BODY);
        validateTrue(!nftSerialNums.isEmpty(), INVALID_TRANSACTION_BODY);
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);
        final var nftsAreEnabled = tokensConfig.nftsAreEnabled();
        if (nftCount > 1) {
            validateTrue(nftsAreEnabled, NOT_SUPPORTED);
        }
        updateNftMetadata(nftSerialNums, nftStore, tokenNftId, tokenUpdateNftsTransactionBody);
        final var recordBuilder = context.recordBuilder(TokenUpdateNftRecordBuilder.class);
        recordBuilder.tokenType(token.tokenType());
    }

    private void updateNftMetadata(
            ArrayList<Long> nftSerialNums,
            WritableNftStore nftStore,
            TokenID tokenNftId,
            TokenUpdateNftsTransactionBody tokenUpdateNftsTransactionBody) {
        // Validate that the list of NFTs provided in txnBody exist in state
        for (final Long nftSerialNumber : nftSerialNums) {
            validateTrue(nftSerialNumber > 0, INVALID_TOKEN_NFT_SERIAL_NUMBER);
            final Nft nft = nftStore.get(tokenNftId, nftSerialNumber);
            validateTrue(nft != null, INVALID_NFT_ID);
            // For now we are only supporting metadata updates to the individual NFTs
            if (tokenUpdateNftsTransactionBody.metadata() != null) {
                var updatedNft =
                        buildUpdatedNft(tokenNftId, tokenUpdateNftsTransactionBody.metadata(), nftSerialNumber);
                // Update the metadata in the listed NFTs
                nftStore.put(updatedNft);
            }
        }
    }

    /**
     * Builds a new non-fungible token.
     * @param tokenId - the token id
     * @param meta - the metadata of the nft
     * @param currentSerialNumber - the current serial number of the nft
     * @return - the newly built nft
     */
    @NonNull
    private Nft buildUpdatedNft(
            @NonNull final TokenID tokenId, @NonNull final Bytes meta, final long currentSerialNumber) {
        return Nft.newBuilder()
                .nftId(NftID.newBuilder()
                        .tokenId(tokenId)
                        .serialNumber(currentSerialNumber)
                        .build())
                .metadata(meta)
                .build();
    }

    /**
     * Updates token metadata if it is present in the token nft update transaction body.
     * @param op token nft update transaction body
     * @param builder token builder
     */
    private void updateMetadata(final TokenUpdateTransactionBody op, final Token.Builder builder) {
        if (op.hasMetadata()) {
            builder.metadata(op.metadata());
        }
    }

    /** The total price should be N * $0.001, where N is the number of NFTs in the transaction body
     * * @param feeContext the {@link FeeContext} with all information needed for the calculation
     * @return the total Fee
     */
    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var op = feeContext.body();
        final var meta = TOKEN_OPS_USAGE_UTILS.tokenUpdateNftUsageFrom(fromPbj(op));
        return feeContext
                .feeCalculator(SubType.TOKEN_NON_FUNGIBLE_UNIQUE)
                .addBytesPerTransaction(meta.getBpt())
                .calculate();
    }
}
