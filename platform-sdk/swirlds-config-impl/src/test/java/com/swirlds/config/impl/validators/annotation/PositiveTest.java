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

package com.swirlds.config.impl.validators.annotation;

import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.validation.ConfigViolationException;
import com.swirlds.config.extensions.sources.SimpleConfigSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PositiveTest {

    @Test
    public void testNoViolation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("positive.intValue", 1))
                .withSources(new SimpleConfigSource("positive.longValue", 1L))
                .withSources(new SimpleConfigSource("positive.doubleValue", 1D))
                .withSources(new SimpleConfigSource("positive.floatValue", 1F))
                .withSources(new SimpleConfigSource("positive.shortValue", 1))
                .withSources(new SimpleConfigSource("positive.byteValue", 1))
                .withConfigDataTypes(PositiveConfigData.class);

        // then
        Assertions.assertDoesNotThrow(() -> configurationBuilder.build(), "No violation should happen");
    }

    @Test
    public void testIntViolation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("positive.intValue", -1))
                .withSources(new SimpleConfigSource("positive.longValue", 1L))
                .withSources(new SimpleConfigSource("positive.doubleValue", 1D))
                .withSources(new SimpleConfigSource("positive.floatValue", 1F))
                .withSources(new SimpleConfigSource("positive.shortValue", 1))
                .withSources(new SimpleConfigSource("positive.byteValue", 1))
                .withConfigDataTypes(PositiveConfigData.class);

        // when
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Violation for @Positive should happen");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals(
                "positive.intValue", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("-1", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "Value must be > 0", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testViolations() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("positive.intValue", -1))
                .withSources(new SimpleConfigSource("positive.longValue", -1L))
                .withSources(new SimpleConfigSource("positive.doubleValue", -1D))
                .withSources(new SimpleConfigSource("positive.floatValue", -1F))
                .withSources(new SimpleConfigSource("positive.shortValue", -1))
                .withSources(new SimpleConfigSource("positive.byteValue", -1))
                .withConfigDataTypes(PositiveConfigData.class);

        // when
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Violation for @Positive should happen");

        // then
        Assertions.assertEquals(6, exception.getViolations().size());
    }

    @Test
    public void testEdgeCaseViolations() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("positive.intValue", 0))
                .withSources(new SimpleConfigSource("positive.longValue", 0L))
                .withSources(new SimpleConfigSource("positive.doubleValue", 0D))
                .withSources(new SimpleConfigSource("positive.floatValue", 0F))
                .withSources(new SimpleConfigSource("positive.shortValue", 0))
                .withSources(new SimpleConfigSource("positive.byteValue", 0))
                .withConfigDataTypes(PositiveConfigData.class);

        // when
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Violation for @Positive should happen");

        // then
        Assertions.assertEquals(6, exception.getViolations().size());
    }
}
