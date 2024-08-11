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

package com.hedera.node.app.workflows.standalone;

import com.hedera.node.app.config.BootstrapConfigProviderImpl;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.services.AppContextImpl;
import com.hedera.node.app.signature.AppSignatureVerifier;
import com.hedera.node.app.signature.impl.SignatureExpanderImpl;
import com.hedera.node.app.signature.impl.SignatureVerifierImpl;
import com.hedera.node.app.workflows.ExecutorModule;
import com.hedera.node.config.data.HederaConfig;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.InstantSource;
import java.util.Map;

/**
 * A factory for creating {@link TransactionExecutor} instances.
 */
public enum TransactionExecutors {
    TRANSACTION_EXECUTORS;

    /**
     * Creates a new {@link TransactionExecutor} based on the given {@link State} and properties.
     * @param state the {@link State} to create the executor from
     * @param properties the properties to use for the executor
     * @return a new {@link TransactionExecutor}
     */
    public TransactionExecutor newExecutor(@NonNull final State state, @NonNull final Map<String, String> properties) {
        final var executor = DaggerExecutorComponent.builder()
                .executorModule(newExecutorModule(properties))
                .metrics(new NoOpMetrics())
                .build();
        executor.initializer().accept(state);
        executor.stateNetworkInfo().initFrom(state);
        return (transactionBody, consensusNow, operationTracers) -> {
            final var dispatch = executor.standaloneDispatchFactory().newDispatch(state, transactionBody, consensusNow);
            executor.dispatchProcessor().processDispatch(dispatch);
            return dispatch.stack().buildStreamItems(consensusNow);
        };
    }

    private ExecutorModule newExecutorModule(@NonNull final Map<String, String> properties) {
        final var bootstrapConfig = new BootstrapConfigProviderImpl().getConfiguration();
        final var appContext = new AppContextImpl(
                InstantSource.system(),
                new AppSignatureVerifier(
                        bootstrapConfig.getConfigData(HederaConfig.class),
                        new SignatureExpanderImpl(),
                        new SignatureVerifierImpl(CryptographyHolder.get())));
        return new ExecutorModule(
                new FileServiceImpl(),
                new ContractServiceImpl(appContext),
                new ConfigProviderImpl(false, null, properties),
                new BootstrapConfigProviderImpl());
    }
}
