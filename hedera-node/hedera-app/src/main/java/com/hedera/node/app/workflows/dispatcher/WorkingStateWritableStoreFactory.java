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
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Factory for all writable stores. It creates new writable stores based on the {@link HederaState}.
 */
@Singleton
public class WorkingStateWritableStoreFactory implements WritableStoreFactory {
    private final WorkingStateAccessor stateAccessor;

    /**
     * Constructor of {@link WorkingStateWritableStoreFactory}
     *
     * @param stateAccessor the {@link WorkingStateAccessor} that all stores are based upon
     * @throws NullPointerException if one of the parameters is {@code null}
     */
    @Inject
    public WorkingStateWritableStoreFactory(@NonNull final WorkingStateAccessor stateAccessor) {
        this.stateAccessor = requireNonNull(stateAccessor);
    }

    /**
     * Get a {@link WritableTopicStore} from the {@link HederaState}
     *
     * @return a new {@link WritableTopicStore}
     */
    @NonNull
    public WritableTopicStore createTopicStore() {
        final var topicStates = stateAccessor.getHederaState().createWritableStates(ConsensusService.NAME);
        return new WritableTopicStore(topicStates);
    }

    /**
     * Get a {@link WritableTokenStore} from the {@link HederaState}
     *
     * @return a new {@link WritableTokenStore}
     */
    @NonNull
    public WritableTokenStore createTokenStore() {
        final var tokenStates = stateAccessor.getHederaState().createWritableStates(TokenService.NAME);
        return new WritableTokenStore(tokenStates);
    }

    @Override
    public WritableTokenRelationStore createTokenRelStore() {
        final var tokenStates = stateAccessor.getHederaState().createWritableStates(TokenService.NAME);
        return new WritableTokenRelationStore(tokenStates);
    }

    @Override
    public WritableAccountStore createAccountStore() {
        final var tokenStates = stateAccessor.getHederaState().createWritableStates(TokenService.NAME);
        return new WritableAccountStore(tokenStates);
    }
}
