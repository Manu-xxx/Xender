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
    id("com.swirlds.platform.conventions")
    `java-library`
    id("com.swirlds.platform.maven-publish")
}

dependencies {
    // Individual Dependencies
    implementation(project(":swirlds-base"))
    api(project(":swirlds-common"))
    api(project(":swirlds-config-api"))
    compileOnly(libs.spotbugs.annotations)

    // Test Dependencies
    testImplementation(project(":swirlds-config-impl"))
    testImplementation(project(":swirlds-unit-tests:common:swirlds-test-framework"))
    testImplementation(project(":swirlds-unit-tests:common:swirlds-common-test"))
    testImplementation(testFixtures(project(":swirlds-common")))testImplementation(testLibs.junit.jupiter.api)
    testImplementation(testLibs.bundles.junit)
}
