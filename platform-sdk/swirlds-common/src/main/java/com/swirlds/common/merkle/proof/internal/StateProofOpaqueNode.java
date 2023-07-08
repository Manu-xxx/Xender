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

package com.swirlds.common.merkle.proof.internal;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.List;

/**
 * A leaf in a state proof tree. Contains data that modifies the hash. Data is opaque, meaning that it is not intended
 * to be interpreted in any meaningful way other than how it modifies the hash.
 */
public class StateProofOpaqueNode implements StateProofNode {

    private static final long CLASS_ID = 0x4ab3834aaba6fbbdL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private byte[] data;

    /**
     * Zero arg constructor required by the serialization framework.
     */
    public StateProofOpaqueNode() {}

    /**
     * Construct a new leaf node with the given bytes.
     *
     * @param byteSegments the opaque data, used only for hash computation. It is split into multiple byte arrays as an
     *                     artifact of the way the data is gathered.
     */
    public StateProofOpaqueNode(@NonNull final List<byte[]> byteSegments) {

        int totalSize = 0;
        for (byte[] segment : byteSegments) {
            totalSize += segment.length;
        }

        this.data = new byte[totalSize];

        // Combine the segments into a unified array
        int offset = 0;
        for (final byte[] segment : byteSegments) {
            System.arraycopy(segment, 0, data, offset, segment.length);
            offset += segment.length;
        }
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public byte[] getHashableBytes(@NonNull final Cryptography cryptography, @NonNull final HashBuilder hashBuilder) {
        if (data == null) {
            throw new IllegalStateException("StateProofOpaqueData has not been properly initialized");
        }

        return data;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public List<MerkleLeaf> getPayloads() {
        if (data == null) {
            throw new IllegalStateException("StateProofOpaqueData has not been properly initialized");
        }
        // no payloads here :)
        return List.of();
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
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeByteArray(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        data = in.readByteArray(Integer.MAX_VALUE); // TODO use sane upper limit
    }
}
