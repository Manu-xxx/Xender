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

package com.swirlds.common.merkle.proof;

import static com.swirlds.common.crypto.DigestType.SHA_384;
import static com.swirlds.common.merkle.proof.internal.StateProofTreeBuilder.buildStateProofTree;
import static com.swirlds.common.utility.Threshold.SUPER_MAJORITY;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.proof.internal.StateProofNode;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.Threshold;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A state proof on one or more merkle nodes.
 * <p>
 * Warning: this is an unstable API, and it may be changed and/or removed suddenly and without warning.
 */
public class StateProof implements SelfSerializable {

    private static final long CLASS_ID = 0xbf5d45fc18b63224L;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    /**
     * A signature and the node ID of the signer.
     *
     * @param nodeId    the node ID of the signer
     * @param signature the signature
     */
    private record NodeSignature(@NonNull NodeId nodeId, @NonNull Signature signature)
            implements Comparable<NodeSignature> {
        @Override
        public int compareTo(final NodeSignature o) {
            return nodeId.compareTo(o.nodeId);
        }
    }

    @FunctionalInterface
    interface SignatureValidator {
        boolean verifySignature(@NonNull Signature signature, @NonNull byte[] bytes, @NonNull PublicKey publicKey);
    }

    private List<NodeSignature> signatures;
    private StateProofNode root;
    private List<MerkleLeaf> payloads;

    /**
     * Zero arg constructor required by the serialization framework.
     */
    public StateProof() {}

    /**
     * Create a state proof on the given merkle node.
     *
     * @param cryptography provides cryptographic primitives
     * @param merkleRoot   the root of the merkle tree to create a state proof on
     * @param signatures   signatures on the root hash of the merkle tree
     * @param payloads     one or more leaf nodes to create a state proof on, may not contain null leaves
     */
    public StateProof(
            @NonNull final Cryptography cryptography,
            @NonNull final MerkleNode merkleRoot,
            @NonNull final Map<NodeId, Signature> signatures,
            @NonNull final List<MerkleLeaf> payloads) {

        this.payloads = Objects.requireNonNull(payloads);
        if (payloads.isEmpty()) {
            throw new IllegalArgumentException("payloads must not be empty");
        }
        for (final MerkleLeaf leaf : payloads) {
            if (leaf == null) {
                throw new IllegalArgumentException("payloads are not permitted to contain null leaves");
            }
        }

        Objects.requireNonNull(signatures);
        this.signatures = new ArrayList<>(signatures.size());
        if (signatures.isEmpty()) {
            throw new IllegalArgumentException("signatures must not be empty");
        }
        for (final Map.Entry<NodeId, Signature> entry : signatures.entrySet()) {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException("signatures are not permitted to contain null values");
            }
            this.signatures.add(new NodeSignature(entry.getKey(), entry.getValue()));
        }
        Collections.sort(this.signatures);

        Objects.requireNonNull(merkleRoot);
        root = buildStateProofTree(cryptography, merkleRoot, payloads);
    }

    /**
     * Cryptographically validate this state proof using the {@link Threshold#SUPER_MAJORITY} threshold.
     *
     * @param cryptography provides cryptographic primitives
     * @param addressBook  the address book to use to validate the state proof
     * @return true if this state proof is valid, otherwise false
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    public boolean isValid(@NonNull final Cryptography cryptography, @NonNull final AddressBook addressBook) {
        return isValid(cryptography, addressBook, SUPER_MAJORITY);
    }

    /**
     * Cryptographically validate this state proof using the provided threshold.
     *
     * @param cryptography provides cryptographic primitives
     * @param addressBook  the address book to use to validate the state proof
     * @return true if this state proof is valid, otherwise false
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    public boolean isValid(
            @NonNull final Cryptography cryptography,
            @NonNull final AddressBook addressBook,
            @NonNull final Threshold threshold) {

        return isValid(cryptography, addressBook, threshold, Signature::verifySignature);
    }

    /**
     * Cryptographically validate this state proof using the provided threshold.
     * <p>
     * This method is package private to permit test code to provide custom signature validation.
     *
     * @param cryptography       provides cryptographic primitives
     * @param addressBook        the address book to use to validate the state proof
     * @param signatureValidator a function that verifies a signature
     * @return true if this state proof is valid, otherwise false
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    boolean isValid(
            @NonNull final Cryptography cryptography,
            @NonNull final AddressBook addressBook,
            @NonNull final Threshold threshold,
            @NonNull final SignatureValidator signatureValidator) {

        Objects.requireNonNull(cryptography);
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(threshold);

        final byte[] hashBytes = root.getHashableBytes(cryptography, SHA_384.createHashBuilder());

        final Set<NodeId> signingNodes = new HashSet<>();

        long validWeight = 0;
        for (final NodeSignature nodeSignature : signatures) {
            if (!signingNodes.add(nodeSignature.nodeId)) {
                // Signature is not unique. It does not matter what else is contained by this state
                // proof. This is very clearly malicious behavior, and so we reject the state proof
                // outright.
                return false;
            }

            if (!addressBook.contains(nodeSignature.nodeId)) {
                // Signature is not in the address book. Not necessarily a sign of malicious behavior,
                // since this state proof may have been constructed with a legitimate address book from
                // a different version.
                continue;
            }
            final Address address = addressBook.getAddress(nodeSignature.nodeId);
            if (address.getWeight() == 0) {
                // Don't bother validating the signature of a zero weight node.
                continue;
            }

            // nodeSignature.signature.verifySignature(hashBytes, address.getSigPublicKey())
            if (!signatureValidator.verifySignature(nodeSignature.signature, hashBytes, address.getSigPublicKey())) {
                // Signature is invalid. Not necessarily a sign of malicious behavior,
                // since this state proof may have been constructed with a legitimate address book from
                // a different version.
                continue;
            }
            validWeight += address.getWeight();
        }

        return threshold.isSatisfiedBy(validWeight, addressBook.getTotalWeight());
    }

    /**
     * Get the payloads of this state proof (i.e. the leaf nodes being "proven"). Do not trust the authenticity of these
     * payloads unless {@link #isValid(Cryptography, AddressBook, Threshold)} returns true.
     *
     * @return the payloads of this state proof
     * @throws IllegalStateException if this method is called before this object has been fully deserialized
     */
    @NonNull
    public List<MerkleLeaf> getPayloads() {
        if (payloads == null) {
            throw new IllegalStateException("StateProof has not been fully deserialized");
        }
        return payloads;
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
        out.writeInt(signatures.size());
        for (final NodeSignature entry : signatures) {
            out.writeSerializable(entry.nodeId, false);
            out.writeSerializable(entry.signature, false);
        }

        out.writeSerializable(root, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        final int numSignatures = in.readInt();
        // TODO throw if there are too many signatures
        signatures = new ArrayList<>(numSignatures);
        for (int i = 0; i < numSignatures; i++) {
            final NodeId nodeId = in.readSerializable(false, NodeId::new);
            if (nodeId == null) {
                throw new IOException("nodeId is null");
            }
            final Signature signature = in.readSerializable(false, Signature::new);
            if (signature == null) {
                throw new IOException("signature is null");
            }

            signatures.add(new NodeSignature(nodeId, signature));
        }

        // TODO limit max number of nodes read... or perhaps limit max bytes
        root = in.readSerializable();
        if (root == null) {
            throw new IOException("root is null");
        }

        // TODO think really long and hard if it's possible to spoof a payload!
        payloads = root.getPayloads();
    }
}
