/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.verification.traceability;

import com.hedera.services.bdd.spec.assertions.matchers.TransactionSidecarRecordMatcher;
import com.hedera.services.stream.proto.TransactionSidecarRecord;

public record MismatchedSidecar(
        TransactionSidecarRecordMatcher expectedSidecarRecord, TransactionSidecarRecord actualSidecarRecord) {

    /**
     * Check if the expected or actual sidecar record has actions.
     * @return {@code true} if either of the records has actions.
     */
    public boolean hasActions() {
        return expectedSidecarRecord.hasActions() || actualSidecarRecord.hasActions();
    }

    /**
     * Check if the expected or actual sidecar record has state changes.
     * @return {@code true} if either of the records has state changes.
     */
    public boolean hasStateChanges() {
        return expectedSidecarRecord.hasStateChanges() || actualSidecarRecord.hasStateChanges();
    }
}
