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

package com.hedera.node.app.workflows.query;

import com.hedera.hapi.node.base.ResponseType;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.node.app.components.QueryComponent;
import com.hedera.node.app.fees.FeeAccumulator;
import com.hedera.node.app.fees.MonoFeeAccumulator;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusHandlers;
import com.hedera.node.app.service.contract.impl.components.ContractComponent;
import com.hedera.node.app.service.file.impl.components.FileComponent;
import com.hedera.node.app.service.network.impl.components.NetworkComponent;
import com.hedera.node.app.service.schedule.impl.components.ScheduleComponent;
import com.hedera.node.app.service.token.impl.handlers.TokenHandlers;
import com.hedera.node.app.state.HederaState;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import javax.inject.Singleton;

/**
 * Module for Query processing.
 */
@Module(subcomponents = {QueryComponent.class})
public interface QueryWorkflowModule {
    @Binds
    @Singleton
    QueryWorkflow bindQueryWorkflow(QueryWorkflowImpl queryWorkflow);

    @Binds
    @Singleton
    FeeAccumulator bindFeeAccumulator(MonoFeeAccumulator feeAccumulator);

    @Provides
    @Singleton
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Function<ResponseType, AutoCloseableWrapper<HederaState>> provideStateAccess(
            @NonNull final Platform platform) {
        // Always return the latest immutable state until we support state proofs
        return responseType -> (AutoCloseableWrapper) platform.getLatestImmutableState();
    }

    @Provides
    static QueryHandlers provideQueryHandlers(
            @NonNull final ConsensusHandlers consensusHandlers,
            @NonNull final FileComponent fileComponent,
            @NonNull final NetworkComponent networkComponent,
            @NonNull final ContractComponent contractComponent,
            @NonNull final ScheduleComponent scheduleComponent,
            @NonNull final TokenHandlers tokenHandlers) {
        return new QueryHandlers(
                consensusHandlers.consensusGetTopicInfoHandler(),
                contractComponent.contractGetBySolidityIDHandler(),
                contractComponent.contractCallLocalHandler(),
                contractComponent.contractGetInfoHandler(),
                contractComponent.contractGetBytecodeHandler(),
                contractComponent.contractGetRecordsHandler(),
                tokenHandlers.cryptoGetAccountBalanceHandler(),
                tokenHandlers.cryptoGetAccountInfoHandler(),
                tokenHandlers.cryptoGetAccountRecordsHandler(),
                tokenHandlers.cryptoGetLiveHashHandler(),
                tokenHandlers.cryptoGetStakersHandler(),
                fileComponent.fileGetContentsHandler(),
                fileComponent.fileGetInfoHandler(),
                networkComponent.networkGetAccountDetailsHandler(),
                networkComponent.networkGetByKeyHandler(),
                networkComponent.networkGetExecutionTimeHandler(),
                networkComponent.networkGetVersionInfoHandler(),
                networkComponent.networkTransactionGetReceiptHandler(),
                networkComponent.networkTransactionGetRecordHandler(),
                scheduleComponent.scheduleGetInfoHandler(),
                tokenHandlers.tokenGetInfoHandler(),
                tokenHandlers.tokenGetAccountNftInfosHandler(),
                tokenHandlers.tokenGetNftInfoHandler(),
                tokenHandlers.tokenGetNftInfosHandler());
    }

    @Provides
    static Codec<Query> provideQueryParser() {
        return Query.PROTOBUF;
    }
}
