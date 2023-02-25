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

package com.swirlds.demo.stats.signing.algorithms;

import com.swirlds.common.crypto.SignatureType;
import java.security.SignatureException;

/**
 * Defines the interface to which all signature producers must adhere.
 */
public interface SigningAlgorithm {

    /**
     * Gets a unique algorithm identifier.
     *
     * @return the unique identifier for this algorithm.
     */
    byte getId();

    /**
     * Gets the raw public key as a byte array.
     *
     * @return the raw public key bytes.
     */
    byte[] getPublicKey();

    /**
     * Gets the private key as a byte array.
     *
     * @return the raw or encoded private key bytes.
     */
    byte[] getPrivateKey();

    /**
     * Signs the data contained in the buffer using the private key obtained from the {@link #getPrivateKey()} method.
     * The {@code offset} and {@code len} arguments must be within the bounds of the provided {@code buffer} array.
     *
     * @param buffer
     * 		an array containing the data to be signed.
     * @param offset
     * 		starting position from which to begin reading in the {@code buffer} array.
     * @param len
     * 		the number of array elements to be read.
     * @return a byte array containing the raw or encoded signature.
     * @throws SignatureException
     * 		if this signature object is not initialized properly or if this signature algorithm is unable to process the
     * 		input data provided.
     * @throws ArrayIndexOutOfBoundsException
     * 		if the {@code offset} or {@code len} arguments refer to invalid locations or exceed the size of the provided
     * 		array.
     */
    byte[] sign(byte[] buffer, int offset, int len) throws SignatureException;

    /**
     * Signs the data contained in the buffer using the private key obtained from the {@link #getPrivateKey()} method.
     * The {@code offset} and {@code len} arguments must be within the bounds of the provided {@code buffer} array.
     *
     * This version returns an {@link ExtendedSignature} instance containing the same raw or encoded signature as
     * returned by the {@link #sign(byte[], int, int)} method but also includes the raw signature components for an
     * elliptical curve based signature.
     *
     * @param buffer
     * 		an array containing the data to be signed.
     * @param offset
     * 		starting position from which to begin reading in the {@code buffer} array.
     * @param len
     * 		the number of array elements to be read.
     * @return a byte array containing the raw or encoded signature.
     * @throws SignatureException
     * 		if this signature object is not initialized properly or if this signature algorithm is unable to process the
     * 		input data provided.
     * @throws ArrayIndexOutOfBoundsException
     * 		if the {@code offset} or {@code len} arguments refer to invalid locations or exceed the size of the provided
     * 		array.
     */
    ExtendedSignature signEx(byte[] buffer, int offset, int len) throws SignatureException;

    /**
     * Called during initialization to ensure that all necessary cryptographic constructs have been created and
     * initialized. This method is only called once; however, implementations should ensure that is idempotent. This
     * method should (in most cases) also create the Public/Private key pair.
     */
    void tryAcquirePrimitives();

    /**
     * Indicates whether this instance is properly initialized and is able to sign data.
     *
     * @return true if this instance is initialized and ready to sign data, false otherwise.
     */
    boolean isAvailable();

    /**
     * Gets the fixed or maximum length (in bytes) of the signatures produced by this algorithm.
     *
     * @return the exact or maximum length (in bytes) of the signature.
     */
    int getSignatureLength();

    /**
     * Gets the length (in bytes) of the public key.
     *
     * @return the length (in bytes) of the public key.
     */
    int getPublicKeyLength();

    /**
     * Gets the length (in bytes) of a single elliptical curve coordinate. This method may return zero (0) for non-EC
     * algorithms.
     *
     * @return the length (in bytes) of a signle elliptical curve coordinate.
     */
    int getCoordinateSize();

    /**
     * Gets the {@link SignatureType} enumeration value describing the algorithm this instance implements.
     *
     * @return the enumeration value describing the algorithm this instance implements.
     */
    SignatureType getSignatureType();
}
