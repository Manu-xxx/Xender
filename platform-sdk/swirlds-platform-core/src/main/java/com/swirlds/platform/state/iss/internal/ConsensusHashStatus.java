/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.iss.internal;

/**
 * Various states in the process of determining the consensus hash of a round.
 */
public enum ConsensusHashStatus {
    /**
     * The consensus hash is not yet known
     */
    UNDECIDED,
    /**
     * The consensus hash is known
     */
    DECIDED,
    /**
     * There exists no consensus hash due to an ISS
     */
    CATASTROPHIC_ISS
}
