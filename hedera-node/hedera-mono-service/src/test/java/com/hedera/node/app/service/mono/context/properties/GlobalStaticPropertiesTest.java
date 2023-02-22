/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.context.properties;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GlobalStaticPropertiesTest {
    private GlobalStaticProperties subject;

    @Test
    void loadsWorkflowsEnabled() {
        subject = new GlobalStaticProperties(new BootstrapProperties());

        assertFalse(subject.workflowsEnabled().isEmpty());
        assertEquals(1, subject.workflowsEnabled().size());
        assertEquals(ConsensusGetTopicInfo, subject.workflowsEnabled().toArray()[0]);
    }

    @Test
    void doesntLoadsWhenPropertiesNull() {
        subject = new GlobalStaticProperties(null);

        assertTrue(subject.workflowsEnabled().isEmpty());
    }
}
