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

package com.hedera.node.app.service.mono.state.migration;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.service.mono.state.merkle.internals.BitPackUtils;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.NftNumPair;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jetbrains.annotations.NotNull;

public final class NftStateTranslator {

    @NonNull
    /**
     * Converts a {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken} and to {@link Nft}.
     * @param merkleUniqueToken the {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken}
     * @return the {@link Nft} converted from the {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken}
     */
    public static Nft nftFromMerkleUniqueToken(
            @NonNull final com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken merkleUniqueToken) {
        requireNonNull(merkleUniqueToken);
        var builder = Nft.newBuilder();
        final var nftIdPair = merkleUniqueToken.getKey().asNftNumPair();
        builder.id(merkelUniqueTokenToUniqueTokenId(nftIdPair));

        builder.ownerId(AccountID.newBuilder()
                        .accountNum(
                                merkleUniqueToken.getOwner().toGrpcAccountId().getAccountNum())
                        .build())
                .spenderId(AccountID.newBuilder()
                        .accountNum(
                                merkleUniqueToken.getSpender().toGrpcAccountId().getAccountNum())
                        .build())
                .mintTime(Timestamp.newBuilder()
                        .seconds(merkleUniqueToken.getCreationTime().getSeconds())
                        .nanos(merkleUniqueToken.getCreationTime().getNanos())
                        .build())
                .metadata(Bytes.wrap(merkleUniqueToken.getMetadata()));

        final var nftPrevIdPair = merkleUniqueToken.getPrev();
        builder.ownerPreviousNftId(merkelUniqueTokenToUniqueTokenId(nftPrevIdPair));

        final var nftNextIdPair = merkleUniqueToken.getNext();
        builder.ownerNextNftId(merkelUniqueTokenToUniqueTokenId(nftNextIdPair));

        return builder.build();
    }

    private static @NonNull UniqueTokenId merkelUniqueTokenToUniqueTokenId(@NotNull NftNumPair merkleUniqueToken) {
        final var tokenTypeNumber = merkleUniqueToken.tokenNum();
        final var serialNumber = merkleUniqueToken.serialNum();
        return UniqueTokenId.newBuilder()
                .tokenId(TokenID.newBuilder().tokenNum(tokenTypeNumber).build())
                .serialNumber(serialNumber)
                .build();
    }

    @NonNull
    /**
     * Converts a {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken} to a {@link Nft}.
     *  @param tokenID the {@link UniqueTokenId}
     *  @param tokenID the {@link ReadableNftStore}
     *
     *
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken merkleUniqueTokenFromNft(
            @NonNull UniqueTokenId tokenID, @NonNull ReadableNftStore readableNftStore) {
        requireNonNull(tokenID);
        requireNonNull(readableNftStore);
        final var optionalNFT = readableNftStore.get(tokenID);
        if (optionalNFT == null) {
            throw new IllegalArgumentException("Token not found");
        }
        return merkleUniqueTokenFromNft(optionalNFT);
    }

    @NonNull
    /***
     * Converts a {@link Nft} to a {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken}.
     * @param nft the {@link Nft} to convert
     * @return the {@link com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken}
     */
    public static com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken merkleUniqueTokenFromNft(
            @NonNull Nft nft) {
        requireNonNull(nft);
        com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken merkleUniqueToken =
                new com.hedera.node.app.service.mono.state.merkle.MerkleUniqueToken();

        if (nft.hasId()) {
            merkleUniqueToken.setKey(EntityNumPair.fromNums(
                    EntityNum.fromLong(nft.id().tokenId().tokenNum()),
                    EntityNum.fromLong(nft.id().serialNumber())));
        }
        merkleUniqueToken.setOwner(EntityId.fromNum(nft.ownerId().accountNum()));
        merkleUniqueToken.setSpender(EntityId.fromNum(nft.spenderId().accountNum()));
        merkleUniqueToken.setPackedCreationTime(
                BitPackUtils.packedTime(nft.mintTime().seconds(), nft.mintTime().nanos()));
        merkleUniqueToken.setMetadata(nft.metadata().toByteArray());

        if (nft.hasOwnerPreviousNftId()) {
            merkleUniqueToken.setPrev(new NftNumPair(
                    nft.ownerPreviousNftId().tokenId().tokenNum(),
                    nft.ownerPreviousNftId().serialNumber()));
        }

        if (nft.hasOwnerNextNftId()) {
            merkleUniqueToken.setNext(new NftNumPair(
                    nft.ownerNextNftId().tokenId().tokenNum(),
                    nft.ownerNextNftId().serialNumber()));
        }

        return merkleUniqueToken;
    }
}
