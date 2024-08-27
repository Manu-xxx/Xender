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

plugins {
    id("java")
    id("com.github.spotbugs")
}

dependencies {
    spotbugs("com.github.spotbugs:spotbugs:4.8.6")
    spotbugs("com.github.spotbugs:spotbugs-annotations:4.8.6")
}

spotbugs {
    reportsDir = layout.buildDirectory.dir("reports/spotbugs")
    onlyAnalyze = listOf("com.hedera.hashgraph.sdk.*") // TODO ????
}

tasks.spotbugsMain {
    reports.register("html") {
        required = true
        outputLocation = layout.buildDirectory.file("reports/spotbugs/main/spotbugs.html")
        setStylesheet("fancy-hist.xsl")
    }
}
