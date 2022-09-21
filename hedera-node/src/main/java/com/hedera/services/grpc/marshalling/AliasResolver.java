/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc.marshalling;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.isSerializedProtoKey;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;

public class AliasResolver {
    private int perceivedMissing = 0;
    private int perceivedCreations = 0;
    private int perceivedInvalidCreations = 0;
    private Map<ByteString, EntityNum> resolutions = new HashMap<>();

    /* ---- temporary token transfer resolutions map containing the token transfers to alias, is needed because a
    token is allowed to be repeated in multiple token transfer lists, but not be repeated in a single token transfer
    list ---- */
    private Map<ByteString, EntityNum> tokenTransferResolutions = new HashMap<>();

    private final GlobalDynamicProperties properties;

    private enum Result {
        KNOWN_ALIAS,
        UNKNOWN_ALIAS,
        REPEATED_UNKNOWN_ALIAS,
        UNKNOWN_EVM_ADDRESS
    }

    @Inject
    public AliasResolver(final GlobalDynamicProperties properties) {
        this.properties = properties;
    }

    public CryptoTransferTransactionBody resolve(
            final CryptoTransferTransactionBody op, final AliasManager aliasManager) {
        final var resolvedOp = CryptoTransferTransactionBody.newBuilder();

        final var resolvedAdjusts = resolveHbarAdjusts(op.getTransfers(), aliasManager);
        resolvedOp.setTransfers(resolvedAdjusts);

        final var resolvedTokenAdjusts =
                resolveTokenAdjusts(op.getTokenTransfersList(), aliasManager);
        resolvedOp.addAllTokenTransfers(resolvedTokenAdjusts);

        return resolvedOp.build();
    }

    public Map<ByteString, EntityNum> resolutions() {
        return resolutions;
    }

    public int perceivedMissingAliases() {
        return perceivedMissing;
    }

    public int perceivedAutoCreations() {
        return perceivedCreations;
    }

    public int perceivedInvalidCreations() {
        return perceivedInvalidCreations;
    }

    public static boolean usesAliases(final CryptoTransferTransactionBody op) {
        for (var adjust : op.getTransfers().getAccountAmountsList()) {
            if (isAlias(adjust.getAccountID())) {
                return true;
            }
        }
        for (var tokenAdjusts : op.getTokenTransfersList()) {
            for (var ownershipChange : tokenAdjusts.getNftTransfersList()) {
                if (isAlias(ownershipChange.getSenderAccountID())
                        || isAlias(ownershipChange.getReceiverAccountID())) {
                    return true;
                }
            }
            for (var tokenAdjust : tokenAdjusts.getTransfersList()) {
                if (isAlias(tokenAdjust.getAccountID())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<TokenTransferList> resolveTokenAdjusts(
            final List<TokenTransferList> opTokenAdjusts, final AliasManager aliasManager) {
        final List<TokenTransferList> resolvedTokenAdjusts = new ArrayList<>();
        for (var tokenAdjust : opTokenAdjusts) {
            final var resolvedTokenAdjust = TokenTransferList.newBuilder();
            tokenTransferResolutions.clear();

            resolvedTokenAdjust.setToken(tokenAdjust.getToken());
            for (final var adjust : tokenAdjust.getTransfersList()) {
                final var result =
                        resolveInternalFungible(
                                aliasManager, adjust, resolvedTokenAdjust::addTransfers, true);

                if (properties.areHTSAutoCreationsEnabled()) {
                    // Since the receiver can be an unknown alias in a CryptoTransfer perceive the
                    // result
                    perceiveResult(result, adjust);
                } else if (result != Result.KNOWN_ALIAS) {
                    perceivedMissing++;
                }
            }

            for (final var change : tokenAdjust.getNftTransfersList()) {
                final var resolvedChange =
                        change.toBuilder().setSerialNumber(change.getSerialNumber());

                final var senderResult =
                        resolveInternal(
                                aliasManager,
                                change.getSenderAccountID(),
                                resolvedChange::setSenderAccountID);
                if (senderResult != Result.KNOWN_ALIAS) {
                    perceivedMissing++;
                }
                final var receiverResult =
                        resolveInternal(
                                aliasManager,
                                change.getReceiverAccountID(),
                                resolvedChange::setReceiverAccountID);

                if (properties.areHTSAutoCreationsEnabled()) {
                    // Since the receiver can be an unknown alias in a CryptoTransfer perceive the
                    // result
                    perceiveNftReceiverResult(receiverResult, change);
                } else if (receiverResult != Result.KNOWN_ALIAS) {
                    perceivedMissing++;
                }

                resolvedTokenAdjust.addNftTransfers(resolvedChange.build());
            }

            resolvedTokenAdjusts.add(resolvedTokenAdjust.build());
        }
        return resolvedTokenAdjusts;
    }

    private TransferList resolveHbarAdjusts(
            final TransferList opAdjusts, final AliasManager aliasManager) {
        final var resolvedAdjusts = TransferList.newBuilder();
        for (var adjust : opAdjusts.getAccountAmountsList()) {
            final var result =
                    resolveInternalFungible(
                            aliasManager, adjust, resolvedAdjusts::addAccountAmounts, false);
            perceiveResult(result, adjust);
        }
        return resolvedAdjusts.build();
    }

    private Result resolveInternal(
            final AliasManager aliasManager,
            final AccountID idOrAlias,
            final Consumer<AccountID> resolvingAction) {
        AccountID resolvedId = idOrAlias;
        var isEvmAddress = false;
        var result = Result.KNOWN_ALIAS;
        if (isAlias(idOrAlias)) {
            final var alias = idOrAlias.getAlias();
            if (alias.size() == EntityIdUtils.EVM_ADDRESS_SIZE) {
                final var evmAddress = alias.toByteArray();
                if (aliasManager.isMirror(evmAddress)) {
                    offerMirrorId(evmAddress, resolvingAction);
                    return Result.KNOWN_ALIAS;
                } else {
                    isEvmAddress = true;
                }
            }
            final var resolution = aliasManager.lookupIdBy(alias);
            if (resolution != MISSING_NUM) {
                resolvedId = resolution.toGrpcAccountId();
            } else {
                result = netOf(isEvmAddress, alias, true);
            }
            resolutions.put(alias, resolution);
        }
        resolvingAction.accept(resolvedId);
        return result;
    }

    private Result resolveInternalFungible(
            final AliasManager aliasManager,
            final AccountAmount adjust,
            final Consumer<AccountAmount> resolvingAction,
            final boolean isForToken) {
        AccountAmount resolvedAdjust = adjust;
        var isEvmAddress = false;
        var result = Result.KNOWN_ALIAS;
        if (isAlias(adjust.getAccountID())) {
            final var alias = adjust.getAccountID().getAlias();
            if (alias.size() == EntityIdUtils.EVM_ADDRESS_SIZE) {
                final var evmAddress = alias.toByteArray();
                if (aliasManager.isMirror(evmAddress)) {
                    offerMirrorId(
                            evmAddress,
                            id ->
                                    resolvingAction.accept(
                                            adjust.toBuilder().setAccountID(id).build()));
                    return Result.KNOWN_ALIAS;
                } else {
                    isEvmAddress = true;
                }
            }
            final var resolution = aliasManager.lookupIdBy(alias);
            if (resolution == MISSING_NUM) {
                if (isForToken) {
                    result = netOf(isEvmAddress, alias, false);
                } else {
                    result = netOf(isEvmAddress, alias, true);
                }
            } else {
                resolvedAdjust =
                        adjust.toBuilder().setAccountID(resolution.toGrpcAccountId()).build();
            }
            resolutions.put(alias, resolution);
            tokenTransferResolutions.put(alias, resolution);
        }
        resolvingAction.accept(resolvedAdjust);
        return result;
    }

    private void perceiveNftReceiverResult(final Result receiverResult, final NftTransfer change) {
        if (receiverResult == Result.UNKNOWN_ALIAS) {
            if (change.getSerialNumber() > 0) {
                final var alias = change.getReceiverAccountID().getAlias();
                if (isSerializedProtoKey(alias)) {
                    perceivedCreations++;
                } else {
                    perceivedInvalidCreations++;
                }
            } else {
                perceivedMissing++;
            }
        }
        if (receiverResult == Result.UNKNOWN_EVM_ADDRESS) {
            perceivedMissing++;
        }
    }

    private void perceiveResult(final Result result, final AccountAmount adjust) {
        if (result == Result.UNKNOWN_ALIAS) {
            if (adjust.getAmount() > 0) {
                final var alias = adjust.getAccountID().getAlias();
                if (isSerializedProtoKey(alias)) {
                    perceivedCreations++;
                } else {
                    perceivedInvalidCreations++;
                }
            } else {
                perceivedMissing++;
            }
        } else if (result == Result.REPEATED_UNKNOWN_ALIAS) {
            perceivedInvalidCreations++;
        } else if (result == Result.UNKNOWN_EVM_ADDRESS) {
            perceivedMissing++;
        }
    }

    private Result netOf(
            final boolean isEvmAddress, final ByteString alias, final boolean isForNftOrHbar) {
        if (isEvmAddress) {
            return Result.UNKNOWN_EVM_ADDRESS;
        } else if (isForNftOrHbar) {
            return resolutions.containsKey(alias)
                    ? Result.REPEATED_UNKNOWN_ALIAS
                    : Result.UNKNOWN_ALIAS;
        } else {
            /* ---- checks if temporary resolutions map has the alias.
            If it has the alias, the alias is repeated in a single token transfer list */
            if (tokenTransferResolutions.containsKey(alias)) {
                return Result.REPEATED_UNKNOWN_ALIAS;
            }
            return resolutions.containsKey(alias) ? Result.KNOWN_ALIAS : Result.UNKNOWN_ALIAS;
        }
    }

    private void offerMirrorId(final byte[] evmAddress, final Consumer<AccountID> resolvingAction) {
        final var contractNum =
                Longs.fromBytes(
                        evmAddress[12],
                        evmAddress[13],
                        evmAddress[14],
                        evmAddress[15],
                        evmAddress[16],
                        evmAddress[17],
                        evmAddress[18],
                        evmAddress[19]);
        resolvingAction.accept(STATIC_PROPERTIES.scopedAccountWith(contractNum));
    }

    /* ---- Only used for tests */
    @VisibleForTesting
    public Map<ByteString, EntityNum> tokenResolutions() {
        return tokenTransferResolutions;
    }
}
