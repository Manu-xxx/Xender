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

package com.swirlds.config.processor.antlr;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.processor.ConfigDataPropertyDefinition;
import com.swirlds.config.processor.ConfigDataRecordDefinition;
import com.swirlds.config.processor.antlr.generated.JavaParser.AnnotationContext;
import com.swirlds.config.processor.antlr.generated.JavaParser.CompilationUnitContext;
import com.swirlds.config.processor.antlr.generated.JavaParser.RecordComponentContext;
import com.swirlds.config.processor.antlr.generated.JavaParser.RecordDeclarationContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates a {@link ConfigDataRecordDefinition} from a given Java source file.
 */
public class AntlrConfigRecordParser {

    private static boolean isAnnotatedWithConfigData(
            final RecordDeclarationContext ctx, String packageName, List<String> imports) {
        final List<AnnotationContext> allAnnotations = AntlrUtils.getAllAnnotations(ctx);
        return AntlrUtils.findAnnotationOfType(ConfigData.class, allAnnotations, packageName, imports)
                .isPresent();
    }

    private static Optional<AnnotationContext> getConfigDataAnnotation(
            final RecordDeclarationContext ctx, final String packageName, final List<String> imports) {
        final List<AnnotationContext> annotations = AntlrUtils.getAllAnnotations(ctx);
        return AntlrUtils.findAnnotationOfType(ConfigData.class, annotations, packageName, imports);
    }

    private static String getConfigDataAnnotationValue(
            final RecordDeclarationContext ctx, final String packageName, final List<String> imports) {
        return getConfigDataAnnotation(ctx, packageName, imports)
                .map(annotationContext -> annotationContext.elementValue())
                .map(elementValueContext -> elementValueContext.getText())
                .map(text -> text.substring(1, text.length() - 1)) // remove quotes
                .orElse("");
    }

    private static Optional<AnnotationContext> getConfigPropertyAnnotation(
            final RecordComponentContext ctx, final String packageName, final List<String> imports) {
        final List<AnnotationContext> annotations = AntlrUtils.getAllAnnotations(ctx);
        return AntlrUtils.findAnnotationOfType(ConfigProperty.class, annotations, packageName, imports);
    }

    private static String getConfigPropertyAnnotationDefaultValue(
            final RecordComponentContext ctx, final String packageName, final List<String> imports) {
        return getConfigPropertyAnnotation(ctx, packageName, imports)
                .flatMap(annotationContext -> AntlrUtils.getAnnotationValue(annotationContext, "defaultValue"))
                .map(text -> text.substring(1, text.length() - 1)) // remove quotes
                .orElse(ConfigProperty.UNDEFINED_DEFAULT_VALUE);
    }

    private static Optional<String> getConfigPropertyAnnotationName(
            final RecordComponentContext ctx, final String packageName, final List<String> imports) {
        return getConfigPropertyAnnotation(ctx, packageName, imports)
                .flatMap(annotationContext -> AntlrUtils.getAnnotationValue(annotationContext));
    }

    private static ConfigDataPropertyDefinition createPropertyDefinition(
            RecordComponentContext ctx,
            final String configPropertyNamePrefix,
            final String packageName,
            final List<String> imports,
            Map<String, String> javadocParams) {
        final String componentName = ctx.identifier().getText();
        final String configPropertyNameSuffix = getConfigPropertyAnnotationName(ctx, packageName, imports).orElse(
                componentName);
        final String name = createPropertyName(configPropertyNamePrefix, configPropertyNameSuffix);
        final String defaultValue = getConfigPropertyAnnotationDefaultValue(ctx, packageName, imports);
        final String type = Optional.ofNullable(ctx.typeType().classOrInterfaceType())
                .map(c -> c.getText())
                .map(typeText -> imports.stream()
                        .filter(importText -> importText.endsWith(typeText))
                        .findAny()
                        .orElse(typeText))
                .map(typeText -> getTypeForJavaLang(typeText))
                .orElseGet(() -> ctx.typeType().primitiveType().getText());
        final String description =
                Optional.ofNullable(javadocParams.get(componentName)).orElse("");
        return new ConfigDataPropertyDefinition(componentName, name, type, defaultValue, description);
    }

    private static String createPropertyName(final String configPropertyNamePrefix,
            final String configPropertyNameSuffix) {
        if (configPropertyNamePrefix.isBlank()) {
            return configPropertyNameSuffix;
        } else {
            return configPropertyNamePrefix + "." + configPropertyNameSuffix;
        }
    }

    private static String getTypeForJavaLang(String type) {
        if (!type.contains(".")) {
            return String.class.getPackageName() + "." + type;
        }
        return type;
    }


    private static List<ConfigDataRecordDefinition> createDefinitions(CompilationUnitContext unitContext) {
        final String packageName = AntlrUtils.getPackage(unitContext);
        final List<String> imports = AntlrUtils.getImports(unitContext);
        return AntlrUtils.getRecordDeclarationContext(unitContext).stream()
                .filter(c -> isAnnotatedWithConfigData(c, packageName, imports))
                .map(recordContext -> createDefinition(unitContext, recordContext, packageName, imports))
                .collect(Collectors.toList());
    }

    private static ConfigDataRecordDefinition createDefinition(
            CompilationUnitContext unitContext,
            RecordDeclarationContext recordContext,
            String packageName,
            List<String> imports) {
        final String recordName = recordContext.identifier().getText();
        final String configPropertyNamePrefix = getConfigDataAnnotationValue(recordContext, packageName, imports);
        final Map<String, String> javadocParams = unitContext.children.stream()
                .filter(c -> AntlrUtils.isJavaDocNode(c))
                .map(c -> c.getText())
                .map(t -> AntlrUtils.getJavaDocParams(t))
                .reduce((m1, m2) -> {
                    m1.putAll(m2);
                    return m1;
                })
                .orElse(Map.of());
        final Set<ConfigDataPropertyDefinition> propertyDefinitions =
                recordContext.recordHeader().recordComponentList().recordComponent().stream()
                        .map(c -> createPropertyDefinition(c, configPropertyNamePrefix, packageName, imports,
                                javadocParams))
                        .collect(Collectors.toSet());
        return new ConfigDataRecordDefinition(packageName, recordName, configPropertyNamePrefix, propertyDefinitions);
    }

    public static List<ConfigDataRecordDefinition> parse(@NonNull final String fileContent) throws IOException {
        final CompilationUnitContext parsedContext = AntlrUtils.parse(fileContent);
        return createDefinitions(parsedContext);
    }
}
