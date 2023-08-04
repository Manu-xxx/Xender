package com.swirlds.platform.crypto;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.security.PublicKey;

/**
 * Verifies signatures
 */
@FunctionalInterface
public interface SignatureVerifier {
	/**
	 * check whether the given signature is valid
	 *
	 * @param data
	 * 		the data that was signed
	 * @param signature
	 * 		the claimed signature of that data
	 * @param publicKey
	 * 		the claimed public key used to generate that signature
	 * @return true if the signature is valid
	 */
	boolean verifySignature(@NonNull byte[] data, @NonNull byte[] signature, @NonNull PublicKey publicKey);
}
