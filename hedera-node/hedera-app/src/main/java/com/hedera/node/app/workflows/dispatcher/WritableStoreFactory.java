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

package com.hedera.node.app.workflows.dispatcher;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;

/**
 * Factory for all writable stores. It creates new writable stores based on the {@link HederaState}.
 */
public class WritableStoreFactory {
    private final HederaState state;

    /**
     * Constructor of {@link WritableStoreFactory}
     *
     * @param hederaState the {@link HederaState} that all stores are based upon
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    @Inject
    public WritableStoreFactory(@NonNull final HederaState hederaState) {
        this.state = requireNonNull(hederaState);
    }

    /**
     * Get a {@link WritableTopicStore} from the {@link HederaState}
     *
     * @return a new {@link WritableTopicStore}
     */
    @NonNull
    public WritableTopicStore createTopicStore() {
        final var topicStates = state.createWritableStates(ConsensusService.NAME);
        return new WritableTopicStore(topicStates);
    }
}
