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
package com.hedera.node.app.workflows.ingest;

import com.hedera.node.app.SessionContext;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import java.nio.ByteBuffer;
import javax.annotation.Nonnull;

/**
 * An implementation of the ingestion pipeline. An implementation of this interface is threadsafe, a
 * single instance of it can be used to execute concurrent transaction ingestion.
 */
public interface IngestWorkflow {
    /**
     * Called to handle a single transaction during the ingestion flow. The call terminates in a
     * {@link TransactionResponse} being returned to the client (for both successful and
     * unsuccessful calls). There are no unhandled exceptions (even Throwable is handled).
     *
     * @param session The per-request {@link SessionContext}.
     * @param requestBuffer The raw protobuf transaction bytes. Must be a transaction object.
     * @param responseBuffer The raw protobuf response bytes.
     */
    void handleTransaction(
            @Nonnull SessionContext session,
            @Nonnull ByteBuffer requestBuffer,
            @Nonnull ByteBuffer responseBuffer);
}
