/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

plugins {
    id("com.hedera.hashgraph.sdk.conventions")
    id("com.hedera.hashgraph.platform-maven-publish")
    id("java-test-fixtures")
}

testModuleInfo {
    requires("com.swirlds.common.testing")
    requires("com.swirlds.config.api")
    requires("com.swirlds.config.extensions")
    requires("com.swirlds.merkledb")
    requires("com.swirlds.test.framework")
    requires("com.swirlds.virtualmap")
    requires("com.swirlds.config.api.test.fixtures")
    requires("org.junit.jupiter.api")
    requires("org.junit.jupiter.params")
}
