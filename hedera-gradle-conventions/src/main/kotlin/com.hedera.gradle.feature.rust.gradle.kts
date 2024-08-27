/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.hedera.gradle.extensions.CargoExtension
import com.hedera.gradle.services.TaskLockService
import com.hedera.gradle.tasks.CargoBuildTask

plugins { id("java") }

val cargo = project.extensions.create<CargoExtension>("cargo")

cargo.targets(
    // "darwin-x86-64",
    "darwin-aarch64",
    // "linux-x86-64",
    // "linux-aarch64",
    // "win32-x86-64-msvc",
)

// Cargo might do installation work, do not run in parallel:
tasks.withType<CargoBuildTask>().configureEach {
    usesService(
        gradle.sharedServices.registerIfAbsent("lock", TaskLockService::class) {
            maxParallelUsages = 1
        }
    )
}
