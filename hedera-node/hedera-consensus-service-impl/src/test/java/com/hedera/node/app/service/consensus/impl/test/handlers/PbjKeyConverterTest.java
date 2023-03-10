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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.consensus.impl.handlers.PbjKeyConverter;
import com.hedera.pbj.runtime.io.Bytes;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PbjKeyConverterTest {
    @Mock
    private Bytes bytes;

    @Mock
    private com.hederahashgraph.api.proto.java.Key grpcKey;

    @Test
    void nullPbjKeyReturnsEmptyOptional() {
        assertEquals(Optional.empty(), PbjKeyConverter.fromPbjKey(null));
    }

    @Test
    void translatesExceptionFromProtoParser() {
        given(grpcKey.toByteArray()).willReturn("NONSENSE".getBytes());

        assertThrows(IllegalStateException.class, () -> PbjKeyConverter.fromGrpcKey(grpcKey));
    }
}
