/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.virtualmerkle.map.smartcontracts.data;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.files.hashmap.KeySerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * This class is the serializer for {@link SmartContractMapKey}.
 */
public final class SmartContractMapKeySerializer implements KeySerializer<SmartContractMapKey> {

    private static final long CLASS_ID = 0x2d68463768cf4c59L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSerializedSize(long dataVersion) {
        return SmartContractMapKey.getSizeInBytes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SmartContractMapKey deserialize(final ByteBuffer buffer, final long dataVersion) throws IOException {
        final SmartContractMapKey key = new SmartContractMapKey();
        key.deserialize(buffer, (int) dataVersion);
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int serialize(final SmartContractMapKey data, final SerializableDataOutputStream outputStream)
            throws IOException {
        data.serialize(outputStream);
        return SmartContractMapKey.getSizeInBytes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int deserializeKeySize(final ByteBuffer buffer) {
        return SmartContractMapKey.getSizeInBytes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final ByteBuffer buffer, final int dataVersion, final SmartContractMapKey keyToCompare)
            throws IOException {
        return keyToCompare.equals(buffer, dataVersion);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // nothing to serialize
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // nothing to deserialize
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }
}
