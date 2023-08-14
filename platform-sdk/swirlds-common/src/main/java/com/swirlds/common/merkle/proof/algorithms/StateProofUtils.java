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

package com.swirlds.common.merkle.proof.algorithms;

import static com.swirlds.common.crypto.DigestType.SHA_384;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.merkle.proof.SignatureVerifier;
import com.swirlds.common.merkle.proof.tree.StateProofInternalNode;
import com.swirlds.common.merkle.proof.tree.StateProofNode;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.MessageDigest;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for validating state proofs.
 */
public final class StateProofUtils {

    private StateProofUtils() {}

    /**
     * Compute the weight of all signatures in a state proof that are valid.
     *
     * @param addressBook       the address book to use for verification, should be a trusted address book
     * @param signatures        the list of signatures to check
     * @param signatureVerifier a method that checks if a signature is valid
     * @param hashBytes         the bytes that were signed (i.e. the hash at the root of the state the proof was derived
     *                          from)
     * @return the total weight of all valid signatures
     */
    public static long computeValidSignatureWeight(
            @NonNull final AddressBook addressBook,
            @NonNull final List<NodeSignature> signatures,
            @NonNull final SignatureVerifier signatureVerifier,
            @NonNull final byte[] hashBytes) {

        final Set<NodeId> signingNodes = new HashSet<>();

        long validWeight = 0;
        for (final NodeSignature nodeSignature : signatures) {
            if (!signingNodes.add(nodeSignature.nodeId())) {
                // Signature is not unique.
                continue;
            }

            if (!addressBook.contains(nodeSignature.nodeId())) {
                // Signature is not in the address book.
                continue;
            }
            final Address address = addressBook.getAddress(nodeSignature.nodeId());
            if (address.getWeight() == 0) {
                // Don't bother validating the signature of a zero weight node.
                continue;
            }

            if (!signatureVerifier.verifySignature(nodeSignature.signature(), hashBytes, address.getSigPublicKey())) {
                // Signature is invalid.
                continue;
            }
            validWeight += address.getWeight();
        }

        return validWeight;
    }

    /**
     * Compute the root hash of a state proof tree.
     *
     * @param cryptography provides cryptographic primitives
     * @param root         the root of the state proof tree
     * @return the computed root hash
     */
    public static byte[] computeStateProofTreeHash(
            @NonNull final Cryptography cryptography, @NonNull final StateProofNode root) {

        final MessageDigest digest = SHA_384.buildDigest();

        // Walk the state proof tree post-ordered depth first order.
        final Deque<StateProofNode> stack = new LinkedList<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            final StateProofNode node = stack.pop();

            if (node instanceof final StateProofInternalNode internal) {
                if (internal.hasBeenVisited()) {
                    // The second time we visit an internal node.
                    // We will have already visited all descendants of this node.
                    node.computeHashableBytes(cryptography, digest);
                } else {
                    // The first time we visit an internal node.
                    // We need to visit its descendants before we can compute its hashable bytes.
                    stack.push(node);
                    for (int childIndex = internal.getChildren().size() - 1; childIndex >= 0; childIndex--) {
                        stack.push(internal.getChildren().get(childIndex));
                    }
                    internal.markAsVisited();
                }
            } else {
                node.computeHashableBytes(cryptography, digest);
            }
        }

        return root.getHashableBytes();
    }
}
