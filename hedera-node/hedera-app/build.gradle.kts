/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
    id("com.hedera.hashgraph.conventions")
}

description = "Hedera Application - Implementation"

dependencies {
    api(project(":hedera-node:hedera-app-spi"))
    implementation(project(":hedera-node:hedera-mono-service"))
    implementation(libs.jsr305.annotation)
    implementation(libs.hapi)
    implementation(libs.bundles.helidon)
    implementation(libs.bundles.swirlds)

    itestImplementation(libs.hapi)
    itestImplementation(libs.bundles.helidon)
    itestImplementation(libs.bundles.swirlds)
    itestImplementation(testLibs.helidon.grpc.client)
    itestImplementation(testLibs.bundles.mockito)

    testImplementation(testFixtures(project(":hedera-node:hedera-mono-service")))
    testImplementation(testLibs.bundles.testing)
}

configurations.all {
    exclude("javax.annotation", "javax.annotation-api")
    exclude("io.grpc", "grpc-core")
    exclude("io.grpc", "grpc-api")
}
