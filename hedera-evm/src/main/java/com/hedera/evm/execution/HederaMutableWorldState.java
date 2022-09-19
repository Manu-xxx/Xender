/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.evm.execution;

import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.worldstate.WorldState;
import org.hyperledger.besu.evm.worldstate.WorldView;

/**
 * Hedera adapted interface for a view over the accounts of the world state and methods for
 * persisting state changes
 */
public interface HederaMutableWorldState extends WorldState, WorldView {
    /**
     * Given a the EVM address of a sponsoring account, returns an EVM address appropriate for a new
     * contract.
     *
     * <p><b>Important: </b>Since the new contract will <i>also</i> be a Hedera entity that has a
     * {@code 0.0.X} id, allocating a new contract address must imply reserving a Hedera entity
     * number. Implementations must be able to return their last reserved number on receiving a
     * call.
     *
     * @param sponsor the address of the sponsor of a new contract
     * @return an appropriate EVM address for the new contract
     */
    Address newContractAddress(Address sponsor);

    /**
     * Creates an updater for this mutable world view.
     *
     * @return a new updater for this mutable world view. On commit, change made to this updater
     *     will become visible on this view.
     */
    HederaWorldUpdater updater();
}
