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

import static com.swirlds.common.utility.CommonUtils.throwArgNull;

/**
 * An extended signature which provides both the raw/encoded signature and its coordinate pair (if the signature was
 * generated by an EC algorithm).
 */
public final class ExtendedSignature {

    /**
     * the full signature comprised of the {@code r} and {@code s} coordinates.
     */
    private final byte[] signature;

    /**
     * the elliptical curve {@code r} coordinate. Only present if the {@link #isEllipticalCurve()} method is a {@code
     * true} value.
     */
    private final byte[] r;

    /**
     * the elliptical curve {@code s} coordinate. Only present if the {@link #isEllipticalCurve()} method is a {@code
     * true} value.
     */
    private final byte[] s;

    /**
     * true if the {@code r} and {@code s} coordinates are provided, false otherwise.
     */
    private final boolean ellipticalCurve;

    /**
     * Creates a new instance for a non-EC signature. This constructor uses {@code null} for both the R and S
     * coordinates. When this constructor is used the {@link #isEllipticalCurve()} will always return {@code false}.
     *
     * @param signature
     * 		the raw or encoded signature.
     */
    public ExtendedSignature(final byte[] signature) {
        this(signature, null, null);
    }

    /**
     * Creates a new instance with the provided raw/encoded signature and the raw/encoded (R, S) coordinate pair. If the
     * {@code r} or {@code s} arguments are {@code null} or an empty byte array then the {@link  #isEllipticalCurve()}
     * method will return {@code false}; otherwise, the {@link #isEllipticalCurve()} method will return {@code true}.
     *
     * @param signature
     * 		the raw or encoded signature.
     * @param r
     * 		the raw or encoded R coordinate.
     * @param s
     * 		the raw of encoded S coordinate.
     */
    public ExtendedSignature(final byte[] signature, final byte[] r, final byte[] s) {
        throwArgNull(signature, "signature");

        this.signature = signature;
        this.r = r;
        this.s = s;
        this.ellipticalCurve = (r != null && r.length > 0 && s != null && s.length > 0);
    }

    /**
     * Gets the raw or encoded signature.
     *
     * @return the raw or encoded signature.
     */
    public byte[] getSignature() {
        return signature;
    }

    /**
     * Gets the raw or encoded R coordinate.
     *
     * @return the raw or encoded R coordinate.
     */
    public byte[] getR() {
        return r;
    }

    /**
     * Gets the raw or encoded S coordinate.
     *
     * @return the raw or encoded S coordinate.
     */
    public byte[] getS() {
        return s;
    }

    /**
     * Indicates whether this signature was created by an elliptical curve algorithm, based on the existence of the (R,
     * S) coordinate pair.
     *
     * @return true if the R and S coordinate pair was provided; otherwise false is returned.
     */
    public boolean isEllipticalCurve() {
        return ellipticalCurve;
    }
}
