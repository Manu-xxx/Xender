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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;

public record HevmBlockValues(long gasLimit, long blockNo, @NonNull Instant blockTime) implements BlockValues {
    private static final Optional<Wei> ZERO_BASE_FEE = Optional.of(Wei.ZERO);

    public HevmBlockValues {
        requireNonNull(blockTime);
    }

    @Override
    public long getGasLimit() {
        return gasLimit;
    }

    @Override
    public long getTimestamp() {
        return blockTime.getEpochSecond();
    }

    @Override
    public Optional<Wei> getBaseFee() {
        return ZERO_BASE_FEE;
    }

    @Override
    public Bytes getDifficultyBytes() {
        return Bytes.EMPTY;
    }

    @Override
    public long getNumber() {
        return blockNo;
    }
}
