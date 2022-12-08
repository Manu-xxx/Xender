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
package com.hedera.node.app.service.token;

import com.hedera.node.app.spi.*;
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;

/**
 * The {@code CryptoService} is responsible for working with {Account}s. It implements all
 * transactions and queries defined in the "CryptoService" protobuf service. The {@code
 * CryptoService} is used extensively by the core application workflows to implement transaction
 * handling, since all transactions and most queries involve payments and thus the transfer of HBAR
 * from one account to another. A {@link CryptoPreTransactionHandler} contains API for all
 * transactions related to crypto (and token) transfers, as well as some additional API needed by
 * the core application to apply payments and compute rewards.
 *
 * <p>Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/crypto_service.proto">Crypto
 * Service</a>.
 */
public interface CryptoService extends Service {
    /**
     * Creates the crypto service pre-handler given a particular Hedera world state.
     *
     * @param states the state of the world
     * @return the corresponding crypto service pre-handler
     */
    @Override
    @NonNull
    CryptoPreTransactionHandler createPreTransactionHandler(
            @NonNull States states,
            @NonNull PreHandleContext ctx);

    /**
     * Returns the concrete implementation instance of the service
     *
     * @return the implementation instance
     */
    @NonNull
    static CryptoService getInstance() {
        return ServiceFactory.loadService(
                CryptoService.class, ServiceLoader.load(CryptoService.class));
    }

    /**
     * Creates {@link AccountKeyLookup} object for other modules to uset during pre-txn handle
     * phase. NOTE: This class can be modified to return other Account's fields as needed in the
     * future.
     *
     * @param states states to use for looking up accounts
     * @return lookup for account's keys
     */
    @NonNull
    AccountKeyLookup createAccountKeyLookupFor(States states);
}
