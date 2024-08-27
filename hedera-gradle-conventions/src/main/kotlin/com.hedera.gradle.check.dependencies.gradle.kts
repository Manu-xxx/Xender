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

import com.autonomousapps.DependencyAnalysisSubExtension
import org.gradlex.javamodule.dependencies.tasks.ModuleDirectivesOrderingCheck
import org.gradlex.javamodule.dependencies.tasks.ModuleDirectivesScopeCheck

plugins {
    id("com.autonomousapps.dependency-analysis")
    id("com.hedera.gradle.base.lifecycle")
    id("com.hedera.gradle.base.jpms-modules")
}

// ordering check is done by SortModuleInfoRequiresStep
tasks.withType<ModuleDirectivesOrderingCheck> { enabled = false }

// Do not report dependencies from one source set to another as 'required'.
// In particular, in case of test fixtures, the analysis would suggest to
// add as testModuleInfo { require(...) } to the main module. This is
// conceptually wrong, because in whitebox testing the 'main' and 'test'
// module are conceptually considered one module (main module extended with tests)
configure<DependencyAnalysisSubExtension> { issues { onAny { exclude(project.path) } } }

tasks.named("qualityGate") { dependsOn(tasks.withType<ModuleDirectivesScopeCheck>()) }
