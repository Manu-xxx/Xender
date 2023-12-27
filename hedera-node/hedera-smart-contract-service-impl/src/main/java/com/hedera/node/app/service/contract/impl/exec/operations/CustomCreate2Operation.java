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

package com.hedera.node.app.service.contract.impl.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.operations.CustomizedOpcodes.CREATE2;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class CustomCreate2Operation extends AbstractCustomCreateOperation {
    private static final Bytes EIP_1014_PREFIX = Bytes.fromHexString("0xFF");
    private final FeatureFlags featureFlags;

    public CustomCreate2Operation(
            @NonNull final GasCalculator gasCalculator, @NonNull final FeatureFlags featureFlags) {
        super(CREATE2.opcode(), "ħCREATE2", 4, 1, gasCalculator);
        this.featureFlags = featureFlags;
    }

    @Override
    protected boolean isEnabled(@NonNull final MessageFrame frame) {
        return featureFlags.isCreate2Enabled(frame);
    }

    @Override
    protected long cost(@NonNull final MessageFrame frame) {
        return gasCalculator().create2OperationGasCost(frame);
    }

    @Override
    protected @Nullable Address setupPendingCreation(@NonNull final MessageFrame frame) {
        final var alias = eip1014AddressFor(frame);
        final var updater = (ProxyWorldUpdater) frame.getWorldUpdater();
        // A bit arbitrary maybe, but if implicit creation isn't enabled, we also
        // don't support finalizing hollow accounts as contracts, so return null
        if (updater.isHollowAccount(alias) && !featureFlags.isImplicitCreationEnabled(frame)) {
            return null;
        }
        updater.setupInternalAliasedCreate(frame.getRecipientAddress(), alias);
        frame.warmUpAddress(alias);
        return alias;
    }

    @Override
    protected void onSuccess(@NonNull final MessageFrame frame, @NonNull final Address creation) {
        final var updater = (ProxyWorldUpdater) frame.getWorldUpdater();
        if (updater.isHollowAccount(creation)) {
            // The hollow account will inherit the contract-specific Hedera properties of its creator
            updater.finalizeHollowAccount(creation, frame.getRecipientAddress());
        }
    }

    private Address eip1014AddressFor(@NonNull final MessageFrame frame) {
        // If the recipient has an EIP-1014 address, it must have been used here
        final var creatorAddress = frame.getRecipientAddress();
        final var offset = clampedToLong(frame.getStackItem(1));
        final var length = clampedToLong(frame.getStackItem(2));
        final Bytes32 salt = UInt256.fromBytes(frame.getStackItem(3));
        final var initCode = frame.readMutableMemory(offset, length);
        final var hash = keccak256(Bytes.concatenate(EIP_1014_PREFIX, creatorAddress, salt, keccak256(initCode)));
        return Address.wrap(hash.slice(12, 20));
    }

    private Bytes32 keccak256(final Bytes input) {
        return Bytes32.wrap(keccak256DigestOf(input.toArrayUnsafe()));
    }

    private static byte[] keccak256DigestOf(final byte[] msg) {
        return new Keccak.Digest256().digest(msg);
    }
}
