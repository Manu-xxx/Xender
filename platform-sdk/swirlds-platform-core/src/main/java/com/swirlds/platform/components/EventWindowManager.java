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

package com.swirlds.platform.components;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.consensus.EventWindow;
import com.swirlds.platform.internal.ConsensusRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The gateway for disseminating {@link EventWindow} instances to the rest of the platform.
 */
public interface EventWindowManager {

    /**
     * Extracts the {@link EventWindow} from the given {@link ConsensusRound}.
     *
     * @param round the {@link ConsensusRound}
     * @return the {@link EventWindow}
     */
    @InputWireLabel("consensus round")
    EventWindow extractEventWindow(@NonNull final ConsensusRound round);

    /**
     * Set the {@link EventWindow}.
     *
     * @param eventWindow the {@link EventWindow}
     * @return the {@link EventWindow} that was set.
     */
    @InputWireLabel("override event window")
    EventWindow updateEventWindow(@NonNull final EventWindow eventWindow);
}
