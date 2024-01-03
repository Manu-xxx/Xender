/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.impl.converters;

import com.swirlds.config.api.converter.ConfigConverter;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Concrete {@link ConfigConverter} implementation that provides the support for {@link ZonedDateTime} values in the
 * configuration
 */
public final class ZonedDateTimeConverter implements ConfigConverter<ZonedDateTime> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ZonedDateTime convert(final String value) throws IllegalArgumentException {
        return ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
    }
}
