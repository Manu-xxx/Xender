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

import com.swirlds.pairings.api.FieldElement;
import com.swirlds.pairings.api.Group;
import com.swirlds.pairings.api.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;

/**
 * An element in Group 1 of the BLS 12-381 curve family
 */
public class Bls12381Group1Element implements GroupElement {
    /**
     * The group this element is part of
     */
    private static final Bls12381Group1 GROUP = Bls12381Group1.getInstance();

    /**
     * The bytes representation of the element
     */
    private byte[] groupElement;

    /**
     * True if the {@link #groupElement} bytes are in a compressed form, otherwise false
     */
    private boolean compressed;

    /**
     * Package private constructor. This is used by {@link Bls12381Group1}, but shouldn't be called
     * directly by anyone else
     *
     * @param groupElement a byte array representing this group element
     */
    Bls12381Group1Element(final byte[] groupElement) {
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
    Bls12381Group1Element(final Bls12381Group1Element other) {
        if (other == null) {
            throw new IllegalArgumentException("other cannot be null");
        }

        this.groupElement = Arrays.copyOf(other.groupElement, other.groupElement.length);
        this.compressed = other.compressed;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] toBytes() {
        return groupElement;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Group getGroup() {
        return GROUP;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GroupElement power(final FieldElement exponent) {
        if (!(exponent instanceof final Bls12381FieldElement exponentElement)) {
            throw new IllegalArgumentException("exponent must be a valid Bls12381FieldElement");
        }

        final byte[] output = new byte[GROUP.getUncompressedSize()];

        final int errorCode = Bls12381Bindings.g1PowZn(this, exponentElement, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("g1PowZn", errorCode);
        }

        return new Bls12381Group1Element(output);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GroupElement multiply(final GroupElement other) {
        if (!(other instanceof final Bls12381Group1Element otherElement)) {
            throw new IllegalArgumentException("other must be a valid Bls12381Group1Element");
        }

        final byte[] output = new byte[GROUP.getUncompressedSize()];

        final int errorCode = Bls12381Bindings.g1Multiply(this, otherElement, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("g1Multiply", errorCode);
        }

        return new Bls12381Group1Element(output);
    }

    @NonNull
    @Override
    public GroupElement add(@NonNull GroupElement groupElement) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GroupElement divide(final GroupElement other) {
        if (!(other instanceof final Bls12381Group1Element otherElement)) {
            throw new IllegalArgumentException("other must be a valid Bls12381Group1Element");
        }

        final byte[] output = new byte[GROUP.getUncompressedSize()];

        final int errorCode = Bls12381Bindings.g1Divide(this, otherElement, output);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("g1Divide", errorCode);
        }

        return new Bls12381Group1Element(output);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GroupElement compress() {
        // Already compressed, no need to do anything
        if (compressed) {
            return this;
        }

        final byte[] newGroupElement = new byte[GROUP.getCompressedSize()];

        final int errorCode = Bls12381Bindings.g1Compress(this, newGroupElement);
        if (errorCode != Bls12381Bindings.SUCCESS) {
            throw new Bls12381Exception("g1Compress", errorCode);
        }

        groupElement = newGroupElement;
        compressed = true;

        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCompressed() {
        return compressed;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (!(o instanceof final Bls12381Group1Element element)) {
            return false;
        }

        return Bls12381Bindings.g1ElementEquals(this, element);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bls12381Group1Element copy() {
        return new Bls12381Group1Element(this);
    }
}
