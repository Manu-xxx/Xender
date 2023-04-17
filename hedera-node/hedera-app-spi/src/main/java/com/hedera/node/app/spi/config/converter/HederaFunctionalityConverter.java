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

package com.hedera.node.app.spi.config.converter;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.swirlds.config.api.converter.ConfigConverter;

/**
 * Converter implementation for the config API to support {@link HederaFunctionality}. Based on
 * https://github.com/hashgraph/hedera-services/issues/6106 we currently need to add ConfigConverter explicitly
 */
public class HederaFunctionalityConverter extends AbstractEnumConfigConverter<HederaFunctionality>
        implements ConfigConverter<HederaFunctionality> {

    @Override
    protected Class<HederaFunctionality> getEnumType() {
        return HederaFunctionality.class;
    }
}
