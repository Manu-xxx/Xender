/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import java.io.BufferedOutputStream
import net.swiftzer.semver.SemVer
import org.owasp.dependencycheck.reporting.ReportGenerator

plugins {
    id("org.sonarqube")
    id("org.owasp.dependencycheck")
    id("lazy.zoo.gradle.git-data-plugin")
}

// Configure the Sonarqube extension for SonarCloud reporting. These properties should not be changed so no need to
// have them in the gradle.properties defintions.
sonarqube {
    properties {
        property("sonar.host.url", "https://sonarcloud.io")
        property("sonar.organization", "hashgraph")
        property("sonar.projectVersion", project.version)
        property("sonar.links.homepage", "https://github.com/hashgraph/hedera-services")
        property("sonar.links.ci", "https://github.com/hashgraph/hedera-services/actions")
        property("sonar.links.issue", "https://github.com/hashgraph/hedera-services/issues")
        property("sonar.links.scm", "https://github.com/hashgraph/hedera-services.git")

        property("sonar.coverage.exclusions", "**/test-clients/**,**/hedera-node/src/jmh/**")

        // Ignored to match pom.xml setup
        property("sonar.issue.ignore.multicriteria", "e1,e2")
        property("sonar.issue.ignore.multicriteria.e1.resourceKey", "**/*.java")
        property("sonar.issue.ignore.multicriteria.e1.ruleKey", "java:S125")
        property("sonar.issue.ignore.multicriteria.e2.resourceKey", "**/*.java")
        property("sonar.issue.ignore.multicriteria.e2.ruleKey", "java:S1874")
    }
}

dependencyCheck {
    autoUpdate = true
    formats = listOf(ReportGenerator.Format.HTML.name, ReportGenerator.Format.XML.name, ReportGenerator.Format.JUNIT.name)
    junitFailOnCVSS = 7.0f
    failBuildOnCVSS = 11.0f
    outputDirectory = layout.buildDirectory.dir("reports/dependency-check").get().asFile.toString()
}

tasks.register("githubVersionSummary") {
    group = "github"
    doLast {
        val ghStepSummaryPath: String = providers.environmentVariable("GITHUB_STEP_SUMMARY").orNull ?:
            throw IllegalArgumentException("This task may only be run in a Github Actions CI environment!" +
                    "Unable to locate the GITHUB_STEP_SUMMARY environment variable.")

        Utils.generateProjectVersionReport(rootProject, BufferedOutputStream(File(ghStepSummaryPath).outputStream()))
    }
}

tasks.register("showVersion") {
    group = "versioning"
    doLast {
        println(project.version)
    }
}

tasks.register("versionAsPrefixedCommit") {
    group = "versioning"
    doLast {
        gitData.lastCommitHash?.let {
            val prefix = providers.gradleProperty("commitPrefix").getOrElse("adhoc")
            val newPrerel = prefix + ".x" + it.take(8)
            val currVer = SemVer.parse(rootProject.version.toString())
            try {
                val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, newPrerel)
                Utils.updateVersion(rootProject, newVer)
            } catch (e: java.lang.IllegalArgumentException) {
                throw IllegalArgumentException(String.format("%s: %s", e.message, newPrerel), e)
            }
        }
    }
}

tasks.register("versionAsSnapshot") {
    group = "versioning"
    doLast {
        val currVer = SemVer.parse(rootProject.version.toString())
        val newVer = SemVer(currVer.major, currVer.minor, currVer.patch, "SNAPSHOT")

        Utils.updateVersion(rootProject, newVer)
    }
}

tasks.register("versionAsSpecified") {
    group = "versioning"
    doLast {
        val verStr = providers.gradleProperty("newVersion")

        if (!verStr.isPresent) {
            throw IllegalArgumentException("No newVersion property provided! Please add the parameter -PnewVersion=<version> when running this task.")
        }

        val newVer = SemVer.parse(verStr.get())
        Utils.updateVersion(rootProject, newVer)
    }
}
