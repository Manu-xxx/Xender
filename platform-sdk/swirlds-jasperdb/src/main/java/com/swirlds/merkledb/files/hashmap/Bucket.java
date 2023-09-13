/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.files.hashmap;

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.MERKLE_DB;
import static com.swirlds.merkledb.files.hashmap.HalfDiskHashMap.SPECIAL_DELETE_ME_VALUE;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.merkledb.utilities.ProtoUtils;
import com.swirlds.virtualmap.VirtualKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class for accessing the data in a bucket. Each bucket has an index and contains a number
 * of bucket entries. Entries contain key hash codes (as a single bucket may contain keys
 * with different hash codes), values, and full serialized key bytes.
 *
 * <p>This class is not fully thread safe. Buckets may be updated in one thread and then
 * accessed from different threads, this use case is supported. However, buckets aren't
 * designed to be updated concurrently from multiple threads.
 */
public final class Bucket<K extends VirtualKey> implements Closeable {
    private static final Logger logger = LogManager.getLogger(Bucket.class);

    /** Keep track of the bucket with most keys we have ever created for logging */
    private static final AtomicInteger LARGEST_BUCKET_CREATED = new AtomicInteger(0);

    private static final FieldDefinition FIELD_BUCKET_INDEX =
            new FieldDefinition("index", FieldType.UINT32, false, false, false, 1);
    private static final FieldDefinition FIELD_BUCKET_ENTRIES =
            new FieldDefinition("entries", FieldType.MESSAGE, true, false, false, 11);

    private static final FieldDefinition FIELD_BUCKETENTRY_HASHCODE =
            new FieldDefinition("hashCode", FieldType.INT32, false, false, false, 1);
    private static final FieldDefinition FIELD_BUCKETENTRY_VALUE =
            new FieldDefinition("value", FieldType.INT64, false, true, false, 2);
    private static final FieldDefinition FIELD_BUCKETENTRY_KEYBYTES =
            new FieldDefinition("keyBytes", FieldType.BYTES, false, false, false, 3);

    /** Key serializer */
    private final KeySerializer<K> keySerializer;

    /**
     * Bucket pool this bucket is managed by, optional. If not null, the bucket is
     * released back to the pool on close.
     */
    private final ReusableBucketPool<K> bucketPool;

    /** Bucket index */
    private final AtomicInteger bucketIndex = new AtomicInteger(0);

    /** List of bucket entries in this bucket */
    private final List<BucketEntry> entries = new ArrayList<>();

    /**
     * Create a new bucket with the default size.
     *
     * @param keySerializer The serializer responsible for converting keys to/from bytes
     */
    Bucket(final KeySerializer<K> keySerializer) {
        this(keySerializer, null);
    }

    /**
     * Create a new bucket with the default size.
     *
     * @param keySerializer The serializer responsible for converting keys to/from bytes
     */
    Bucket(final KeySerializer<K> keySerializer, final ReusableBucketPool<K> bucketPool) {
        this.keySerializer = keySerializer;
        this.bucketPool = bucketPool;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        if (bucketPool != null) {
            bucketPool.releaseBucket(this);
        }
    }

    /**
     * Reset for next use
     *
     * @return this bucket for each chaining
     */
    public Bucket<K> clear() {
        setBucketIndex(0);
        entries.clear();
        return this;
    }

    public KeySerializer<K> getKeySerializer() {
        return keySerializer;
    }

    /** Get the index for this bucket */
    public int getBucketIndex() {
        return bucketIndex.get();
    }

    /** Set the index for this bucket */
    public void setBucketIndex(int bucketIndex) {
        this.bucketIndex.set(bucketIndex);
    }

    /** Get the number of entries stored in this bucket */
    public int getBucketEntryCount() {
        return entries.size();
    }

    private void checkLargestBucket(final int count) {
        if (!logger.isDebugEnabled(MERKLE_DB.getMarker())) {
            return;
        }
        if (count > LARGEST_BUCKET_CREATED.get()) {
            LARGEST_BUCKET_CREATED.set(count);
            logger.debug(MERKLE_DB.getMarker(), "New largest bucket, now = {} entries", count);
        }
    }

    /** Get the size of this bucket in bytes, including header */
    public int sizeInBytes() {
        int size = 0;
        if (bucketIndex.get() > 0) {
            size += ProtoUtils.sizeOfTag(FIELD_BUCKET_INDEX, ProtoUtils.WIRE_TYPE_VARINT)
                    + ProtoUtils.sizeOfVarInt32(bucketIndex.get());
        }
        for (final BucketEntry entry : entries) {
            size += ProtoUtils.sizeOfDelimited(FIELD_BUCKET_ENTRIES, entry.sizeInBytes());
        }
        return size;
    }

    /**
     * Find a value for given key
     *
     * @param keyHashCode the int hash for the key
     * @param key the key object
     * @param notFoundValue the long to return if the key is not found
     * @return the stored value for given key or notFoundValue if nothing is stored for the key
     * @throws IOException If there was a problem reading the value from file
     */
    public long findValue(final int keyHashCode, final K key, final long notFoundValue) throws IOException {
        final int entryIndex = findEntryIndex(keyHashCode, key);
        if (entryIndex >= 0) {
            // yay! we found it
            return entries.get(entryIndex).getValue();
        } else {
            return notFoundValue;
        }
    }

    /**
     * Put a key/value entry into this bucket.
     *
     * @param key the entry key
     * @param value the entry value, this can also be special
     *     HalfDiskHashMap.SPECIAL_DELETE_ME_VALUE to mean delete
     */
    public void putValue(final K key, final long value) {
        final int keyHashCode = key.hashCode();
        try {
            final int entryIndex = findEntryIndex(keyHashCode, key);
            if (value == SPECIAL_DELETE_ME_VALUE) {
                if (entryIndex >= 0) {
                    entries.remove(entryIndex);
                } else {
                    // entry not found, nothing to delete
                }
                return;
            }
            if (entryIndex >= 0) {
                // yay! we found it, so update value
                final BucketEntry entry = entries.get(entryIndex);
                entry.setValue(value);
                return;
            }
            final BucketEntry newEntry = new BucketEntry(keyHashCode, value, key);
            entries.add(newEntry);

            checkLargestBucket(entries.size());
        } catch (IOException e) {
            logger.error(EXCEPTION.getMarker(), "Failed putting key={} value={} in a bucket", key, value, e);
            throw new UncheckedIOException(e);
        }
    }

    public void readFrom(final ReadableSequentialData in) {
        // defaults
        bucketIndex.set(0);
        entries.clear();

        int entriesCount = 0;
        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int number = tag >> TAG_FIELD_OFFSET;
            if (number == FIELD_BUCKET_INDEX.number()) {
                bucketIndex.set(in.readVarInt(false));
            } else if (number == FIELD_BUCKET_ENTRIES.number()) {
                final int entryBytesSize = in.readVarInt(false);
                final long oldLimit = in.limit();
                in.limit(in.position() + entryBytesSize);
                entries.add(new BucketEntry(in));
                in.limit(oldLimit);
                entriesCount++;
            } else {
                throw new IllegalArgumentException("Unknown bucket field: " + number);
            }
        }

        checkLargestBucket(entriesCount);
    }

    void readFrom(final ByteBuffer buffer) throws IOException {
        bucketIndex.set(buffer.getInt());
        buffer.getInt(); // skip the size
        final int entriesCount = buffer.getInt();
        for (int i = 0; i < entriesCount; i++) {
            entries.add(new BucketEntry(buffer));
        }
    }

    public void writeTo(final WritableSequentialData out) {
        if (bucketIndex.get() > 0) {
            ProtoUtils.writeTag(out, FIELD_BUCKET_INDEX);
            out.writeVarInt(bucketIndex.get(), false);
        }
        for (final BucketEntry entry : entries) {
            ProtoUtils.writeTag(out, FIELD_BUCKET_ENTRIES);
            out.writeVarInt(entry.sizeInBytes(), false);
            entry.writeTo(out);
        }
    }

    void writeTo(final ByteBuffer buffer) throws IOException {
        final int initialPos = buffer.position();
        buffer.putInt(bucketIndex.get());
        buffer.putInt(0); // size, will be updated later
        buffer.putInt(entries.size());
        for (final BucketEntry entry : entries) {
            buffer.putInt(entry.getHashCode());
            buffer.putLong(entry.getValue());
            keySerializer.serialize(entry.getKey(), buffer);
        }
        final int finalPos = buffer.position();
        final int serializedSize = finalPos - initialPos;
        buffer.putInt(initialPos + Integer.BYTES, serializedSize);
    }

    public static long extractKey(final BufferedData bucketBytes) {
        // don't mess up with original buffer
        final BufferedData in = bucketBytes.slice(bucketBytes.position(), bucketBytes.limit());
        while (in.hasRemaining()) {
            final int tag = in.readVarInt(false);
            final int number = tag >> TAG_FIELD_OFFSET;
            if (number == FIELD_BUCKET_INDEX.number()) {
                return in.readVarInt(false);
            } else if (number == FIELD_BUCKET_ENTRIES.number()) {
                final int entryBytesSize = in.readVarInt(false);
                in.skip(entryBytesSize);
            } else {
                throw new IllegalArgumentException("Unknown bucket field: " + number);
            }
        }
        return 0; // default value
    }

    // =================================================================================================================
    // Private API

    private int findEntryIndex(final int keyHashCode, final K key) throws IOException {
        final int entryCount = entries.size();
        for (int index = 0; index < entryCount; index++) {
            final BucketEntry entry = entries.get(index);
            if (keyHashCode == entry.getHashCode()) {
                if (entry.equals(keySerializer, key)) {
                    return index;
                }
            }
        }
        return -1;
    }

    /** toString for debugging */
    @SuppressWarnings("StringConcatenationInsideStringBufferAppend")
    @Override
    public String toString() {
        final int entryCount = getBucketEntryCount();
        final int size = sizeInBytes();
        final StringBuilder sb = new StringBuilder(
                "Bucket{bucketIndex=" + getBucketIndex() + ", entryCount=" + entryCount + ", size=" + size + "\n");
        for (int i = 0; i < entryCount; i++) {
            final BucketEntry entry = entries.get(i);
            final int hashCode = entry.getHashCode();
            final long value = entry.getValue();
            final K key = keySerializer.deserialize(entry.getKeyBytes());
            sb.append("    ENTRY[" + i + "] value= " + value + " keyHashCode=" + hashCode + " key=" + key + "\n");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * A single entry in a bucket, which contains key hash code, value (usually, path), and
     * full serialized key. A bucket may contain multiple such entries.
     *
     * <p>This class would be a record, if it was immutable. However, when a value is updated
     * in a bucket, and a bucket entry already exists for the same key, instead of creating
     * a new entry, we just update the value in the existing entry.
     */
    private class BucketEntry {

        /** Key hash code */
        private int hashCode;
        /** Long value */
        private long value;
        /** Key */
        private K key;
        /** Serialized key */
        private BufferedData keyBytes;

        /** Creates new bucket entry from hash code, value, and serialized key bytes */
        public BucketEntry(final int hashCode, final long value, @NonNull final K key) {
            this.hashCode = hashCode;
            this.value = value;
            this.key = key;
            this.keyBytes = null;
        }

        /** Creates new bucket entry by reading its fields from the given protobuf buffer */
        public BucketEntry(final ReadableSequentialData entryData) {
            // defaults
            hashCode = 0;
            value = 0;
            key = null;
            keyBytes = null;

            // read fields
            while (entryData.hasRemaining()) {
                final int tag = entryData.readVarInt(false);
                final int number = tag >> TAG_FIELD_OFFSET;
                if (number == FIELD_BUCKETENTRY_HASHCODE.number()) {
                    hashCode = entryData.readVarInt(false);
                } else if (number == FIELD_BUCKETENTRY_VALUE.number()) {
                    value = entryData.readVarLong(false);
                } else if (number == FIELD_BUCKETENTRY_KEYBYTES.number()) {
                    final int bytesSize = entryData.readVarInt(false);
                    keyBytes = BufferedData.allocate(bytesSize);
                    entryData.readBytes(keyBytes);
                } else {
                    throw new IllegalArgumentException("Unknown bucket entry field: " + number);
                }
            }

            // check required fields
            if (keyBytes == null) {
                throw new IllegalArgumentException("Null key bytes for bucket entry");
            }
        }

        /** Creates new bucket entry by reading its fields from the given binary buffer */
        public BucketEntry(final ByteBuffer buffer) throws IOException {
            hashCode = buffer.getInt();
            value = buffer.getLong();
            // This is going to be somewhat slow. A possible workaround is to re-introduce
            // KeySerializer.deserializeKeySize() method and use it here to read raw key bytes
            // as a byte buffer. In this case, either entry key is not null, or key bytes as
            // BufferedData is not null, or key bytes as ByteBuffer is not null
            key = keySerializer.deserialize(buffer, 0);
            keyBytes = null;
        }

        public int getHashCode() {
            return hashCode;
        }

        public long getValue() {
            return value;
        }

        public void setValue(long value) {
            this.value = value;
        }

        public K getKey() {
            if (key == null) {
                assert keyBytes != null;
                key = keySerializer.deserialize(keyBytes);
            }
            return key;
        }

        public BufferedData getKeyBytes() {
            if (keyBytes == null) {
                assert key != null;
                final int size = keySerializer.getSerializedSize(key);
                keyBytes = BufferedData.allocate(size);
                keySerializer.serialize(key, keyBytes);
            } else {
                keyBytes.reset();
            }
            return keyBytes;
        }

        public int sizeInBytes() {
            int size = 0;
            size += ProtoUtils.sizeOfTag(FIELD_BUCKETENTRY_HASHCODE, ProtoUtils.WIRE_TYPE_VARINT)
                    + ProtoUtils.sizeOfVarInt32(hashCode);
            if (value != 0) {
                size += ProtoUtils.sizeOfTag(FIELD_BUCKETENTRY_VALUE, ProtoUtils.WIRE_TYPE_VARINT)
                        + ProtoUtils.sizeOfVarInt64(value);
            }
            final BufferedData keyb = getKeyBytes();
            size += ProtoUtils.sizeOfDelimited(FIELD_BUCKETENTRY_KEYBYTES, (int) keyb.capacity());
            return size;
        }

        public void writeTo(final WritableSequentialData out) {
            ProtoUtils.writeTag(out, FIELD_BUCKETENTRY_HASHCODE);
            out.writeVarInt(hashCode, false);
            if (value != 0) {
                ProtoUtils.writeTag(out, FIELD_BUCKETENTRY_VALUE);
                out.writeVarLong(value, false);
            }
            final BufferedData keyb = getKeyBytes();
            ProtoUtils.writeBytes(out, FIELD_BUCKETENTRY_KEYBYTES, (int) keyb.capacity(), o -> o.writeBytes(keyb));
        }

        public boolean equals(final KeySerializer<K> keySerializer, final K key) {
            if (this.key != null) {
                return this.key.equals(key);
            } else {
                assert keyBytes != null;
                keyBytes.reset();
                return keySerializer.equals(keyBytes, key);
            }
        }
    }
}
