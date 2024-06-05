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

package com.hedera.node.app.workflows.handle.flow.dispatcher;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.flow.annotations.DispatchScope;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import dagger.BindsInstance;
import dagger.Subcomponent;
import java.util.Set;

@Subcomponent(modules = {ChildDispatchModule.class})
@DispatchScope
public interface ChildDispatchComponent extends Dispatch {
    @Subcomponent.Factory
    interface Factory {
        ChildDispatchComponent create(
                @BindsInstance SingleTransactionRecordBuilderImpl recordBuilder,
                @BindsInstance TransactionInfo txnInfo,
                @BindsInstance ComputeDispatchFeesAsTopLevel computeDispatchFeesAsTopLevel,
                @BindsInstance AccountID syntheticPayer,
                @BindsInstance HandleContext.TransactionCategory childCategory,
                @BindsInstance SavepointStackImpl stack,
                @BindsInstance Set<Key> requiredKeys,
                @BindsInstance Set<Account> hollowAccounts,
                @BindsInstance ResponseCodeEnum userError);
    }
}
