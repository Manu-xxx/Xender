package com.hedera.hashgraph.gradlebuild.rules

import org.gradle.api.artifacts.ComponentMetadataContext
import org.gradle.api.artifacts.ComponentMetadataRule

abstract class HederaProtobufJavaApiMetadataRule : ComponentMetadataRule {

    override fun execute(context: ComponentMetadataContext) {
        context.details.allVariants {
            withDependencies {
                removeAll { it.name == "grpc-testing" }
                removeAll { it.name == "javax.annotation-api" }
                add("com.github.spotbugs:spotbugs-annotations:4.7.3")
            }
        }
    }
}