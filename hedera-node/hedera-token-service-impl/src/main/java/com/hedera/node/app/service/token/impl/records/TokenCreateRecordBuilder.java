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

package com.hedera.node.app.service.token.impl.records;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@code RecordBuilder} specialization for tracking the side effects of a {@code TokenCreate}
 * transaction.
 */
public interface TokenCreateRecordBuilder {
    /**
     * Tracks creation of a new token by number. Even if someday we support creating multiple
     * tokens within a smart contract call, we will still only need to track one created token
     * per child record.
     *
     * @param tokenID the {@link AccountID} of the new token
     * @return this builder
     */
    @NonNull
    TokenCreateRecordBuilder tokenID(@NonNull TokenID tokenID);
}
