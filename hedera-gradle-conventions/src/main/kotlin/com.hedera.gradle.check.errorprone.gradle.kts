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

import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("java")
    id("net.ltgt.errorprone")
}

dependencies {
    // https://github.com/google/error-prone
    // https://errorprone.info/
    errorprone("com.google.errorprone:error_prone_core:2.32.0")

    // https://github.com/uber/NullAway
    errorprone("com.uber.nullaway:nullaway:0.11.3")

    // https://github.com/grpc/grpc-java-api-checker
    errorprone("io.grpc:grpc-java-api-checker:1.1.0")
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        // https://github.com/uber/NullAway
        warn("NullAway")
        option("NullAway:AnnotatedPackages", "com.hedera.hashgraph.sdk")
        option("NullAway:TreatGeneratedAsUnannotated", "true")

        // https://github.com/grpc/grpc-java-api-checker
        disable("GrpcExperimentalApi")
        warn("GrpcInternal")

        // Enable _all_ checks then selectively disable checks
        allDisabledChecksAsWarnings = true
        disable("BooleanParameter")
        disable("FieldCanBeFinal")
        disable("Finally")
        disable("FutureReturnValueIgnored")
        disable("InlineMeSuggester")
        disable("ThreadJoinLoop")
        disable("ThrowSpecificExceptions")
        disable("TryFailRefactoring")
        disable("UngroupedOverloads")
        disable("UnnecessaryDefaultInEnumSwitch")

        // Ignore generated and protobuf code
        disableWarningsInGeneratedCode = true
        excludedPaths = ".*generated.*"
    }
}
