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

package com.hedera.services.bdd.junit.hedera;

import static com.hedera.services.bdd.junit.hedera.live.ProcessUtils.OVERRIDE_RECORD_STREAM_FOLDER;

import com.hedera.hapi.node.base.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;

public abstract class AbstractNode implements HederaNode {
    protected final NodeMetadata metadata;

    protected AbstractNode(@NonNull final NodeMetadata metadata) {
        this.metadata = metadata;
    }

    @Override
    public int getPort() {
        return metadata.grpcPort();
    }

    @Override
    public long getNodeId() {
        return metadata.nodeId();
    }

    @Override
    public String getName() {
        return metadata.name();
    }

    @Override
    public AccountID getAccountId() {
        return metadata.accountId();
    }

    @Override
    public Path getRecordStreamPath() {
        return metadata.workingDir()
                .resolve(OVERRIDE_RECORD_STREAM_FOLDER)
                .resolve("record0.0." + getAccountId().accountNumOrThrow());
    }
}
