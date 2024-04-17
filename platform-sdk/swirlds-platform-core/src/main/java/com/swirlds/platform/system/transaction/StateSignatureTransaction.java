/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.transaction;

import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.proto.event.EventPayload.PayloadOneOfType;
import com.swirlds.proto.event.StateSignaturePayload;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

/**
 * Every round, the signature of a signed state is put in this transaction
 * and gossiped to other nodes
 */
public final class StateSignatureTransaction extends ConsensusTransactionImpl {

    /**
     * class identifier for the purposes of serialization
     */
    public static final long CLASS_ID = 0xaf7024c653caabf4L;

    private static class ClassVersion {
        public static final int ADDED_EPOCH_HASH = 3;
    }

    private OneOf<PayloadOneOfType> payload;

    /**
     * No-argument constructor used by ConstructableRegistry
     */
    public StateSignatureTransaction() {}

    public StateSignatureTransaction(@NonNull final StateSignaturePayload payload) {
        this.payload = new OneOf<>(PayloadOneOfType.STATE_SIGNATURE_PAYLOAD, payload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSize() {
        return getSerializedLength();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        final StateSignaturePayload stateSignaturePayload = payload.as();

        out.writeInt((int) stateSignaturePayload.signature().length());
        stateSignaturePayload.signature().writeTo(out);

        out.writeInt((int) stateSignaturePayload.hash().length());
        stateSignaturePayload.hash().writeTo(out);

        out.writeLong(stateSignaturePayload.round());
        out.writeInt(Integer.MIN_VALUE); // epochHash is always null
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final byte[] sigBytes = in.readByteArray(SignatureType.RSA.signatureLength());
        final byte[] hashBytes = in.readByteArray(DigestType.SHA_384.digestLength());
        final long round = in.readLong();
        in.readInt(); // epochHash is always null

        this.payload = new OneOf<>(
                PayloadOneOfType.STATE_SIGNATURE_PAYLOAD,
                StateSignaturePayload.newBuilder()
                        .round(round)
                        .signature(Bytes.wrap(sigBytes))
                        .hash(Bytes.wrap(hashBytes))
                        .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.ADDED_EPOCH_HASH;
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
        return ClassVersion.ADDED_EPOCH_HASH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSerializedLength() {
        return Integer.BYTES
                + (int) getStateSignaturePayload().signature().length()
                + Integer.BYTES
                + (int) getStateSignaturePayload().hash().length()
                + Integer.BYTES // epochHash, always null
                + Long.BYTES;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final StateSignatureTransaction that = (StateSignatureTransaction) o;
        return Objects.equals(payload, that.payload);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(payload);
    }

    /**
     * This method just returns null. It is a temporary method that will be removed once we switch to StateSignaturePayload.
     */
    @Override
    public byte[] getContents() {
        return null;
    }

    @Override
    public boolean isSystem() {
        return true;
    }

    @Override
    public OneOf<PayloadOneOfType> getPayload() {
        return payload;
    }

    private StateSignaturePayload getStateSignaturePayload() {
        return payload.as();
    }
}
