/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtual.merkle.map;

import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIME_CONSUMING;
import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIMING_SENSITIVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.merkledb.MerkleDbDataSourceBuilder;
import com.swirlds.merkledb.MerkleDbTableConfig;
import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestKeySerializer;
import com.swirlds.virtual.merkle.TestObjectKey;
import com.swirlds.virtual.merkle.TestObjectKeySerializer;
import com.swirlds.virtual.merkle.TestValue;
import com.swirlds.virtual.merkle.TestValueSerializer;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

@Tag(TIMING_SENSITIVE)
final class MapTest {

    VirtualDataSourceBuilder<TestKey, TestValue> createLongBuilder() {
        final MerkleDbTableConfig<TestKey, TestValue> tableConfig = new MerkleDbTableConfig<>(
                (short) 1, DigestType.SHA_384,
                (short) 1, new TestKeySerializer(),
                (short) 1, new TestValueSerializer());
        return new MerkleDbDataSourceBuilder<>(tableConfig);
    }

    VirtualDataSourceBuilder<TestObjectKey, TestValue> createGenericBuilder() {
        final MerkleDbTableConfig<TestObjectKey, TestValue> tableConfig = new MerkleDbTableConfig<>(
                (short) 1, DigestType.SHA_384,
                (short) 1, new TestObjectKeySerializer(),
                (short) 1, new TestValueSerializer());
        return new MerkleDbDataSourceBuilder<>(tableConfig);
    }

    VirtualMap<TestKey, TestValue> createLongMap(String label) {
        return new VirtualMap<>(label, createLongBuilder());
    }

    VirtualMap<TestObjectKey, TestValue> createObjectMap(String label) {
        return new VirtualMap<>(label, createGenericBuilder());
    }

    @Test
    @Tag(TIME_CONSUMING)
    @Tags({@Tag("VirtualMerkle"), @Tag("VMAP-019")})
    @DisplayName("Insert one million elements with same key but different value")
    void insertRemoveAndModifyOneMillion() throws InterruptedException {
        final int changesPerBatch = 15_432; // Some unexpected size just to be crazy
        final int max = 1_000_000;
        VirtualMap<TestKey, TestValue> map = createLongMap("insertRemoveAndModifyOneMillion");
        try {
            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.put(new TestKey(i), new TestValue(i));
            }

            for (int i = 0; i < max; i++) {
                assertEquals(new TestValue(i), map.get(new TestKey(i)), "Expected same");
            }

            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.remove(new TestKey(i));
            }

            assertTrue(map.isEmpty(), "Map should be empty");

            for (int i = 0; i < max; i++) {
                if (i > 0 && i % changesPerBatch == 0) {
                    VirtualMap<TestKey, TestValue> older = map;
                    map = map.copy();
                    older.release();
                }

                map.put(new TestKey(i + max), new TestValue(i + max));
            }

            for (int i = 0; i < max; i++) {
                assertEquals(new TestValue(i + max), map.get(new TestKey(i + max)), "Expected same");
                assertNull(map.get(new TestKey(i)), "The old value should not exist anymore");
            }
        } finally {
            map.release();
        }
    }

    @Test
    @Tags({@Tag("VirtualMerkle")})
    @DisplayName("Delete a value that was moved to a different virtual path")
    void deletedObjectLeavesOnFlush() throws InterruptedException {
        VirtualMap<TestObjectKey, TestValue> map = createObjectMap("deletedObjectLeavesOnFlush");
        for (int i = 0; i < 8; i++) {
            map.put(new TestObjectKey(i), new TestValue(i));
        }

        VirtualRootNode<TestObjectKey, TestValue> rootNode = map.getRight();
        rootNode.enableFlush();

        RecordAccessor<TestObjectKey, TestValue> records = rootNode.getRecords();
        // Check that key/value 0 is at path 7
        VirtualLeafRecord<TestObjectKey, TestValue> leaf = records.findLeafRecord(7, false);
        assertNotNull(leaf);
        assertEquals(new TestObjectKey(0), leaf.getKey());
        assertEquals(new TestValue(0), leaf.getValue());

        VirtualMap<TestObjectKey, TestValue> copy = map.copy();
        map.release();
        map = copy;
        rootNode.waitUntilFlushed();

        // Move key/value to a different path, then delete
        map.remove(new TestObjectKey(0));
        map.remove(new TestObjectKey(2));
        map.put(new TestObjectKey(8), new TestValue(8));
        map.put(new TestObjectKey(0), new TestValue(0));
        map.remove(new TestObjectKey(0));

        rootNode = map.getRight();
        rootNode.enableFlush();

        copy = map.copy();
        map.release();
        map = copy;
        rootNode.waitUntilFlushed();

        // During this second flush, key/value 0 must be deleted from the map despite its
        // path in the virtual tree doesn't match the path in the data source
        assertFalse(map.containsKey(new TestObjectKey(0)));
        assertNull(map.get(new TestObjectKey(0)));

        map.release();
    }
}
