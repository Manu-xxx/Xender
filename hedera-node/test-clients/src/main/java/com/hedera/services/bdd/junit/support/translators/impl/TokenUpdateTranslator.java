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

package com.hedera.services.bdd.junit.support.translators.impl;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.services.bdd.junit.support.translators.BaseTranslator;
import com.hedera.services.bdd.junit.support.translators.BlockTransactionPartsTranslator;
import com.hedera.services.bdd.junit.support.translators.inputs.BlockTransactionParts;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Defines a translator for a token update into a {@link SingleTransactionRecord}.
 */
public class TokenUpdateTranslator implements BlockTransactionPartsTranslator {
    @Override
    public SingleTransactionRecord translate(
            @NonNull final BlockTransactionParts parts,
            @NonNull final BaseTranslator baseTranslator,
            @NonNull final List<StateChange> remainingStateChanges) {
        return baseTranslator.recordFrom(parts, (receiptBuilder, recordBuilder, sidecarRecords, involvedTokenId) -> {
            if (parts.status() == SUCCESS) {
                final var op = parts.body().tokenUpdateOrThrow();
                final var targetId = op.tokenOrThrow();
                final var iter = remainingStateChanges.listIterator();
                while (iter.hasNext()) {
                    final var stateChange = iter.next();
                    if (stateChange.hasMapUpdate()
                            && stateChange.mapUpdateOrThrow().keyOrThrow().hasTokenIdKey()) {
                        final var tokenId =
                                stateChange.mapUpdateOrThrow().keyOrThrow().tokenIdKeyOrThrow();
                        if (tokenId.equals(targetId)) {
                            iter.remove();
                            final var token = stateChange
                                    .mapUpdateOrThrow()
                                    .valueOrThrow()
                                    .tokenValueOrThrow();
                            final var treasuryId = token.treasuryAccountIdOrThrow();
                            if (!baseTranslator.wasAlreadyAssociated(targetId, treasuryId)) {
                                recordBuilder.automaticTokenAssociations(new TokenAssociation(targetId, treasuryId));
                            }
                            return;
                        }
                    }
                }
            }
        });
    }
}
