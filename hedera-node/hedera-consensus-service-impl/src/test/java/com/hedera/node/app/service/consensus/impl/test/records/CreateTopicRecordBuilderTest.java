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

package com.hedera.node.app.service.consensus.impl.test.records;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.consensus.impl.records.CreateTopicRecordBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateTopicRecordBuilderTest {
    private CreateTopicRecordBuilder subject = new CreateTopicRecordBuilder();

    @Test
    void recordsNothingInMonoContextIfNothingTracked() {
        assertThrows(IllegalStateException.class, subject::getCreatedTopic);
    }

    @Test
    void recordsTrackedSideEffectsInMonoContext() {
        final var returnedSubject = subject.setFinalStatus(SUCCESS).setCreatedTopic(123L);
        assertSame(subject, returnedSubject);
        assertEquals(123L, subject.getCreatedTopic());
    }
}
