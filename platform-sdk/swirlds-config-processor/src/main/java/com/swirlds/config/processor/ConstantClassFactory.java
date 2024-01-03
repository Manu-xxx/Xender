/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.processor;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import javax.lang.model.element.Modifier;
import javax.tools.JavaFileObject;

/**
 * Utilities for creating constant class files from {@link ConfigDataRecordDefinition} instances.
 * Its methods should be accessed statically, and it should not be instantiated.
 */
public final class ConstantClassFactory {

    /**
     * private constructor to prevent instantiation
     */
    private ConstantClassFactory() {}

    /**
     * Processes a given {@link ConfigDataRecordDefinition} and writes a corresponding constant class file.
     *
     * @param configDataRecordDefinition The record definition to be processed. Must not be {@code null}.
     * @param constantsSourceFile The Java file object where the constant class should be written. Must not be {@code null}.
     *
     * @throws IOException If an error occurs while writing the constant class.
     */
    public static void doWork(
            @NonNull final ConfigDataRecordDefinition configDataRecordDefinition,
            @NonNull final JavaFileObject constantsSourceFile)
            throws IOException {

        Objects.requireNonNull(configDataRecordDefinition, "configDataRecordDefinition must not be null");
        Objects.requireNonNull(constantsSourceFile, "constantsSourceFile must not be null");

        final String originalRecordClassName =
                configDataRecordDefinition.packageName() + "." + configDataRecordDefinition.simpleClassName();

        TypeSpec.Builder constantsClassBuilder = TypeSpec.classBuilder(
                        configDataRecordDefinition.simpleClassName() + ConfigProcessorConstants.CONSTANTS_CLASS_SUFFIX)
                .addModifiers(Modifier.FINAL, Modifier.PUBLIC)
                .addJavadoc(
                        "Constraints constants for all the property names that are part of {@link $L}. Generated by {@code $L} on $L.\n@see $L\n",
                        originalRecordClassName,
                        ConstantClassFactory.class.getName(),
                        DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now()),
                        originalRecordClassName);

        configDataRecordDefinition.propertyDefinitions().forEach(propertyDefinition -> {
            final String name = toConstantName(
                    propertyDefinition.name().replace(configDataRecordDefinition.configDataName() + ".", ""));
            FieldSpec fieldSpec = FieldSpec.builder(
                            String.class, name, Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$S", propertyDefinition.name())
                    .addJavadoc(
                            "Name of the {@link $L#$L} property\n@see $L#$L\n",
                            originalRecordClassName,
                            propertyDefinition.fieldName(),
                            originalRecordClassName,
                            propertyDefinition.fieldName())
                    .build();
            constantsClassBuilder.addField(fieldSpec);
        });

        TypeSpec constantsClass = constantsClassBuilder.build();
        JavaFile javaFile = JavaFile.builder(configDataRecordDefinition.packageName(), constantsClass)
                .build();

        try (Writer writer = constantsSourceFile.openWriter()) {
            javaFile.writeTo(writer);
        }
    }

    /**
     * Converts a property name into a constant name. The conversion is based on changing from camel case to snake case
     *
     * @param propertyName The property name to be converted. Must not be {@code null}.
     *
     * @return The converted constant name. Never {@code null}.
     */
    @NonNull
    public static String toConstantName(@NonNull final String propertyName) {
        Objects.requireNonNull(propertyName, "propertyName must not be null");

        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < propertyName.length(); i++) {
            final char character = propertyName.charAt(i);
            if (i > 0 && Character.isUpperCase(character)) {
                builder.append("_");
                builder.append(character);
            } else {
                builder.append(Character.toUpperCase(character));
            }
        }
        return builder.toString();
    }
}
