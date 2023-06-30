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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjLogsFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.contract.ContractLoginfo;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.log.Log;

public record HederaEvmTransactionResult(
        long gasUsed,
        long gasPrice,
        @Nullable ContractID recipientId,
        @Nullable ContractID recipientEvmAddress,
        @NonNull Bytes output,
        @Nullable String haltReason,
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
        return new HederaEvmTransactionResult(
                0, 0, null, null, Bytes.EMPTY, null, reason, null, Collections.emptyList());
    }

    /**
     * Create a result for a transaction that succeeded.
     *
     * @param gasUsed the gas used by the transaction
     * @return the result
     */
    public static HederaEvmTransactionResult successFrom(
            final long gasUsed,
            @NonNull final ContractID recipientId,
            @NonNull final ContractID recipientEvmAddress,
            @NonNull final MessageFrame frame) {
        requireNonNull(frame);
        return successFrom(
                gasUsed, frame.getGasPrice(), recipientId, recipientEvmAddress, frame.getOutputData(), frame.getLogs());
    }

    public static HederaEvmTransactionResult successFrom(
            final long gasUsed,
            @NonNull final Wei gasPrice,
            @NonNull final ContractID recipientId,
            @NonNull final ContractID recipientEvmAddress,
            @NonNull final org.apache.tuweni.bytes.Bytes output,
            @NonNull final List<Log> logs) {
        return new HederaEvmTransactionResult(
                gasUsed,
                requireNonNull(gasPrice).toLong(),
                requireNonNull(recipientId),
                requireNonNull(recipientEvmAddress),
                tuweniToPbjBytes(requireNonNull(output)),
                null,
                null,
                null,
                pbjLogsFrom(requireNonNull(logs)));
    }

    /**
     * Create a result for a transaction that failed.
     *
     * @param gasUsed the gas used by the transaction
     * @return the result
     */
    public static HederaEvmTransactionResult failureFrom(final long gasUsed, @NonNull final MessageFrame frame) {
        requireNonNull(frame);

        return new HederaEvmTransactionResult(
                gasUsed,
                frame.getGasPrice().toLong(),
                null,
                null,
                Bytes.EMPTY,
                frame.getExceptionalHaltReason().map(Object::toString).orElse(null),
                null,
                frame.getRevertReason().map(ConversionUtils::tuweniToPbjBytes).orElse(null),
                Collections.emptyList());
    }

    public boolean isSuccess() {
        return abortReason == null && revertReason == null && haltReason == null;
    }
}
