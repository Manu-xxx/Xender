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
package com.hedera.node.app.workflows.prehandle;

import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.annotation.Nonnull;

/**
 * A {@code PreHandleDispatcher} takes a validated transaction and dispatches it to the correct
 * handler
 */
public interface PreHandleDispatcher {

    /**
     * Dispatch a request. It is forwarded to the correct handler, which takes care of the specific
     * functionality
     *
     * @param transactionBody the {@link TransactionBody} of the request
     * @throws NullPointerException if {@code transactionBody} is {@code null}
     */
    @Nonnull
    TransactionMetadata dispatch(@Nonnull TransactionBody transactionBody);
}
