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

package com.swirlds.pairings.bls12381.impl;

import static com.swirlds.pairings.bls12381.impl.Bls12381Bindings.SUCCESS;
import static com.swirlds.pairings.bls12381.impl.Bls12381Bindings.g2Compress;
import static com.swirlds.pairings.bls12381.impl.Bls12381Bindings.g2Divide;
import static com.swirlds.pairings.bls12381.impl.Bls12381Bindings.g2Multiply;
import static com.swirlds.pairings.bls12381.impl.Bls12381Bindings.g2PowZn;

import com.swirlds.pairings.api.FieldElement;
import com.swirlds.pairings.api.Group;
import com.swirlds.pairings.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;

/**
 * An element in G1 group used in BLS12-381
 */
public class Bls12381Group2Element implements GroupElement {
    private static final Bls12381Group2 GROUP = Bls12381Group2.getInstance();

    /** The bytes representation of the element */
    private byte[] groupElement;

    /** True if the {@link #groupElement} bytes are in a compressed form, otherwise false */
    private boolean compressed;

    /**
     * Package private constructor. This is used by {@link Bls12381Group2}, but shouldn't be called
     * directly by anyone else
     *
     * @param groupElement a byte array representing this group element
     */
    Bls12381Group2Element(final byte[] groupElement) {
        if (groupElement == null) {
            throw new IllegalArgumentException("groupElement parameter must not be null");
        }

        this.groupElement = groupElement;
        this.compressed = groupElement.length == GROUP.getCompressedSize();
    }

    /**
     * Package private copy constructor
     *
     * @param other the object being copied
     */
    Bls12381Group2Element(final Bls12381Group2Element other) {
        if (other == null) {
            throw new IllegalArgumentException("other cannot be null");
        }

        this.groupElement = Arrays.copyOf(other.groupElement, other.groupElement.length);
        this.compressed = other.compressed;
    }

    /** {@inheritDoc} */
    @Override
    public Group getGroup() {
        return GROUP;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] toBytes() {
        return groupElement;
    }

    /** {@inheritDoc} */
    @Override
    public GroupElement power(final FieldElement exponent) {
        if (!(exponent instanceof final Bls12381FieldElement exponentElement)) {
            throw new IllegalArgumentException("exponent must be a valid Bls12381FieldElement");
        }

        final byte[] output = new byte[GROUP.getUncompressedSize()];

        final int errorCode = g2PowZn(this, exponentElement, output);
        if (errorCode != SUCCESS) {
            throw new Bls12381Exception("g2PowZn", errorCode);
        }

        return new Bls12381Group2Element(output);
    }

    /** {@inheritDoc} */
    @Override
    public GroupElement multiply(final GroupElement other) {
        if (!(other instanceof final Bls12381Group2Element otherElement)) {
            throw new IllegalArgumentException("other must be a valid Bls12381Group2Element");
        }

        final byte[] output = new byte[GROUP.getUncompressedSize()];

        final int errorCode = g2Multiply(this, otherElement, output);
        if (errorCode != SUCCESS) {
            throw new Bls12381Exception("g2Multiply", errorCode);
        }

        return new Bls12381Group2Element(output);
    }

    @NonNull
    @Override
    public GroupElement add(@NonNull GroupElement groupElement) {
        return null;
    }

    /** {@inheritDoc} */
    @Override
    public GroupElement divide(final GroupElement other) {
        if (!(other instanceof final Bls12381Group2Element otherElement)) {
            throw new IllegalArgumentException("other must be a valid Bls12381Group2Element");
        }

        final byte[] output = new byte[GROUP.getUncompressedSize()];

        final int errorCode = g2Divide(this, otherElement, output);
        if (errorCode != SUCCESS) {
            throw new Bls12381Exception("g2Divide", errorCode);
        }

        return new Bls12381Group2Element(output);
    }

    /** {@inheritDoc} */
    @Override
    public GroupElement compress() {
        // Already compressed, no need to do anything
        if (compressed) {
            return this;
        }

        final byte[] newGroupElement = new byte[GROUP.getCompressedSize()];

        final int errorCode = g2Compress(this, newGroupElement);
        if (errorCode != SUCCESS) {
            throw new Bls12381Exception("g2Compress", errorCode);
        }

        groupElement = newGroupElement;
        compressed = true;

        return this;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isCompressed() {
        return compressed;
    }

    @Override
    public String toString() {
        return Arrays.toString(groupElement);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public Bls12381Group2Element copy() {
        return new Bls12381Group2Element(this);
    }
}
