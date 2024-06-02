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

package com.hedera.services.bdd.junit.extensions;

import static java.lang.reflect.Modifier.isStatic;
import static org.junit.platform.commons.support.AnnotationSupport.findAnnotatedFields;

import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.annotations.ContractSpec;
import com.hedera.services.bdd.spec.dsl.entities.SpecAccount;
import com.hedera.services.bdd.spec.dsl.entities.SpecContract;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.Field;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

public class SpecEntityExtension implements ParameterResolver, BeforeAllCallback {
    @Override
    public boolean supportsParameter(
            @NonNull final ParameterContext parameterContext, @NonNull final ExtensionContext extensionContext) {
        return SpecEntity.class.isAssignableFrom(parameterContext.getParameter().getType());
    }

    @Override
    public Object resolveParameter(
            @NonNull final ParameterContext parameterContext, @NonNull final ExtensionContext extensionContext)
            throws ParameterResolutionException {
        final var entityType = parameterContext.getParameter().getType();
        if (entityType == SpecAccount.class) {
            return new SpecAccount(parameterContext.getParameter().getName());
        } else if (entityType == SpecContract.class) {
            final var parameter = parameterContext.getParameter();
            if (!parameter.isAnnotationPresent(ContractSpec.class)) {
                throw new IllegalArgumentException("Missing @ContractSpec annotation");
            }
            return contractFrom(parameter.getAnnotation(ContractSpec.class));
        } else {
            throw new ParameterResolutionException("Unsupported entity type " + entityType);
        }
    }

    @Override
    public void beforeAll(@NonNull final ExtensionContext context) throws Exception {
        for (final var field : findAnnotatedFields(
                context.getRequiredTestClass(), ContractSpec.class, f -> isStatic(f.getModifiers()))) {
            final var specContract = contractFrom(field.getAnnotation(ContractSpec.class));
            injectValueIntoField(field, specContract);
        }
    }

    private SpecContract contractFrom(@NonNull final ContractSpec annotation) {
        final var name = annotation.name().isBlank() ? annotation.contract() : annotation.name();
        return new SpecContract(name, annotation.contract(), annotation.creationGas());
    }

    private void injectValueIntoField(@NonNull final Field field, @NonNull final Object value)
            throws IllegalAccessException {
        final var accessible = field.isAccessible();
        field.setAccessible(true);
        field.set(null, value);
        field.setAccessible(accessible);
    }
}
