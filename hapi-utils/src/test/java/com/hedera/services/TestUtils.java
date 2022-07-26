/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services;

import com.hedera.services.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import java.io.IOException;

public class TestUtils {
    public static ThrottleDefinitions protoDefs(final String testResource) throws IOException {
        try (final var in =
                ThrottlesJsonToProtoSerde.class
                        .getClassLoader()
                        .getResourceAsStream(testResource)) {
            return ThrottlesJsonToProtoSerde.loadProtoDefs(in);
        }
    }

    public static com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions pojoDefs(
            final String testResource) throws IOException {
        try (final var in =
                ThrottlesJsonToProtoSerde.class
                        .getClassLoader()
                        .getResourceAsStream(testResource)) {
            return ThrottlesJsonToProtoSerde.loadPojoDefs(in);
        }
    }
}
