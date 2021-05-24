package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import org.apache.commons.codec.binary.Hex;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;

public class SignatureGenerator {
  /**
   * Signature algorithm
   */
  static final String SIGNATURE_ALGORITHM = "SHA384withECDSA";

  /**
   * Signs a message with a private key.
   *
   * @param msgBytes to be signed
   * @param priv private key
   * @return signature in hex format
   */
  public static String signBytes(byte[] msgBytes, PrivateKey priv)
      throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
    // Create a Signature object and initialize it with the private key
    byte[] sigBytes = null;
    if (priv instanceof EdDSAPrivateKey) {
      EdDSAEngine engine = new EdDSAEngine();
      engine.initSign(priv);
      sigBytes = engine.signOneShot(msgBytes);
    } else {
      Signature sigInstance = Signature.getInstance(SIGNATURE_ALGORITHM);
      sigInstance.initSign(priv);
      // Update and sign the data
      sigInstance.update(msgBytes, 0, msgBytes.length);
      sigBytes = sigInstance.sign();
    }

    String sigHex = Hex.encodeHexString(sigBytes);
    return sigHex;
  }

}
