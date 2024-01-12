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

package com.swirlds.platform.event;

/**
 * The strategy used to determine if an event is ancient. There are currently two types: one bound by generations and
 * one bound by birth rounds. The original definition of ancient used generations. The new definition for ancient uses
 * birth rounds. Once migration has been completed to birth rounds, support for the generation defined ancient threshold
 * will be removed.
 */
public enum AncientMode {
    /**
     * The ancient threshold is defined by generations.
     */
    GENERATION_THRESHOLD,
    /**
     * The ancient threshold is defined by birth rounds.
     */
    BIRTH_ROUND_THRESHOLD
}
