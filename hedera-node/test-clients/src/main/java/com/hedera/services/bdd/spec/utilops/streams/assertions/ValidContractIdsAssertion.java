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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;

public class ValidContractIdsAssertion implements RecordStreamAssertion {
    @Override
    public boolean isApplicableToSidecar(TransactionSidecarRecord sidecar) {
        return true;
    }

    @Override
    public boolean testSidecar(TransactionSidecarRecord sidecar) throws AssertionError {
        switch (sidecar.getSidecarRecordsCase()) {
            case STATE_CHANGES -> validateStateChangeIds(sidecar);
            case ACTIONS -> validateActionIds(sidecar);
            case BYTECODE -> validateBytecodeIds(sidecar);
            case SIDECARRECORDS_NOT_SET -> {
                // No-op
            }
        }
        // This validator never officially passes until the end of the test (i.e., it
        // should run on every sidecar record)
        return false;
    }

    private void validateStateChangeIds(@NonNull final TransactionSidecarRecord sidecar) {
        final var stateChanges = sidecar.getStateChanges().getContractStateChangesList();
        for (final var change : stateChanges) {
            if (change.hasContractId()) {
                assertValid(change.getContractId(), "stateChange#contractId", sidecar);
            }
        }
    }

    private void validateActionIds(@NonNull final TransactionSidecarRecord sidecar) {
        final var actions = sidecar.getActions().getContractActionsList();
        for (final var action : actions) {
            if (action.hasCallingAccount()) {
                assertValid(action.getCallingAccount(), "action#callingAccount", sidecar);
            } else if (action.hasCallingContract()) {
                assertValid(action.getCallingContract(), "action#callingContract", sidecar);
            }

            if (action.hasRecipientAccount()) {
                assertValid(action.getRecipientAccount(), "action#recipientAccount", sidecar);
            } else if (action.hasRecipientContract()) {
                assertValid(action.getRecipientContract(), "action#recipientContract", sidecar);
            }
        }
    }

    private void validateBytecodeIds(@NonNull final TransactionSidecarRecord sidecar) {
        final var bytecode = sidecar.getBytecode();
        assertValid(bytecode.getContractId(), "bytecode#contractId", sidecar);
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    private void assertValid(
            @NonNull final ContractID id,
            @NonNull final String label,
            @NonNull final TransactionSidecarRecord sidecar) {
        assertValid(id.getShardNum(), id.getRealmNum(), id.getContractNum(), "Contract", label, sidecar);
    }

    private void assertValid(
            @NonNull final AccountID id, @NonNull final String label, @NonNull final TransactionSidecarRecord sidecar) {
        assertValid(id.getShardNum(), id.getRealmNum(), id.getAccountNum(), "Account", label, sidecar);
    }

    private void assertValid(
            final long shardNum,
            final long realmNum,
            final long entityNum,
            @NonNull final String type,
            @NonNull final String label,
            @NonNull final TransactionSidecarRecord sidecar) {
        if (!isValid(shardNum, realmNum, entityNum)) {
            throw new AssertionError(type + " id (from "
                    + label + " field) "
                    + String.format("%d.%d.%d", shardNum, realmNum, entityNum)
                    + " is not valid in sidecar record " + sidecar);
        }
    }

    private boolean isValid(long shard, long realm, long num) {
        return shard == 0L && realm == 0L && num >= 1;
    }
}
