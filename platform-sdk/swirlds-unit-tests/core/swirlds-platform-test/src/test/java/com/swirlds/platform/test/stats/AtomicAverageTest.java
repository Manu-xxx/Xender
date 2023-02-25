/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.platform.stats.AtomicAverage;
import org.junit.jupiter.api.Test;

class AtomicAverageTest {
    @Test
    void basic() {
        final double def = 0;
        final double weight = 0.5;
        AtomicAverage a = new AtomicAverage(weight, def);
        assertEquals(def, a.get(), "The average value should be equal to the initialized value.");

        a.update(1);
        a.update(2);

        assertEquals(1.5, a.get(), "The evenly weighted average of 1 and 2 should be 1.5.");
    }

    @Test
    void weight() {
        final double weight = 0.2;
        AtomicAverage a = new AtomicAverage(weight);

        a.update(1);
        a.update(2);

        assertEquals(
                1.2,
                round(a.get(), 5),
                "The average of 1 and 2 with a" + " weight of 0.2 for new values should be 1.2");
    }

    public static double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
