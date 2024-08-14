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

package com.hedera.node.app.service.token.impl.util;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PENDING_AIRDROP_ID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class that computes the expected results of a pending airdrops removal and then commit the needed changes.
 * This way it will save several state updates of pending airdrop's linked list pointers {@code previousAirdrop()}
 * and {@code nextAirdrop()}, also sender's account updates of {@code headPendingAirdropId()} and
 * {@code numberPendingAirdrops()}
 */
public class PendingAirdropUpdater {
    private static final Logger log = LogManager.getLogger(PendingAirdropUpdater.class);

    @Inject
    public PendingAirdropUpdater() {}

    /**
     * Removes provided pending airdrops from the state.
     * Updates sender accounts ({@code headPendingAirdropId()} and {@code numberPendingAirdrops()}).
     * Update neighbour pending airdrops linked list pointers ({@code previousAirdrop()} and {@code nextAirdrop()}).
     *
     * @param airdropsToRemove list of PendingAirdropId to be removed
     */
    public void removePendingAirdrops(
            @NonNull final List<PendingAirdropId> airdropsToRemove,
            @NonNull final WritableAirdropStore pendingAirdropStore,
            @NonNull final WritableAccountStore accountStore) {
        for (final var id : airdropsToRemove) {
            removePendingAirdrops(id, pendingAirdropStore, accountStore);
        }
    }

    /**
     *  Compute updates needed to be commited, after removing a single pending airdrop.
     *  It populates maps {@code updatedSenders} and {@code updatedAirdrops} with updated entities, ready
     *  to be persisted in the state.
     *
     * <p>
     *  <b>Note:</b> this method don't persist any state changes.
     *
     * @param airdropId pending airdrop to remove
     */
    private void removePendingAirdrops(
            @NonNull final PendingAirdropId airdropId,
            @NonNull final WritableAirdropStore pendingAirdropStore,
            @NonNull final WritableAccountStore accountStore) {
        final var senderId = airdropId.senderIdOrThrow();
        final var airdrop = pendingAirdropStore.getForModify(airdropId);
        validateTrue(airdrop != null, INVALID_PENDING_AIRDROP_ID);

        // update pending airdrops links
        final var prevAirdropId = airdrop.previousAirdrop();
        final var nextAirdropId = airdrop.nextAirdrop();
        if (prevAirdropId != null) {
            final var prevAirdrop = pendingAirdropStore.getForModify(airdropId);
            if (prevAirdrop == null) {
                log.error("Failed to find pending airdrop with id {}", prevAirdropId);
            } else {
                final var updatedAirdrop =
                        prevAirdrop.copyBuilder().nextAirdrop(nextAirdropId).build();
                pendingAirdropStore.put(prevAirdropId, updatedAirdrop);
            }
        }
        if (nextAirdropId != null) {
            final var nextAirdrop = pendingAirdropStore.getForModify(nextAirdropId);
            if (nextAirdrop == null) {
                log.error("Failed to find pending airdrop with id {}", nextAirdropId);
            } else {
                final var updatedAirdrop =
                        nextAirdrop.copyBuilder().previousAirdrop(prevAirdropId).build();
                pendingAirdropStore.put(nextAirdropId, updatedAirdrop);
            }
        }
        var senderAccount = accountStore.getAccountById(senderId);
        final var updatedSender =
                senderAccount.copyBuilder().numberPendingAirdrops(senderAccount.numberPendingAirdrops() - 1);
        if (airdropId.equals(senderAccount.headPendingAirdropId())) {
            updatedSender.headPendingAirdropId(airdrop.nextAirdrop());
        }
        accountStore.put(updatedSender.build());
        pendingAirdropStore.remove(airdropId);
    }
}
