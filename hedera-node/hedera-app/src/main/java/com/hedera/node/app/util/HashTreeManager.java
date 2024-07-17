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

package com.hedera.node.app.util;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is responsible for managing the streaming hashes of a Tree.
 * It is used to calculate the root hash of the Tree.
 */
public class HashTreeManager<T> {
    private final String HASHING_ALGO = "SHA-384"; // The hashing algorithm used
    private List<Bytes> hashList = new ArrayList<>(); // The list of hashes in the tree
    private final Codec<T> codec; // The codec used to encode and decode the elements in the tree
    private final MessageDigest digest; // The message digest

    /**
     * Constructor for the HashTreeManager class.
     * @param codec The codec used to encode and decode the elements in the tree
     * @throws NoSuchAlgorithmException If the hashing algorithm is not found
     */
    public HashTreeManager(Codec<T> codec) throws NoSuchAlgorithmException {
        this.codec = codec;
        this.digest = MessageDigest.getInstance(HASHING_ALGO);
    }

    /** Add an element to the Tree
     * @param element The element to add to the tree - This could just be a Block Item
     */
    public void addElement(T element) {
        Bytes encodedElement = codec.toBytes(element); // Use the codec to encode the element
        Bytes hashedElement = Bytes.wrap(digest.digest(encodedElement.toByteArray()));
        hashList.add(hashedElement);
        rebalanceTree();
    }

    public void addElements(List<T> elements) throws NoSuchAlgorithmException {
        for (T element : elements) {
            Bytes encodedElement = codec.toBytes(element);
            Bytes hashedElement = Bytes.wrap(digest.digest(encodedElement.toByteArray()));
            hashList.add(hashedElement);
        }
        rebalanceTree();
    }

    private void rebalanceTree() {
        while (hashList.size() > 1) {
            if (hashList.size() % 2 != 0) {
                hashList.add(hashList.get(hashList.size() - 1));
            }
            List<Bytes> newHashList = new ArrayList<>();
            for (int i = 0; i < hashList.size(); i += 2) {
                Bytes x = hashList.get(i);
                Bytes y = hashList.get(i + 1);
                newHashList.add(hash(x.append(y), digest));
            }
            hashList = newHashList;
        }
    }

    /** Hash the base bytes using the given message digest
     * @param base The base bytes to hash
     * @param digest The message digest to use
     * @return The hashed bytes
     */
    public Bytes hash(Bytes base, MessageDigest digest) {
        byte[] encodedHash = digest.digest(base.toByteArray());
        return Bytes.wrap(encodedHash);
    }

    public Bytes getTreeRoot() {
        if (hashList.isEmpty()) {
            return null;
        }
        rebalanceTree();
        return hashList.get(0); // The last remaining element is the tree root
    }

    public String getTreeRootAsString() {
        Bytes treeRoot = getTreeRoot();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < treeRoot.length(); i++) {
            byte b = treeRoot.getByte(i);
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void setHeader(Bytes bytes) {}
    // ----------------- part of "closing" the block, implement in separate ticket
    public void combineTreesAndComputeHash() {}
}
