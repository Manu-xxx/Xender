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

package com.hedera.node.app.workflows.handle.flow.modules;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.fees.FeeAccumulatorImpl;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.dispatcher.ServiceApiFactory;
import com.hedera.node.app.workflows.handle.flow.FlowHandleContext;
import com.hedera.node.app.workflows.handle.flow.annotations.HandleContextScope;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

@Module
public interface DispatchModule {
    @Binds
    @HandleContextScope
    HandleContext bindHandleContext(FlowHandleContext context);

    @Provides
    @HandleContextScope
    static AccountID topLevelPayer(AccountID payer) {
        return payer;
    }

    @Provides
    @HandleContextScope
    static FeeAccumulator provideFeeAccumulator(ServiceApiFactory factory,
                                                SingleTransactionRecordBuilderImpl recordBuilder) {
        return new FeeAccumulatorImpl(factory.getApi(TokenServiceApi.class), recordBuilder);
    }
}
