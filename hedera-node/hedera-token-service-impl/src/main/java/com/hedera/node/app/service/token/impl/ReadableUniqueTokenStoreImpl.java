/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.ReadableUniqueTokenStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Default implementation of {@link ReadableTokenStore}.
 */
public class ReadableUniqueTokenStoreImpl implements ReadableUniqueTokenStore {
    /** The underlying data storage class that holds the token data. */
    private final ReadableKVState<UniqueTokenId, Nft> nftState;

    /**
     * Create a new {@link ReadableUniqueTokenStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableUniqueTokenStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
        this.nftState = states.get(TokenServiceImpl.NFTS_KEY);
    }

    @Override
    @Nullable
    public Nft get(@NonNull final TokenID id, final long serialNumber) {
        requireNonNull(id);
        final var tokenId = UniqueTokenId.newBuilder()
                .tokenTypeNumber(id.tokenNum())
                .serialNumber(serialNumber)
                .build();
        return getNftLeaf(tokenId);
    }

    @Override
    @Nullable
    public Nft get(@NonNull final UniqueTokenId id) {
        requireNonNull(id);
        return getNftLeaf(id);
    }

    /**
     * Returns the NFT leaf for the given nftId. If the token and its serial doesn't exist returns {@code
     * null}
     *
     * @param nftId given token Id and its serial number
     * @return Nft leaf for the given unique token Id
     */
    private Nft getNftLeaf(final UniqueTokenId nftId) {
        return nftState.get(nftId);
    }
}
