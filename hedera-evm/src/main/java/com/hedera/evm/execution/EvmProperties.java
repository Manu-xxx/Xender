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
package com.hedera.evm.execution;

import com.hedera.services.stream.proto.SidecarType;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;

public interface EvmProperties {

    public Address fundingAccount();

    public Set<SidecarType> enabledSidecars();

    public Bytes32 chainIdBytes32();

    public int maxGasRefundPercentage();

    boolean areNftsEnabled();

    int maxBatchSizeWipe();
}
