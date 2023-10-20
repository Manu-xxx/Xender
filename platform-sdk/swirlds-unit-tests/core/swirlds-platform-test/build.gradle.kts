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

plugins {
    id("com.hedera.hashgraph.sdk.conventions")
    id("com.hedera.hashgraph.benchmark-conventions")
}

jmhModuleInfo {
    requires("com.swirlds.base")
    requires("com.swirlds.common")
    requires("com.swirlds.config.api")
    requires("com.swirlds.platform.core")
    requires("com.swirlds.common.test.fixtures")
    requires("com.swirlds.platform.core.test.fixtures")
    requires("jmh.core")
}

testModuleInfo {
    requires("com.swirlds.common.testing")
    requires("com.swirlds.merkle")
    requires("com.swirlds.sign.tool") // should be removed in future
    requires("com.swirlds.base.test.fixtures")
    requires("awaitility")
    requires("org.junit.jupiter.params")
    requires("org.mockito.junit.jupiter")
    requiresStatic("com.github.spotbugs.annotations")
}
