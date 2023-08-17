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

plugins { id("com.swirlds.platform.conventions") }

dependencies {
    // Individual Dependencies
    implementation(project(":swirlds-platform-core"))
}

val copyApp =
    tasks.register<Copy>("copyApp") {
        from(tasks.jar)
        into(File(rootProject.projectDir, "sdk"))
        rename { "${project.name}.jar" }
        shouldRunAfter(tasks.assemble)
    }

tasks.assemble { dependsOn(copyApp) }

extraJavaModuleInfo { failOnMissingModuleInfo.set(false) }

dependencies {
    runtimeOnly(project(":swirlds-merkle"))
    runtimeOnly(project(":swirlds-unit-tests:structures:swirlds-merkle-test"))
    runtimeOnly(libs.protobuf)
}

tasks.jar {
    // Gradle fails to track 'configurations.runtimeClasspath' as an input to the task if it is
    // only used in the 'mainfest.attributes'. Hence, we explicitly add it as input.
    inputs.files(configurations.runtimeClasspath)
    manifest {
        attributes(
            "Main-Class" to "com.swirlds.platform.Browser",
            "Class-Path" to
                configurations.runtimeClasspath.get().elements.map { entry ->
                    entry
                        .map { "data/lib/" + it.asFile.name }
                        .sorted()
                        .joinToString(separator = " ")
                }
        )
    }
}
