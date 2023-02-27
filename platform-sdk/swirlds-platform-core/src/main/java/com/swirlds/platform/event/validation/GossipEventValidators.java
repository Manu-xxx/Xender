/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.platform.event.GossipEvent;
import java.util.List;

/**
 * A {@link GossipEventValidator} which combines multiple validators to provide a single output
 */
public class GossipEventValidators implements GossipEventValidator {
    private final List<GossipEventValidator> validators;

    public GossipEventValidators(final List<GossipEventValidator> validators) {
        this.validators = validators;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEventValid(final GossipEvent event) {
        for (final GossipEventValidator validator : validators) {
            if (!validator.isEventValid(event)) {
                // if a single validation fails, the event is invalid
                return false;
            }
        }
        // if all checks pass, the event is valid
        return true;
    }
}
