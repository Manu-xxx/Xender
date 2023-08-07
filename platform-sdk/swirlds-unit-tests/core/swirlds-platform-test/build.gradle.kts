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
    id("org.gradle.java-test-fixtures")
}

extraJavaModuleInfo { failOnMissingModuleInfo.set(false) }

dependencies {
    // Individual Dependencies
    implementation(project(":swirlds-platform-core"))
    compileOnly(libs.spotbugs.annotations)

    // Test Dependencies
    testCompileOnly(libs.spotbugs.annotations)

    // These should not be implementation() based deps, but this requires refactoring to eliminate.
    implementation(testLibs.bundles.mocking)
    implementation(project(":swirlds-common-testing"))
    implementation(project(":swirlds-test-framework"))
    implementation(testFixtures(project(":swirlds-config-api")))
    implementation(testFixtures(project(":swirlds-common")))
    implementation(testFixtures(project(":swirlds-platform-core")))

    testImplementation(project(":swirlds-merkle"))
    testImplementation(project(":swirlds-sign-tool")) // FUTURE WORK: should be removed in future
    testImplementation(libs.classgraph)
    testImplementation(testLibs.bundles.junit)
    testImplementation(testLibs.bundles.utils)
    testImplementation(testFixtures(project(":swirlds-base")))
    testImplementation(testFixtures(project(":swirlds-common")))
    testImplementation(testFixtures(project(":swirlds-platform-core")))

    testImplementation(project(":swirlds-config-impl"))
}
