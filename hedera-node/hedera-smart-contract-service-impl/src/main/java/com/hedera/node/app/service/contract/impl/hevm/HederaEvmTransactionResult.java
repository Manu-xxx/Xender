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

package com.hedera.node.app.service.contract.impl.hevm;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractLoginfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;

public record HederaEvmTransactionResult(
        long gasUsed,
        long gasPrice,
        @Nullable Bytes recipient,
        @NonNull Bytes output,
        @Nullable Bytes haltReason,
        @Nullable ResponseCodeEnum abortReason,
        @Nullable Bytes revertReason,
        @NonNull List<ContractLoginfo> logs) {
    public HederaEvmTransactionResult {
        requireNonNull(output);
        requireNonNull(logs);
    }

    /**
     * Create a result for a transaction that was aborted before entering the EVM due to a
     * Hedera-specific reason.
     *
     * @param reason the reason for the abort
     * @return the result
     */
    public static HederaEvmTransactionResult abortFor(@NonNull final ResponseCodeEnum reason) {
        return new HederaEvmTransactionResult(0, 0, null, Bytes.EMPTY, null, reason, null, List.of());
    }
}
