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

package com.swirlds.virtualmap.internal.reconnect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualHashBytes;
import com.swirlds.virtualmap.datasource.VirtualLeafBytes;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.hash.VirtualHasher;
import com.swirlds.virtualmap.serialize.KeySerializer;
import com.swirlds.virtualmap.serialize.ValueSerializer;
import com.swirlds.virtualmap.test.fixtures.InMemoryBuilder;
import com.swirlds.virtualmap.test.fixtures.TestKey;
import com.swirlds.virtualmap.test.fixtures.TestKeySerializer;
import com.swirlds.virtualmap.test.fixtures.TestValue;
import com.swirlds.virtualmap.test.fixtures.TestValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReconnectHashListenerTest {

    private static final Cryptography CRYPTO = CryptographyHolder.get();

    private static final KeySerializer<TestKey> keySerializer = new TestKeySerializer();
    private static final ValueSerializer<TestValue> valueSerializer = new TestValueSerializer();

    @Test
    @DisplayName("Null datasource throws")
    void nullDataSourceThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new ReconnectHashListener<TestKey, TestValue>(1, 1, keySerializer, valueSerializer, null, null),
                "A null data source should produce an NPE");
    }

    // Future: We should also check for first/last leaf path being equal when not 1. That really should never happen.
    // That check should be laced through everything, including VirtualMapState.
    @ParameterizedTest
    @CsvSource({
        "-2,  1", // Invalid negative first, good last
        " 1, -2", // Good first, invalid negative last
        " 0,  1", // Invalid zero first, good last
        " 1,  0", // Good first, invalid zero last
        " 0,  0", // Both invalid
        " 9, 8"
    }) // Invalid (both should be equal only if == 1
    @DisplayName("Illegal first and last leaf path combinations throw")
    void badLeafPaths(long firstLeafPath, long lastLeafPath) {
        final VirtualDataSource ds = new InMemoryBuilder().build("badLeafPaths", true);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReconnectHashListener<>(
                        firstLeafPath, lastLeafPath, keySerializer, valueSerializer, ds, null),
                "Should have thrown IllegalArgumentException");
    }

    @ParameterizedTest
    @CsvSource({"-1, -1", " 1,  1", " 1,  2", " 4,  8"})
    @DisplayName("Valid configurations create an instance")
    void goodLeafPaths(long firstLeafPath, long lastLeafPath) {
        final VirtualDataSource ds = new InMemoryBuilder().build("goodLeafPaths", true);
        try {
            new ReconnectHashListener<>(firstLeafPath, lastLeafPath, keySerializer, valueSerializer, ds, null);
        } catch (Exception e) {
            fail("Should have been able to create the instance", e);
        }
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 10, 100, 1000, 10_000, 100_000, 1_000_000})
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Flushed data is always done in the right order")
    void flushOrder(int size) {
        final VirtualDataSourceSpy ds = new VirtualDataSourceSpy(new InMemoryBuilder().build("flushOrder", true));

        final ReconnectNodeRemover<TestKey, TestValue> remover = mock(ReconnectNodeRemover.class);

        // 100 leaves would have firstLeafPath = 99, lastLeafPath = 198
        final long last = size + size;
        final ReconnectHashListener<TestKey, TestValue> listener =
                new ReconnectHashListener<>(size, last, keySerializer, valueSerializer, ds, remover);
        final VirtualHasher<TestKey, TestValue> hasher = new VirtualHasher<>();
        hasher.hash(
                this::hash, LongStream.range(size, last).mapToObj(this::leaf).iterator(), size, last, listener);

        // Now validate that everything showed up the data source in ordered chunks
        final TreeSet<VirtualHashBytes> allInternalRecords =
                new TreeSet<>(Comparator.comparingLong(VirtualHashBytes::path));
        for (List<VirtualHashBytes> internalRecords : ds.internalRecords) {
            allInternalRecords.addAll(internalRecords);
        }

        assertEquals(size + size, allInternalRecords.size(), "Some internal records were not written!");
        long expected = 0;
        for (VirtualHashBytes rec : allInternalRecords) {
            final long path = rec.path();
            assertEquals(expected, path, "Path did not match expectation. path=" + path + ", expected=" + expected);
            expected++;
        }

        final TreeSet<VirtualLeafBytes> allLeafRecords =
                new TreeSet<>(Comparator.comparingLong(VirtualLeafBytes::path));

        for (List<VirtualLeafBytes> leafRecords : ds.leafRecords) {
            allLeafRecords.addAll(leafRecords);
        }

        assertEquals(size, allLeafRecords.size(), "Some leaf records were not written!");
        expected = size;
        for (VirtualLeafBytes rec : allLeafRecords) {
            final long path = rec.path();
            assertEquals(expected, path, "Path did not match expectation. path=" + path + ", expected=" + expected);
            expected++;
        }
    }

    private VirtualLeafRecord<TestKey, TestValue> leaf(long path) {
        return new VirtualLeafRecord<>(path, new TestKey(path), new TestValue(path));
    }

    private Hash hash(long path) {
        return CRYPTO.digestSync(("" + path).getBytes(StandardCharsets.UTF_8));
    }

    private static final class VirtualDataSourceSpy implements VirtualDataSource {

        private final VirtualDataSource delegate;

        private final List<List<VirtualHashBytes>> internalRecords = new ArrayList<>();
        private final List<List<VirtualLeafBytes>> leafRecords = new ArrayList<>();

        VirtualDataSourceSpy(VirtualDataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public void saveRecords(
                final long firstLeafPath,
                final long lastLeafPath,
                @NonNull final Stream<VirtualHashBytes> pathHashRecordsToUpdate,
                @NonNull final Stream<VirtualLeafBytes> leafRecordsToAddOrUpdate,
                @NonNull final Stream<VirtualLeafBytes> leafRecordsToDelete,
                final boolean isReconnectContext)
                throws IOException {
            final var ir = pathHashRecordsToUpdate.toList();
            this.internalRecords.add(ir);
            final var lr = leafRecordsToAddOrUpdate.toList();
            this.leafRecords.add(lr);
            delegate.saveRecords(
                    firstLeafPath, lastLeafPath, ir.stream(), lr.stream(), leafRecordsToDelete, isReconnectContext);
        }

        @Override
        public void saveRecords(
                final long firstLeafPath,
                final long lastLeafPath,
                @NonNull final Stream<VirtualHashBytes> pathHashRecordsToUpdate,
                @NonNull final Stream<VirtualLeafBytes> leafRecordsToAddOrUpdate,
                @NonNull final Stream<VirtualLeafBytes> leafRecordsToDelete)
                throws IOException {

            saveRecords(
                    firstLeafPath,
                    lastLeafPath,
                    pathHashRecordsToUpdate,
                    leafRecordsToAddOrUpdate,
                    leafRecordsToDelete,
                    true);
        }

        @Override
        public VirtualLeafBytes loadLeafRecord(final Bytes key, final int keyHashCode) throws IOException {
            return delegate.loadLeafRecord(key, keyHashCode);
        }

        @Override
        public VirtualLeafBytes loadLeafRecord(final long path) throws IOException {
            return delegate.loadLeafRecord(path);
        }

        @Override
        public long findKey(final Bytes key, final int keyHashCode) throws IOException {
            return delegate.findKey(key, keyHashCode);
        }

        @Override
        public Hash loadHash(final long path) throws IOException {
            return delegate.loadHash(path);
        }

        @Override
        public void snapshot(final Path snapshotDirectory) throws IOException {
            delegate.snapshot(snapshotDirectory);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void copyStatisticsFrom(final VirtualDataSource that) {
            // this database has no statistics
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void registerMetrics(final Metrics metrics) {
            // this database has no statistics
        }

        @Override
        public long getFirstLeafPath() {
            return delegate.getFirstLeafPath();
        }

        @Override
        public long getLastLeafPath() {
            return delegate.getLastLeafPath();
        }

        @Override
        public void enableBackgroundCompaction() {
            // no op
        }

        @Override
        public void stopAndDisableBackgroundCompaction() {
            // no op
        }

        @Override
        @SuppressWarnings("rawtypes")
        public KeySerializer getKeySerializer() {
            throw new UnsupportedOperationException("This method should never be called");
        }

        @Override
        @SuppressWarnings("rawtypes")
        public ValueSerializer getValueSerializer() {
            throw new UnsupportedOperationException("This method should never be called");
        }
    }
}
