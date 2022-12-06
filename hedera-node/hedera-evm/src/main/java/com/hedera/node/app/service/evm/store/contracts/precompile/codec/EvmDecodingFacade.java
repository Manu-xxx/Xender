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
package com.hedera.node.app.service.evm.store.contracts.precompile.codec;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Tuple;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class EvmDecodingFacade {

    private EvmDecodingFacade() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static final int FUNCTION_SELECTOR_BYTES_LENGTH = 4;

    public static Tuple decodeFunctionCall(
            @NonNull final Bytes input, final Bytes selector, final ABIType<Tuple> decoder) {
        if (!selector.equals(input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH))) {
            throw new IllegalArgumentException(
                    "Selector does not match, expected "
                            + selector
                            + " actual "
                            + input.slice(0, FUNCTION_SELECTOR_BYTES_LENGTH));
        }
        return decoder.decode(input.slice(FUNCTION_SELECTOR_BYTES_LENGTH).toArray());
    }
}
