/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.hcm.api.pairings;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;

/**
 * An interface representing a generic field element
 */
public interface FieldElement extends ByteRepresentable<FieldElement>, UnderCurve {
    /**
     * Returns the field the element is in
     *
     * @return the field
     */
    Field getField();

    /**
     * Adds another field element to this one
     *
     * @param other the other field element
     * @return a new field element which is the sum of this element and another
     */
    @NonNull
    FieldElement add(@NonNull FieldElement other);

    /**
     * Subtracts another field element from this one
     *
     * @param other the other field element
     * @return a new field element which is the difference of this element and another
     */
    @NonNull
    FieldElement subtract(@NonNull FieldElement other);

    /**
     * Multiplies another field element with this one
     *
     * @param other the other field element
     * @return a new field element which is the product of this element and another
     */
    @NonNull
    FieldElement multiply(@NonNull FieldElement other);

    /**
     * Divides the field element by another
     *
     * @param other the other field element
     * @return a new field element which is the quotient of this element and another
     */
    @NonNull
    FieldElement divide(@NonNull FieldElement other);

    /**
     * Takes the field element to the power of an integer
     *
     * @param exponent the exponent integer
     * @return a new field element which is the power
     */
    @NonNull
    FieldElement power(@NonNull BigInteger exponent);
}
