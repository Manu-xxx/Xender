/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.metrics.platform.prometheus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NameConvertTests {

    @Test
    void testNameConverter() {
        assertThat(NameConverter.fix("Hello_World:42")).isEqualTo("Hello_World:42");
        assertThat(NameConverter.fix("")).isEmpty();
        assertThat(NameConverter.fix(".- /%")).isEqualTo(":___per_Percent");
        assertThatThrownBy(() -> NameConverter.fix(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
