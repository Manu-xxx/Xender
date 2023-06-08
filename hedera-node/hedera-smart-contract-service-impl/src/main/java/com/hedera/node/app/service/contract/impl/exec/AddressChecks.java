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

package com.hedera.node.app.service.contract.impl.exec;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.processor.AbstractMessageProcessor;

/**
 * Provides address checks used to customize behavior of Hedera {@link org.hyperledger.besu.evm.operation.Operation}
 * or {@link AbstractMessageProcessor} overrides. (The main way the Hedera EVM customizes Besu is by giving special
 * treatment to missing and/or system addresses.)
 */
public interface AddressChecks {
    /**
     * Returns {@code true} if the given address is present in the given frame. This could mean the address is
     * a precompile; or a user-space account whose priority address is the given address; or a token "address"
     * whose code will redirect ERC-20/ERC-721 token operations to the {@code 0x167} system contract.
     *
     * @param address the address to check
     * @param frame the frame to check
     * @return whether the address is present in the frame
     */
    boolean isPresent(@NonNull Address address, @NonNull MessageFrame frame);

    /**
     * Returns {@code true} if the given address is a system address (i.e. a long-zero address for an entity
     * id up to {@code 0.0.750}).
     *
     * @param address the address to check
     * @return whether the address is a system address
     */
    boolean isSystemAccount(@NonNull Address address);

    /**
     * Returns {@code true} if the given address is a non-user account (i.e. a long-zero address for an entity
     * id up to {@code 0.0.1000}).
     *
     * @param address
     * @return
     */
    boolean isNonUserAccount(@NonNull Address address);

    /**
     * A convenience method to check if the given address is completely missing from the given frame. (I.e.,
     * neither a system account nor present in the sense of {@link AddressChecks#isPresent(Address, MessageFrame)}.)
     *
     * @param address the address to check
     * @param frame the frame to check
     * @return whether the address is missing from the frame
     */
    default boolean isNeitherSystemNorPresent(@NonNull Address address, @NonNull MessageFrame frame) {
        return !isSystemAccount(address) && !isPresent(address, frame);
    }

    /**
     *
     * @param address
     * @param frame
     * @return
     */
    default boolean isNeitherNonUserNorPresent(@NonNull Address address, @NonNull MessageFrame frame) {
        return !isNonUserAccount(address) && !isPresent(address, frame);
    }

    /**
     * Returns {@code true} if the given address is a Hedera precompile.
     *
     * @param address the address to check
     * @return whether the address is a Hedera precompile
     */
    boolean isHederaPrecompile(@NonNull Address address);
}
