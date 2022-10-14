/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.merkle;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.state.migration.QueryableRecords;
import com.hedera.test.utils.SeededPropertySource;
import org.junit.jupiter.api.Test;

class MerklePayerRecordsTest {
    @Test
    void alwaysHasMutableFcq() {
        final var subject = new MerklePayerRecords();
        assertNotNull(subject.mutableQueue());
    }

    @Test
    void queryableRecordsCanChange() {
        final var subject = new MerklePayerRecords();
        assertSame(QueryableRecords.NO_QUERYABLE_RECORDS, subject.asQueryableRecords());

        final var someRecord = SeededPropertySource.forSerdeTest(11, 1).nextRecord();
        subject.offer(someRecord);
        final var queryable = subject.asQueryableRecords();
        assertEquals(1, queryable.expectedSize());
    }
}
