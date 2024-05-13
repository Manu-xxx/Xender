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

package com.hedera.services.bdd.suites.contract.hapi;

import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.contract.HapiParserUtil.asHeadlongAddress;
import static java.lang.Integer.MAX_VALUE;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiSuite;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;

@HapiTestSuite(fuzzyMatch = true)
@Tag(SMART_CONTRACT)
public class ContractStateSuite extends HapiSuite {
    private static final String CONTRACT = "StateContract";
    private static final SplittableRandom RANDOM = new SplittableRandom(1_234_567L);
    private static final Logger LOG = LogManager.getLogger(ContractStateSuite.class);

    @Override
    protected Logger getResultsLogger() {
        return LOG;
    }

    @Override
    public List<DynamicTest> getSpecsInSuite() {
        return List.of(stateChangesSpec());
    }

    @HapiTest
    final DynamicTest stateChangesSpec() {
        final var iterations = 2;
        final var integralTypes = Map.ofEntries(
                Map.entry("Uint8", 0x01),
                Map.entry("Uint16", 0x02),
                Map.entry("Uint32", (long) 0x03),
                Map.entry("Uint64", BigInteger.valueOf(4)),
                Map.entry("Uint128", BigInteger.valueOf(5)),
                Map.entry("Uint256", BigInteger.valueOf(6)),
                Map.entry("Int8", 0x01),
                Map.entry("Int16", 0x02),
                Map.entry("Int32", 0x03),
                Map.entry("Int64", 4L),
                Map.entry("Int128", BigInteger.valueOf(5)),
                Map.entry("Int256", BigInteger.valueOf(6)));

        return defaultHapiSpec("stateChangesSpec")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(IntStream.range(0, iterations)
                        .boxed()
                        .flatMap(i -> Stream.of(
                                        Stream.of(contractCall(CONTRACT, "setVarBool", RANDOM.nextBoolean())),
                                        Arrays.stream(integralTypes.keySet().toArray(new String[0]))
                                                .map(type -> contractCall(
                                                        CONTRACT, "setVar" + type, integralTypes.get(type))),
                                        Stream.of(contractCall(CONTRACT, "setVarAddress", randomHeadlongAddress())),
                                        Stream.of(contractCall(CONTRACT, "setVarContractType")),
                                        Stream.of(contractCall(CONTRACT, "setVarBytes32", randomBytes32())),
                                        Stream.of(contractCall(CONTRACT, "setVarString", randomString())),
                                        Stream.of(contractCall(CONTRACT, "setVarEnum", randomEnum())),
                                        randomSetAndDeleteVarInt(),
                                        randomSetAndDeleteString(),
                                        randomSetAndDeleteStruct())
                                .flatMap(s -> s))
                        .toArray(HapiSpecOperation[]::new))
                .then();
    }

    private Stream<HapiSpecOperation> randomSetAndDeleteVarInt() {
        final var numSetsAndDeletes = 2;
        return IntStream.range(0, numSetsAndDeletes)
                .boxed()
                .flatMap(i -> Stream.of(
                        contractCall(CONTRACT, "setVarIntArrDataAlloc", new Object[] {randomInts()})
                                .gas(5_000_000),
                        contractCall(CONTRACT, "deleteVarIntArrDataAlloc")));
    }

    private Stream<HapiSpecOperation> randomSetAndDeleteString() {
        final var numCycles = 2;
        return IntStream.range(0, numCycles)
                .boxed()
                .flatMap(i -> Stream.of(
                        contractCall(CONTRACT, "setVarStringConcat", randomString())
                                .gas(5_000_000),
                        contractCall(CONTRACT, "setVarStringConcat", randomString())
                                .gas(5_000_000),
                        contractCall(CONTRACT, "deleteVarStringConcat").gas(5_000_000)));
    }

    private Stream<HapiSpecOperation> randomSetAndDeleteStruct() {
        final var numCycles = 4;
        return IntStream.range(0, numCycles)
                .boxed()
                .flatMap(i -> Stream.of(
                        contractCall(CONTRACT, "setVarContractStruct", randomContractStruct())
                                .gas(5_000_000),
                        contractCall(CONTRACT, "deleteVarContractStruct").gas(5_000_000)));
    }

    private Tuple randomContractStruct() {
        return Tuple.of(
                BigInteger.valueOf(RANDOM.nextInt(MAX_VALUE)),
                randomHeadlongAddress(),
                randomBytes32(),
                randomString(),
                RANDOM.nextInt(3),
                randomInts(),
                randomString());
    }

    private Address randomHeadlongAddress() {
        final var bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return asHeadlongAddress(bytes);
    }

    private byte[] randomBytes32() {
        final var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private String randomString() {
        return new String(randomBytes32());
    }

    private int randomEnum() {
        return RANDOM.nextInt(3);
    }

    private BigInteger[] randomInts() {
        return new BigInteger[] {
            BigInteger.valueOf(RANDOM.nextInt(MAX_VALUE)),
            BigInteger.valueOf(RANDOM.nextInt(MAX_VALUE)),
            BigInteger.valueOf(RANDOM.nextInt(MAX_VALUE)),
            BigInteger.valueOf(RANDOM.nextInt(MAX_VALUE)),
        };
    }
}
