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

package com.swirlds.merkledb.files;

import static com.hedera.pbj.runtime.ProtoParserTools.TAG_FIELD_OFFSET;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILEMETADATA_COMPACTION_LEVEL;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILEMETADATA_CREATION_NANOS;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILEMETADATA_CREATION_SECONDS;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILEMETADATA_INDEX;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILEMETADATA_ITEMS_COUNT;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILEMETADATA_ITEM_VERSION;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILE_ITEMS;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILE_METADATA;
import static com.swirlds.merkledb.utilities.ProtoUtils.WIRE_TYPE_FIXED_64_BIT;
import static com.swirlds.merkledb.utilities.ProtoUtils.WIRE_TYPE_VARINT;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.merkledb.utilities.ProtoUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

/**
 * DataFile's metadata that is stored in the data file's footer
 */
@SuppressWarnings("unused")
// Future work: make this class final, once DataFileMetadataJdb is dropped
// See https://github.com/hashgraph/hedera-services/issues/8344 for details
public class DataFileMetadata {

    /**
     * Maximum level of compaction for storage files.
     */
    public static final int MAX_COMPACTION_LEVEL = 127;

    /** The file index, in a data file collection */
    // Future work: make it private final, once this class is final again
    // https://github.com/hashgraph/hedera-services/issues/8344
    protected int index;

    /** The creation date of this file */
    // Future work: make it private final, once this class is final again
    // https://github.com/hashgraph/hedera-services/issues/8344
    protected Instant creationDate;

    /**
     * The number of data items the file contains. When metadata is loaded from a file, the number
     * of items is read directly from there. When metadata is created by {@link DataFileWriter} for
     * new files during flushes or compactions, this field is set to 0 initially and then updated
     * right before the file is finished writing. For such new files, no code needs their metadata
     * until they are fully written, so wrong (zero) item count shouldn't be an issue.
     */
    // Future work: make it private, once this class is final again
    // https://github.com/hashgraph/hedera-services/issues/8344
    protected volatile long itemsCount;

    /** Serialization version for data stored in the file */
    // Future work: make it private final, once this class is final again
    // https://github.com/hashgraph/hedera-services/issues/8344
    protected long serializationVersion;

    /** The level of compaction this file has. See {@link DataFileCompactor}*/
    protected byte compactionLevel;

    // Set in writeTo()
    private long dataItemCountHeaderOffset = 0;

    /**
     * Create a new DataFileMetadata with complete set of data
     *
     * @param itemsCount The number of data items the file contains
     * @param index The file index, in a data file collection
     * @param creationDate The creation data of this file, this is critical as it is used when
     *     merging two files to know which files data is newer.
     * @param serializationVersion Serialization version for data stored in the file
     */
    public DataFileMetadata(
            final long itemsCount,
            final int index,
            final Instant creationDate,
            final long serializationVersion,
            final int compactionLevel) {
        this.itemsCount = itemsCount;
        this.index = index;
        this.creationDate = creationDate;
        this.serializationVersion = serializationVersion;
        assert compactionLevel >= 0 && compactionLevel < MAX_COMPACTION_LEVEL;
        this.compactionLevel = (byte) compactionLevel;
    }

    /**
     * Create a DataFileMetadata loading it from a existing file
     *
     * @param file The file to read metadata from
     * @throws IOException If there was a problem reading metadata footer from the file
     */
    public DataFileMetadata(Path file) throws IOException {
        // Defaults
        int index = 0;
        long creationSeconds = 0;
        int creationNanos = 0;
        long itemsCount = 0;
        long serializationVersion = 0;
        byte compactionLevel = 0;

        // Read values from the file, skipping all data items
        try (final InputStream fin = Files.newInputStream(file, StandardOpenOption.READ)) {
            final ReadableSequentialData in = new ReadableStreamingData(fin);
            in.limit(Files.size(file));
            while (in.hasRemaining()) {
                final int tag = in.readVarInt(false);
                final int fieldNum = tag >> TAG_FIELD_OFFSET;
                if (fieldNum == FIELD_DATAFILE_METADATA.number()) {
                    final int metadataSize = in.readVarInt(false);
                    final long oldLimit = in.limit();
                    in.limit(in.position() + metadataSize);
                    try {
                        while (in.hasRemaining()) {
                            final int metadataTag = in.readVarInt(false);
                            final int metadataFieldNum = metadataTag >> TAG_FIELD_OFFSET;
                            if (metadataFieldNum == FIELD_DATAFILEMETADATA_INDEX.number()) {
                                index = in.readVarInt(false);
                            } else if (metadataFieldNum == FIELD_DATAFILEMETADATA_CREATION_SECONDS.number()) {
                                creationSeconds = in.readVarLong(false);
                            } else if (metadataFieldNum == FIELD_DATAFILEMETADATA_CREATION_NANOS.number()) {
                                creationNanos = in.readVarInt(false);
                            } else if (metadataFieldNum == FIELD_DATAFILEMETADATA_ITEMS_COUNT.number()) {
                                itemsCount = in.readLong();
                            } else if (metadataFieldNum == FIELD_DATAFILEMETADATA_ITEM_VERSION.number()) {
                                serializationVersion = in.readVarLong(false);
                            } else if (metadataFieldNum == FIELD_DATAFILEMETADATA_COMPACTION_LEVEL.number()) {
                                final int compactionLevelInt = in.readVarInt(false);
                                assert compactionLevelInt < MAX_COMPACTION_LEVEL;
                                compactionLevel = (byte) compactionLevelInt;
                            } else {
                                throw new IllegalArgumentException(
                                        "Unknown data file metadata field: " + metadataFieldNum);
                            }
                        }
                    } finally {
                        in.limit(oldLimit);
                    }
                    break;
                } else if (fieldNum == FIELD_DATAFILE_ITEMS.number()) {
                    // Just skip it. By default, metadata is written to the very beginning of the file,
                    // so this code should never be executed. However, with other implementations data
                    // items may come first, this code must be ready to handle it
                    final int size = in.readVarInt(false);
                    in.skip(size);
                } else {
                    throw new IllegalArgumentException("Unknown data file field: " + fieldNum);
                }
            }
        }

        // Initialize this object
        this.index = index;
        this.creationDate = Instant.ofEpochSecond(creationSeconds, creationNanos);
        this.itemsCount = itemsCount;
        this.serializationVersion = serializationVersion;
        this.compactionLevel = compactionLevel;
    }

    void writeTo(final BufferedData out) {
        ProtoUtils.writeDelimited(out, FIELD_DATAFILE_METADATA, fieldsSizeInBytes(), this::writeFields);
    }

    private void writeFields(final WritableSequentialData out) {
        if (getIndex() != 0) {
            ProtoUtils.writeTag(out, FIELD_DATAFILEMETADATA_INDEX);
            out.writeVarInt(getIndex(), false);
        }
        final Instant creationInstant = getCreationDate();
        ProtoUtils.writeTag(out, FIELD_DATAFILEMETADATA_CREATION_SECONDS);
        out.writeVarLong(creationInstant.getEpochSecond(), false);
        ProtoUtils.writeTag(out, FIELD_DATAFILEMETADATA_CREATION_NANOS);
        out.writeVarInt(creationInstant.getNano(), false);
        dataItemCountHeaderOffset = out.position();
        ProtoUtils.writeTag(out, FIELD_DATAFILEMETADATA_ITEMS_COUNT);
        out.writeLong(0); // will be updated later
        if (getSerializationVersion() != 0) {
            ProtoUtils.writeTag(out, FIELD_DATAFILEMETADATA_ITEM_VERSION);
            out.writeVarLong(getSerializationVersion(), false);
        }
        if (getCompactionLevel() != 0) {
            ProtoUtils.writeTag(out, FIELD_DATAFILEMETADATA_COMPACTION_LEVEL);
            out.writeVarInt(compactionLevel, false);
        }
    }

    /**
     * Get the number of data items the file contains. If this method is called before the
     * corresponding file is completely written by {@link DataFileWriter}, the return value is 0.
     */
    public long getDataItemCount() {
        return itemsCount;
    }

    /**
     * Updates number of data items in the file. This method must be called after metadata is
     * written to a file using {@link #writeTo(BufferedData)}.
     *
     * <p>This method is called by {@link DataFileWriter} right before the file is finished writing.
     */
    void updateDataItemCount(final BufferedData out, final long count) {
        this.itemsCount = count;
        assert dataItemCountHeaderOffset != 0;
        out.position(dataItemCountHeaderOffset);
        ProtoUtils.writeTag(out, FIELD_DATAFILEMETADATA_ITEMS_COUNT);
        out.writeLong(count);
    }

    /** Get the files index, out of a set of data files */
    public int getIndex() {
        return index;
    }

    /** Get the date the file was created in UTC */
    public Instant getCreationDate() {
        return creationDate;
    }

    /** Get the serialization version for data stored in this file */
    public long getSerializationVersion() {
        return serializationVersion;
    }

    // For testing purposes. In low-level data file tests, skip this number of bytes from the
    // beginning of the file before reading data items, assuming file metadata is always written
    // first, then data items
    int metadataSizeInBytes() {
        return ProtoUtils.sizeOfDelimited(FIELD_DATAFILE_METADATA, fieldsSizeInBytes());
    }

    private int fieldsSizeInBytes() {
        int size = 0;
        if (index != 0) {
            size += ProtoUtils.sizeOfTag(FIELD_DATAFILEMETADATA_INDEX, WIRE_TYPE_VARINT);
            size += ProtoUtils.sizeOfVarInt32(index);
        }
        size += ProtoUtils.sizeOfTag(FIELD_DATAFILEMETADATA_CREATION_SECONDS, WIRE_TYPE_VARINT);
        size += ProtoUtils.sizeOfVarInt64(creationDate.getEpochSecond());
        size += ProtoUtils.sizeOfTag(FIELD_DATAFILEMETADATA_CREATION_NANOS, WIRE_TYPE_VARINT);
        size += ProtoUtils.sizeOfVarInt64(creationDate.getNano());
        size += ProtoUtils.sizeOfTag(FIELD_DATAFILEMETADATA_ITEMS_COUNT, WIRE_TYPE_FIXED_64_BIT);
        size += Long.BYTES;
        if (serializationVersion != 0) {
            size += ProtoUtils.sizeOfTag(FIELD_DATAFILEMETADATA_ITEM_VERSION, WIRE_TYPE_VARINT);
            size += ProtoUtils.sizeOfVarInt64(serializationVersion);
        }
        if (compactionLevel != 0) {
            size += ProtoUtils.sizeOfTag(FIELD_DATAFILEMETADATA_COMPACTION_LEVEL, WIRE_TYPE_VARINT);
            size += ProtoUtils.sizeOfVarInt32(compactionLevel);
        }
        return size;
    }

    public int getCompactionLevel() {
        return compactionLevel;
    }

    /** toString for debugging */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("itemsCount", itemsCount)
                .append("index", index)
                .append("creationDate", creationDate)
                .append("serializationVersion", serializationVersion)
                .toString();
    }

    /**
     * Equals for use when comparing in collections, based on all fields in the toString() output.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataFileMetadata that = (DataFileMetadata) o;
        return itemsCount == that.itemsCount
                && index == that.index
                && serializationVersion == that.serializationVersion
                && compactionLevel == that.compactionLevel
                && Objects.equals(this.creationDate, that.creationDate);
    }

    /**
     * hashCode for use when comparing in collections, based on all fields in the toString() output.
     */
    @Override
    public int hashCode() {
        return Objects.hash(itemsCount, index, creationDate, serializationVersion, compactionLevel);
    }
}
