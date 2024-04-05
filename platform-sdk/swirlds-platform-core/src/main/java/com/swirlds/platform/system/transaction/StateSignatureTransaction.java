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

    /**
     * Create a state signature transaction. This constructor conforms to the one that will be generated by PBJ.
     *
     * @param round
     * 		The round number of the signed state that this transaction belongs to
     * @param stateSignature
     * 		The byte array of signature of the signed state
     * @param stateHash
     *      The hash that was signed
     * @param epochHash
     *      The hash of the epoch to which this signature corresponds to
     */
    public StateSignatureTransaction(
            final long round,
            @NonNull final Bytes stateSignature,
            @NonNull final Bytes stateHash,
            @NonNull final Bytes epochHash) {
        this(new StateSignaturePayload(
                round,
                stateSignature,
                stateHash,
                epochHash
        ));
    }

    public StateSignatureTransaction(@NonNull final StateSignaturePayload payload){
        this.payload = new OneOf<>(PayloadOneOfType.STATE_SIGNATURE_PAYLOAD, payload);
    }

    // The following 4 methods equate to the ones that will be generated by PBJ once we switch to StateSignaturePayload
    /**
     * @return the round number of the signed state that this transaction belongs to
     */
    public long round() {
        return ((StateSignaturePayload)payload.value()).round();
    }

    /**
     * @return the signature on the state
     */
    @NonNull
    public Bytes signature() {
        return ((StateSignaturePayload)payload.value()).signature();
    }

    /**
     * @return the hash that was signed
     */
    @NonNull
    public Bytes hash() {
        return ((StateSignaturePayload)payload.value()).hash();
    }

    /**
     * @return the hash of the epoch to which this signature corresponds to
     */
    @NonNull
    public Bytes epochHash() {
        return ((StateSignaturePayload)payload.value()).epochHash();
    }
    // End of PBJ generated methods

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
        out.writeInt(Integer.MIN_VALUE);// epochHash is always null
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final byte[] sigBytes = in.readByteArray(SignatureType.RSA.signatureLength());
        final byte[] hashBytes = in.readByteArray(DigestType.SHA_384.digestLength());
        final long round = in.readLong();
        in.readInt();// epochHash is always null

        this.payload = new OneOf<>(PayloadOneOfType.STATE_SIGNATURE_PAYLOAD,
                new StateSignaturePayload(
                round,
                Bytes.wrap(sigBytes),
                Bytes.wrap(hashBytes),
                Bytes.EMPTY
        ));
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
        return Integer.BYTES + (int)signature().length()
                + Integer.BYTES + (int)hash().length()
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
}
