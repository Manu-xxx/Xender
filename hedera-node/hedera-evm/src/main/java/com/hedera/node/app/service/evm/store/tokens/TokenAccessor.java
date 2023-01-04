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
package com.hedera.node.app.service.evm.store.tokens;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmNftInfo;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmTokenInfo;
import com.hederahashgraph.api.proto.java.TokenInfo;
import java.util.List;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;

public interface TokenAccessor {
    Optional<EvmTokenInfo> evmInfoForToken(final Address tokenId, final byte[] ledgerId);

    Optional<EvmNftInfo> evmNftInfo(final Address target, final byte[] ledgerId);

    boolean isTokenAddress(final Address address);

    boolean isFrozen(final Address accountId, final Address tokenId);

    boolean defaultFreezeStatus(final Address tokenId);

    boolean defaultKycStatus(final Address tokenId);

    boolean isKyc(final Address accountId, final Address tokenId);

    Optional<List<CustomFee>> infoForTokenCustomFees(final Address tokenId);

    TokenType typeOf(final Address tokenId);

    Optional<TokenInfo> infoForToken(final Address tokenId, final byte[] ledgerId);

    // TODO JKey keyOf(final TokenID tokenId, final Supplier<Enum> keyType);

    String nameOf(final Address tokenId);

    String symbolOf(final Address tokenId);

    long totalSupplyOf(final Address tokenId);

    int decimalsOf(final Address tokenId);

    long balanceOf(final Address accountId, final Address tokenId);

    long staticAllowanceOf(final Address ownerId, final Address spenderId, final Address tokenId);

    Address staticApprovedSpenderOf(final Address nftId);

    boolean staticIsOperator(
            final Address ownerId, final Address operatorId, final Address tokenId);

    Address ownerOf(final Address nftId);

    Address canonicalAddress(final Address addressOrAlias);

    String metadataOf(final Address nftId);
}
