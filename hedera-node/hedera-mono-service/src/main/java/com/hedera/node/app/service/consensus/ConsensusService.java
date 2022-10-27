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
package com.hedera.node.app.service.consensus;

import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;
import javax.annotation.Nonnull;

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/consensus_service.proto">Consensus
 * Service</a>.
 */
public interface ConsensusService extends Service {
    /**
     * Creates the consensus service pre-handler given a particular Hedera world state.
     *
     * @param states the state of the world
     * @return the corresponding consensus service pre-handler
     */
    @Override
    @Nonnull
    ConsensusPreTransactionHandler createPreTransactionHandler(@Nonnull States states);
}
