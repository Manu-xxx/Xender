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
plugins {
    id("com.hedera.hashgraph.aggregate-reports")
    id("com.hedera.hashgraph.spotless-conventions")
    id("com.hedera.hashgraph.spotless-kotlin-conventions")
}

repositories {
    mavenCentral()
}

var removeTempDockerFilesTask = tasks.register<Delete>("removeTempDockerFiles") {
    description = "Deletes all temp docker files that are copied in the root folder to create the docker image"
    group = "docker"

    delete(".env", ".dockerignore", "Dockerfile")
}

tasks.clean {
    dependsOn(removeTempDockerFilesTask)
}

var updateDockerEnvTask = tasks.register<Exec>("updateDockerEnv") {
    description = "Creates the .env file in the docker folder that contains environment variables for docker"
    group = "docker"

    workingDir("./docker")
    commandLine("./update-env.sh", project.version)
}

tasks.register<Exec>("createDockerImage") {
    description = "Creates the docker image of the services based on the current version"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir("./docker")
    commandLine("./docker-build.sh", project.version)
    finalizedBy(removeTempDockerFilesTask)
}

tasks.register<Exec>("startDockerContainers") {
    description = "Starts docker containers of the services based on the current version"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir("./docker")
    commandLine("docker-compose", "up")
}

tasks.register<Exec>("stopDockerContainers") {
    description = "Stops running docker containers of the services"
    group = "docker"

    dependsOn(updateDockerEnvTask)
    workingDir("./docker")
    commandLine("docker-compose", "stop")
}

var loginToGoogleDockerRegistryTask = tasks.register<Exec>("loginToGoogleDockerRegistry") {
    description = "Does the login to the Google registry"
    group = "docker"

    workingDir("./docker")
    commandLine("./google-registry-login.sh", "PRIVATE-JSON-KEY")
}

tasks.register<Exec>("tagDockerImage") {
    description = "Creates a new tag for the docker image"
    group = "docker"

    dependsOn(loginToGoogleDockerRegistryTask)
    commandLine(
        "docker",
        "tag",
        "services-node:" + project.version,
        "gcr.io/hedera-registry/services-node:" + project.version
    )
}

tasks.register<Exec>("pushDockerImage") {
    description = "Pushes the current tag of the docker image to the google registry"
    group = "docker"

    dependsOn(loginToGoogleDockerRegistryTask)
    commandLine(
        "docker",
        "push",
        "gcr.io/hedera-registry/services-node:" + project.version
    )
}
