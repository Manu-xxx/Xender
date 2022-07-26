/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.bdd.suites.utils.contracts;

import static com.hedera.services.bdd.suites.utils.contracts.FunctionParameters.PrecompileFunction.MINT;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import java.math.BigInteger;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;

// To be extended for the rest of the precompile functions
public class FunctionParameters {
    public static FunctionParameters functionParameters() {
        return new FunctionParameters();
    }

    private static final int ABI_ID_MINT_TOKEN = 0x278e0b88;
    private static final TupleType mintTokenType = TupleType.parse("(address,uint64,bytes[])");

    public enum PrecompileFunction {
        MINT
    }

    private PrecompileFunction precompileFunction;
    private long amount;
    private byte[] tokenAddress;
    private List<String> metadata;

    private FunctionParameters() {}

    public FunctionParameters forFunction(final PrecompileFunction precompileFunction) {
        this.precompileFunction = precompileFunction;
        return this;
    }

    public FunctionParameters withTokenAddress(final byte[] tokenAddress) {
        this.tokenAddress = tokenAddress;
        return this;
    }

    public FunctionParameters withAmount(final long amount) {
        this.amount = amount;
        return this;
    }

    public FunctionParameters withMetadata(final List<String> metadata) {
        this.metadata = metadata;
        return this;
    }

    public Bytes build() {
        var functionHash = Bytes.EMPTY;
        var functionParams = Bytes.EMPTY;

        if (MINT.equals(precompileFunction)) {
            functionHash = Bytes.ofUnsignedInt(ABI_ID_MINT_TOKEN);
            final var result =
                    Tuple.of(
                            convertBesuAddressToHeadlongAddress(tokenAddress),
                            BigInteger.valueOf(amount),
                            metadata.stream().map(String::getBytes).toArray(byte[][]::new));
            functionParams = Bytes.wrap(mintTokenType.encode(result).array());
        }

        return Bytes.concatenate(functionHash, functionParams);
    }

    private Address convertBesuAddressToHeadlongAddress(final byte[] address) {
        return Address.wrap(Address.toChecksumAddress(new BigInteger(address)));
    }
}
