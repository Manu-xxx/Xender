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

import static com.swirlds.common.merkle.proof.algorithms.StateProofSerialization.deserializeSignatures;
import static com.swirlds.common.merkle.proof.algorithms.StateProofSerialization.deserializeStateProofTree;
import static com.swirlds.common.merkle.proof.algorithms.StateProofSerialization.extractPayloads;
import static com.swirlds.common.merkle.proof.algorithms.StateProofSerialization.serializeSignatures;
import static com.swirlds.common.merkle.proof.algorithms.StateProofSerialization.serializeStateProofTree;
import static com.swirlds.common.merkle.proof.algorithms.StateProofTreeBuilder.buildStateProofTree;
import static com.swirlds.common.merkle.proof.algorithms.StateProofTreeBuilder.processSignatures;
import static com.swirlds.common.merkle.proof.algorithms.StateProofTreeBuilder.validatePayloads;
import static com.swirlds.common.merkle.proof.algorithms.StateProofValidator.computeStateProofTreeHash;
import static com.swirlds.common.merkle.proof.algorithms.StateProofValidator.computeValidSignatureWeight;
import static com.swirlds.common.units.DataUnit.UNIT_BYTES;
import static com.swirlds.common.units.DataUnit.UNIT_MEGABYTES;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.proof.algorithms.NodeSignature;
import com.swirlds.common.merkle.proof.algorithms.SignatureValidator;
import com.swirlds.common.merkle.proof.tree.StateProofNode;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.utility.Threshold;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * The maximum number of signatures supported by deserialization.
     */
    public static final int MAX_SIGNATURE_COUNT = 1024;

    /**
     * The maximum number of children a state proof node is permitted to have.
     */
    public static final int MAX_CHILD_COUNT = 64;

    /**
     * The maximum size of the state proof tree supported by deserialization, in bytes. This constant is chosen to be
     * sufficiently large as to be unlikely to be reached in practice, but small enough to prevent memory exhaustion in
     * the event of an attack.
     */
    public static final long MAX_STATE_PROOF_TREE_SIZE = (long) UNIT_MEGABYTES.convertTo(64, UNIT_BYTES);

    private List<NodeSignature> signatures;
    private StateProofNode root;
    private List<MerkleLeaf> payloads;

    private byte[] hashBytes;

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

        Objects.requireNonNull(cryptography);
        Objects.requireNonNull(merkleRoot);
        Objects.requireNonNull(signatures);
        Objects.requireNonNull(payloads);

        this.payloads = validatePayloads(payloads);
        this.signatures = processSignatures(signatures);
        this.root = buildStateProofTree(cryptography, merkleRoot, payloads);
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
        Objects.requireNonNull(signatureValidator);

        if (hashBytes == null) {
            // we only need to recompute the hash once
            hashBytes = computeStateProofTreeHash(cryptography, root);
        }
        long validWeight = computeValidSignatureWeight(addressBook, signatures, signatureValidator, hashBytes);
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
        serializeSignatures(out, signatures);
        serializeStateProofTree(out, root);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        signatures = deserializeSignatures(in);
        root = deserializeStateProofTree(in);
        payloads = extractPayloads(root);
    }
}
