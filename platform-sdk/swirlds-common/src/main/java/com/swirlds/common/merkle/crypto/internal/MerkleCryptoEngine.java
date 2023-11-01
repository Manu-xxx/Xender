/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.merkle.crypto.internal;

import static com.swirlds.common.merkle.utility.MerkleConstants.MERKLE_DIGEST_TYPE;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.hash.MerkleHashBuilder;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.logging.legacy.LogMarker;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

public class MerkleCryptoEngine implements MerkleCryptography {

    /**
     * The digest provider instance that is used to generate hashes of MerkleInternal objects.
     */
    private final MerkleInternalDigestProvider merkleInternalDigestProvider;

    /**
     * The merkle provider used to compute digests for merkle trees.
     */
    private final MerkleHashBuilder merkleHashBuilder;

    private final Cryptography basicCryptoEngine;

    /**
     * Create a new merkle crypto engine.
     *
     * @param threadManager
     * 		responsible for thread lifecycle management
     * @param cryptography
     * 		provides cryptographic primitives
     * @param settings
     * 		provides settings for cryptography
     */
    public MerkleCryptoEngine(
            final ThreadManager threadManager, final Cryptography cryptography, final CryptoConfig settings) {
        basicCryptoEngine = cryptography;
        this.merkleInternalDigestProvider = new MerkleInternalDigestProvider();
        this.merkleHashBuilder =
                new MerkleHashBuilder(threadManager, this, cryptography, settings.computeCpuDigestThreadCount());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash digestTreeSync(final MerkleNode root, final DigestType digestType) {
        return merkleHashBuilder.digestTreeSync(root);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Future<Hash> digestTreeAsync(final MerkleNode root, final DigestType digestType) {
        return merkleHashBuilder.digestTreeAsync(root);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash digestSync(final MerkleInternal node, final List<Hash> childHashes, final boolean setHash) {
        try {
            final Hash hash = merkleInternalDigestProvider.compute(node, childHashes, MERKLE_DIGEST_TYPE);
            if (setHash) {
                node.setHash(hash);
            }
            return hash;
        } catch (final NoSuchAlgorithmException e) {
            throw new CryptographyException(e, LogMarker.EXCEPTION);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash digestSync(final MerkleInternal node, final DigestType digestType, boolean setHash) {
        List<Hash> childHashes = new ArrayList<>(node.getNumberOfChildren());
        for (int childIndex = 0; childIndex < node.getNumberOfChildren(); childIndex++) {
            MerkleNode child = node.getChild(childIndex);
            if (child == null) {
                childHashes.add(basicCryptoEngine.getNullHash(digestType));
            } else {
                childHashes.add(child.getHash());
            }
        }
        return digestSync(node, childHashes, setHash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash digestSync(MerkleLeaf leaf, DigestType digestType) {
        return basicCryptoEngine.digestSync(leaf, digestType);
    }
}
