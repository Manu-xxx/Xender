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

plugins {
  id("com.hedera.hashgraph.conventions")
  id("com.hedera.hashgraph.shadow-jar")
  id("org.gradle.java-test-fixtures")
}

description = "Hedera Services Command-Line Clients"

tasks.jar { manifest { attributes("Automatic-Module-Name" to "com.hedera.services.cli") } }

configurations.all {
  exclude("javax.annotation", "javax.annotation.api")
  exclude("com.google.code.findbugs", "jsr305")
  exclude("org.jetbrains", "annotations")
  exclude("org.checkerframework", "checker-qual")
  exclude("org.hamcrest", "hamcrest-core")
}

dependencies {
  compileOnly(libs.spotbugs.annotations)
  implementation(libs.bundles.swirlds)
  implementation(project(":hedera-node:hedera-mono-service"))
  implementation(testLibs.picocli)
  testImplementation(testLibs.bundles.testing)
}
