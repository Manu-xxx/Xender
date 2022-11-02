package com.hedera.services.state.virtual;

import com.hedera.services.state.virtual.entities.OnDiskTokenRel;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.merkledb.serialize.ValueSerializer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

public class OnDiskTokenRelMerkleDbValueSerializer implements ValueSerializer<OnDiskTokenRel> {

    // Serializer class ID
    static final long CLASS_ID = 0x0e52cff909625f56L;

    // Serializer version
    static final int CURRENT_VERSION = 1;

    // Data version
    static final int DATA_VERSION = 1;

    // Serializer info

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    // Data version

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return Byte.BYTES // flags;
                + Long.BYTES // prev
                + Long.BYTES // next
                + Long.BYTES // balance
                + Long.BYTES; // numbers
    }

    // Value serialization

    @Override
    public int serialize(final OnDiskTokenRel value, final SerializableDataOutputStream out) throws IOException {
        Objects.requireNonNull(value);
        Objects.requireNonNull(out);
        value.serialize(out);
        return getSerializedSize();
    }

    // Value deserialization

    @Override
    public OnDiskTokenRel deserialize(final ByteBuffer buffer, final long version) throws IOException {
        Objects.requireNonNull(buffer);
        final OnDiskTokenRel value = new OnDiskTokenRel();
        value.deserialize(buffer, (int) version);
        return value;
    }
}
