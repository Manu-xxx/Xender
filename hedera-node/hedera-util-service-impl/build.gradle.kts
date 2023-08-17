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

plugins { id("com.hedera.hashgraph.conventions") }

description = "Default Hedera Util Service Implementation"

dependencies {
    javaModuleDependencies {
        annotationProcessor(gav("dagger.compiler"))

        testImplementation(project(":app-service-network-admin"))
        testImplementation(testFixtures(project(":app-spi")))
        testImplementation(testFixtures(project(":config")))
        testImplementation(gav("com.swirlds.config.api"))
        testImplementation(gav("com.swirlds.test.framework"))
        testImplementation(gav("org.assertj.core"))
        testImplementation(gav("org.junit.jupiter.api"))
        testImplementation(gav("org.junit.jupiter.params"))
        testImplementation(gav("org.mockito"))
        testImplementation(gav("org.mockito.junit.jupiter"))
        testCompileOnly(gav("com.github.spotbugs.annotations"))
    }
}
