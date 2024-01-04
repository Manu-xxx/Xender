/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.lifecycle.selectors;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.HapiTestNode;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Selects the node that has the given name, like Alice or Bob.
 */
public class SelectByName implements NodeSelector {
    private final String name;

    public SelectByName(@NonNull final String name) {
        this.name = requireNonNull(name);
    }

    @Override
    public boolean test(@NonNull final HapiTestNode hapiTestNode) {
        return name.equalsIgnoreCase(hapiTestNode.getName());
    }

    @Override
    public String toString() {
        return "by name '" + name + "'";
    }
}
