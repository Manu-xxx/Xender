package com.hedera.services.state.enums;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

/**
 * Token Types of {@link com.hedera.services.state.merkle.MerkleToken}
 */
public enum TokenType {
    /**
     * Interchangeable value with one another, where any quantity of them has the same value as another equal quantity if they are in the same class.
     */
    FUNGIBLE,
    /**
     * Not interchangeable with one another, as they typically have different values.
     */
    NON_FUNGIBLE
}
