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
package com.hedera.node.app.service.scheduled.impl;

import com.hedera.node.app.service.scheduled.SchedulePreTransactionHandler;
import com.hedera.node.app.service.scheduled.ScheduleService;
import com.hedera.node.app.spi.PreHandleTxnAccessor;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class ScheduleServiceImpl implements ScheduleService {
    private final PreHandleTxnAccessor callContext;
    public ScheduleServiceImpl(){
        this.callContext = null;
    }

    public ScheduleServiceImpl(final PreHandleTxnAccessor callContext) {
        this.callContext = callContext; // How is this constructed ? Not sure if it should be injected here
    }

    @NonNull
    @Override
    public SchedulePreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx) {
        Objects.requireNonNull(states);
        Objects.requireNonNull(ctx);
        return new SchedulePreTransactionHandlerImpl(callContext);
    }
}
