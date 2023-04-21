/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.consensus;

import static com.swirlds.common.test.WeightGenerators.BALANCED;
import static com.swirlds.common.test.WeightGenerators.BALANCED_REAL_WEIGHT;
import static com.swirlds.common.test.WeightGenerators.INCREMENTING;
import static com.swirlds.common.test.WeightGenerators.ONE_THIRD_ZERO_WEIGHT;
import static com.swirlds.common.test.WeightGenerators.RANDOM_REAL_WEIGHT;
import static com.swirlds.common.test.WeightGenerators.SINGLE_NODE_STRONG_MINORITY;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public class ConsensusTestArgs {

    public static final String BALANCED_WEIGHT_DESC = "Balanced Weight";
    public static final String BALANCED_REAL_WEIGHT_DESC = "Balanced Weight, Real Total Weight Value";
    public static final String INCREMENTAL_NODE_WEIGHT_DESC = "Incremental Node Weight";
    public static final String SINGLE_NODE_STRONG_MINORITY_DESC = "Single Node With Strong Minority Weight";
    public static final String ONE_THIRD_NODES_ZERO_WEIGHT_DESC = "One Third of Nodes Have Zero Weight";
    public static final String RANDOM_WEIGHT_DESC = "Random Weight, Real Total Weight Value";

    static Stream<Arguments> reconnectSimulation() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(
                        4,
                        BALANCED,
                        BALANCED_WEIGHT_DESC,
                        6757520909990169760L, // used to reproduce fourth issue
                        260671156642312409L, // used to reproduce fourth issue
                        -3288011960561144705L, // used to reproduce third issue
                        -7928741292155768265L, // used to reproduce third issue
                        -2668357724624929237L, // used to reproduce first minTimestamp issue
                        -2230677537676013594L // used to reproduce second minTimestamp issue))
                        )),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(4, ONE_THIRD_ZERO_WEIGHT, ONE_THIRD_NODES_ZERO_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(4, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> reconnectSimulationWithShadowGraph() {
        return Stream.of(
                Arguments.of(
                        new ConsensusTestParams(10, SINGLE_NODE_STRONG_MINORITY, SINGLE_NODE_STRONG_MINORITY_DESC)),
                Arguments.of(new ConsensusTestParams(10, ONE_THIRD_ZERO_WEIGHT, ONE_THIRD_NODES_ZERO_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(10, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> staleEvent() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(6, INCREMENTING, BALANCED_WEIGHT_DESC, 2524451583646241601L)),
                Arguments.of(new ConsensusTestParams(6, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(6, SINGLE_NODE_STRONG_MINORITY, SINGLE_NODE_STRONG_MINORITY_DESC)),
                Arguments.of(new ConsensusTestParams(6, ONE_THIRD_ZERO_WEIGHT, ONE_THIRD_NODES_ZERO_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(6, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> areAllEventsReturned() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, ONE_THIRD_ZERO_WEIGHT, ONE_THIRD_NODES_ZERO_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> orderInvarianceTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, ONE_THIRD_ZERO_WEIGHT, ONE_THIRD_NODES_ZERO_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(50, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> forkingTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> partitionTests() {
        return Stream.of(
                // Uses balanced weights for 4 so that each partition can continue to create events.
                // This limitation if one of the test, not the consensus algorithm.
                Arguments.of(new ConsensusTestParams(4, BALANCED_REAL_WEIGHT, BALANCED_REAL_WEIGHT_DESC)),

                // Use uneven weight such that no single node has a strong minority and could be
                // put in a partition by itself and no longer generate events. This limitation if one
                // of the test, not the consensus algorithm.
                Arguments.of(new ConsensusTestParams(5, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)));
    }

    static Stream<Arguments> subQuorumPartitionTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(7, BALANCED_REAL_WEIGHT, BALANCED_REAL_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(
                        9,
                        ONE_THIRD_ZERO_WEIGHT,
                        ONE_THIRD_NODES_ZERO_WEIGHT_DESC,
                        // used to cause a stale mismatch, documented in swirlds/swirlds-platform/issues/5007
                        3101029514312517274L,
                        -4115810541946354865L)));
    }

    static Stream<Arguments> cliqueTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(4, BALANCED, BALANCED_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> variableRateTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> nodeUsesStaleOtherParents() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(
                        4,
                        INCREMENTING,
                        INCREMENTAL_NODE_WEIGHT_DESC,
                        // seed was failing because Consensus ratio is 0.6611, which is less than what was previously
                        // set
                        458078453642476240L)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> nodeProvidesStaleOtherParents() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(
                        4,
                        INCREMENTING,
                        INCREMENTAL_NODE_WEIGHT_DESC,
                        // seed was previously failing because Consensus ratio is 0.2539
                        -6816700673806876476L)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> quorumOfNodesGoDownTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> subQuorumOfNodesGoDownTests() {
        return Stream.of(
                Arguments.of(new ConsensusTestParams(2, BALANCED, BALANCED_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(4, INCREMENTING, INCREMENTAL_NODE_WEIGHT_DESC)),
                Arguments.of(new ConsensusTestParams(9, RANDOM_REAL_WEIGHT, RANDOM_WEIGHT_DESC)));
    }

    static Stream<Arguments> ancientEventTests() {
        return Stream.of(Arguments.of(new ConsensusTestParams(4, BALANCED, BALANCED_WEIGHT_DESC)));
    }
}
