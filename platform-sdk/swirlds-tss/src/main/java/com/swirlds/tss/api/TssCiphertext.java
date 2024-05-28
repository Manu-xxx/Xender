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

package com.swirlds.tss.api;

import com.swirlds.pairings.ecdh.EcdhPrivateKey;
import com.swirlds.signaturescheme.api.PairingPublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A ciphertext produced by a single node.
 *
 * @param <P> the type of public key that can be used to verify signatures produced by the secret keys encrypted in this
 *            ciphertext
 */
public interface TssCiphertext<P extends PairingPublicKey> {
    /**
     * Extract the private key data from this ciphertext.
     * <p>
     * The private key decrypted by this method is not the final private key. Rather, it is a partial private key.
     *
     * @param ecdhPrivateKey the private key of the node that is extracting the private shares
     * @param shareId        the ID of the private key to decrypt
     * @return the private key decrypted from this ciphertext
     */
    @NonNull
    TssPrivateKey<P> decryptPrivateKey(@NonNull final EcdhPrivateKey ecdhPrivateKey, @NonNull TssShareId shareId);

    /**
     * Serialize this ciphertext to bytes.
     *
     * @return the serialized ciphertext
     */
    byte[] toBytes();
}
