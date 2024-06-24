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

import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.recreateWorkingDir;
import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implementation support for a node that uses a local working directory.
 */
public abstract class AbstractLocalNode<T extends AbstractLocalNode<T>> extends AbstractNode implements HederaNode {
    /**
     * Whether the working directory has been initialized.
     */
    protected boolean workingDirInitialized;

    protected AbstractLocalNode(@NonNull final NodeMetadata metadata) {
        super(metadata);
    }

    @Override
    public T initWorkingDir(@NonNull final String configTxt) {
        recreateWorkingDir(requireNonNull(metadata.workingDir()), configTxt);
        workingDirInitialized = true;
        return self();
    }

    protected void assertWorkingDirInitialized() {
        if (!workingDirInitialized) {
            throw new IllegalStateException("Working directory not initialized");
        }
    }

    protected abstract T self();
}
