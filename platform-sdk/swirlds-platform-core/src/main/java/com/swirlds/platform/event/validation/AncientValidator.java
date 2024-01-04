/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.validation;

import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import java.util.function.Supplier;

/**
 * Determines that ancient events are invalid
 */
public class AncientValidator implements GossipEventValidator {
    private final Supplier<GraphGenerations> generations;

    public AncientValidator(final Supplier<GraphGenerations> generations) {
        this.generations = generations;
    }

    @Override
    public boolean isEventValid(final GossipEvent event) {
        return !generations.get().isAncient(event);
    }
}
