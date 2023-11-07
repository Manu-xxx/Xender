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

package com.hedera.services.bdd.spec.utilops;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.TargetNetworkType;

/**
 * Enumerates the different modes in which a {@link SnapshotModeOp} can be run.
 */
public enum SnapshotMode {
    /**
     * Takes a snapshot of the record stream generated by running a {@link HapiSpec} against a standalone
     * mono-service node.
     */
    TAKE_FROM_MONO_STREAMS(TargetNetworkType.STANDALONE_MONO_NETWORK),
    /**
     * Takes a snapshot of the record stream generated by running a {@link HapiSpec} against a
     * {@link com.hedera.services.bdd.junit.HapiTest} network.
     */
    TAKE_FROM_HAPI_TEST_STREAMS(TargetNetworkType.HAPI_TEST_NETWORK),
    /**
     * Fuzzy-matches the record stream generated by running a {@link HapiSpec} against a standalone
     * mono-service node with a saved snapshot.
     */
    FUZZY_MATCH_AGAINST_MONO_STREAMS(TargetNetworkType.STANDALONE_MONO_NETWORK),
    /**
     * Fuzzy-matches the record stream generated by running a {@link HapiSpec} against a
     * {@link com.hedera.services.bdd.junit.HapiTest} network with a saved snapshot.
     */
    FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS(TargetNetworkType.HAPI_TEST_NETWORK);

    private final TargetNetworkType targetNetworkType;

    SnapshotMode(TargetNetworkType targetNetworkType) {
        this.targetNetworkType = targetNetworkType;
    }

    public TargetNetworkType targetNetworkType() {
        return targetNetworkType;
    }
}
