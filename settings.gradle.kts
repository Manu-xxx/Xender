/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

pluginManagement { @Suppress("UnstableApiUsage") includeBuild("build-logic") }

plugins { id("com.hedera.hashgraph.settings") }

includeBuild("platform-sdk")

includeBuild("hedera-node")

includeAllBuilds("platform-sdk/platform-apps/demos")

includeAllBuilds("platform-sdk/platform-apps/tests")

fun includeAllBuilds(containingFolder: String) {
    File(containingFolder).listFiles()?.forEach { folder ->
        if (File(folder, "settings.gradle.kts").exists()) {
            includeBuild(folder.path)
        }
    }
}
