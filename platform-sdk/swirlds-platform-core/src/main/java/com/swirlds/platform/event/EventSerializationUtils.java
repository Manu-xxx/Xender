/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.platform.event.EventTransaction.TransactionOneOfType;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.StaticSoftwareVersion;
import com.swirlds.platform.system.events.EventDescriptorWrapper;
import com.swirlds.platform.system.events.UnsignedEvent;
import com.swirlds.platform.system.events.UnsignedEvent.ClassVersion;
import com.swirlds.platform.util.TransactionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class EventSerializationUtils {
    private EventSerializationUtils() {
        // Utility class
    }

    private static final long EVENT_CLASS_ID = 0x21c2620e9b6a2243L;

    private static final long APPLICATION_TRANSACTION_CLASS_ID = 0x9ff79186f4c4db97L;
    private static final int APPLICATION_TRANSACTION_VERSION = 1;
    private static final long STATE_SIGNATURE_CLASS_ID = 0xaf7024c653caabf4L;
    private static final int STATE_SIGNATURE_VERSION = 3;

    /**
     * Serialize a event to the output stream {@code out}.
     *
     * @param out the stream to which this object is to be written
     * @param softwareVersion the software version
     * @param eventCore the event core
     * @param selfParent the self parent
     * @param otherParents the other parents
     * @param eventTransactions the event transactions
     *
     * @throws IOException if an I/O error occurs
     */
    public static void serializeEvent(
            @NonNull final SerializableDataOutputStream out,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final EventCore eventCore,
            @NonNull final EventDescriptorWrapper selfParent,
            @NonNull final List<EventDescriptorWrapper> otherParents,
            @NonNull final List<EventTransaction> eventTransactions)
            throws IOException {
        out.writeInt(ClassVersion.BIRTH_ROUND);
        out.writeSerializable(softwareVersion, true);
        out.writeInt(NodeId.ClassVersion.ORIGINAL);
        out.writeLong(eventCore.creatorNodeId());
        EventDescriptorWrapper.serialize(out, selfParent);
        EventDescriptorWrapper.serializeList(out, otherParents);
        out.writeLong(eventCore.birthRound());
        out.writeInstant(HapiUtils.asInstant(eventCore.timeCreated()));

        // write serialized length of transaction array first, so during the deserialization proces
        // it is possible to skip transaction array and move on to the next object
        out.writeInt(TransactionUtils.getLegacyObjectSize(eventTransactions));
        // transactions may include both system transactions and application transactions
        // so writeClassId set to true and allSameClass set to false
        out.writeInt(eventTransactions.size());
        if (!eventTransactions.isEmpty()) {
            final boolean allSameClass = false;
            out.writeBoolean(allSameClass);
        }
        for (final EventTransaction transaction : eventTransactions) {
            switch (transaction.transaction().kind()) {
                case APPLICATION_TRANSACTION:
                    serializeApplicationTransaction(out, transaction);
                    break;
                case STATE_SIGNATURE_TRANSACTION:
                    serializeStateSignatureTransaction(out, transaction);
                    break;
                default:
                    throw new IOException("Unknown transaction type: "
                            + transaction.transaction().kind());
            }
        }
    }

    private static void serializeApplicationTransaction(
            @NonNull final SerializableDataOutputStream out, @NonNull final EventTransaction transaction)
            throws IOException {
        out.writeLong(APPLICATION_TRANSACTION_CLASS_ID);
        out.writeInt(APPLICATION_TRANSACTION_VERSION);
        final Bytes bytes = transaction.transaction().as();
        out.writeInt((int) bytes.length());
        bytes.writeTo(out);
    }

    private static void serializeStateSignatureTransaction(
            @NonNull final SerializableDataOutputStream out, @NonNull final EventTransaction transaction)
            throws IOException {
        final StateSignatureTransaction stateSignatureTransaction =
                transaction.transaction().as();

        out.writeLong(STATE_SIGNATURE_CLASS_ID);
        out.writeInt(STATE_SIGNATURE_VERSION);
        out.writeInt((int) stateSignatureTransaction.signature().length());
        stateSignatureTransaction.signature().writeTo(out);

        out.writeInt((int) stateSignatureTransaction.hash().length());
        stateSignatureTransaction.hash().writeTo(out);

        out.writeLong(stateSignatureTransaction.round());
        out.writeInt(Integer.MIN_VALUE); // epochHash is always null
    }

    /**
     * Deserialize the event as {@link UnsignedEvent}.
     *
     * @param in the stream from which this object is to be read
     * @return the deserialized event
     * @throws IOException if unsupported transaction types are encountered
     */
    @NonNull
    public static UnsignedEvent deserializeUnsignedEvent(@NonNull final SerializableDataInputStream in)
            throws IOException {
        final int version = in.readInt();
        if (version != ClassVersion.BIRTH_ROUND) {
            throw new IOException("Unsupported version: " + version);
        }

        Objects.requireNonNull(in, "The input stream must not be null");
        final SoftwareVersion softwareVersion =
                in.readSerializable(StaticSoftwareVersion.getSoftwareVersionClassIdSet());

        final NodeId creatorId = in.readSerializable(false, NodeId::new);
        if (creatorId == null) {
            throw new IOException("creatorId is null");
        }
        final EventDescriptorWrapper selfParent = EventDescriptorWrapper.deserialize(in);
        final List<EventDescriptorWrapper> otherParents = EventDescriptorWrapper.deserializeList(in);
        final long birthRound = in.readLong();

        final Instant timeCreated = in.readInstant();
        in.readInt(); // read serialized length
        final List<EventTransaction> transactionList = new ArrayList<>();
        final int transactionSize = in.readInt();
        if (transactionSize > 0) {
            in.readBoolean(); // allSameClass
        }
        for (int i = 0; i < transactionSize; i++) {
            final long classId = in.readLong();
            final int classVersion = in.readInt();
            if (classId == APPLICATION_TRANSACTION_CLASS_ID) {
                final OneOf<TransactionOneOfType> oneOf = new OneOf<>(
                        TransactionOneOfType.APPLICATION_TRANSACTION,
                        deserializeApplicationTransaction(in, classVersion));
                transactionList.add(new EventTransaction(oneOf));
            } else if (classId == STATE_SIGNATURE_CLASS_ID) {
                final OneOf<TransactionOneOfType> oneOf = new OneOf<>(
                        TransactionOneOfType.STATE_SIGNATURE_TRANSACTION,
                        deserializeStateSignatureTransaction(in, classVersion));
                transactionList.add(new EventTransaction(oneOf));
            } else {
                throw new IOException("Unknown classId: " + classId);
            }
        }

        return new UnsignedEvent(
                softwareVersion, creatorId, selfParent, otherParents, birthRound, timeCreated, transactionList);
    }

    @Nullable
    private static Bytes deserializeApplicationTransaction(
            @NonNull final SerializableDataInputStream in, final int classVersion) throws IOException {
        if (classVersion != APPLICATION_TRANSACTION_VERSION) {
            throw new IOException("Unsupported application class version: " + classVersion);
        }
        final byte[] bytes = in.readByteArray(1000000);

        if (bytes != null) {
            return Bytes.wrap(bytes);
        }
        return null;
    }

    @NonNull
    private static StateSignatureTransaction deserializeStateSignatureTransaction(
            SerializableDataInputStream in, int classVersion) throws IOException {
        if (classVersion != STATE_SIGNATURE_VERSION) {
            throw new IOException("Unsupported state signature class version: " + classVersion);
        }
        final byte[] sigBytes = in.readByteArray(1000000);
        final byte[] hashBytes = in.readByteArray(1000000);
        final long round = in.readLong();
        in.readInt(); // epochHash is always null
        return new StateSignatureTransaction(round, Bytes.wrap(sigBytes), Bytes.wrap(hashBytes));
    }
}
