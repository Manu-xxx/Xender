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

package com.hedera.node.app.roster;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.roster.schemas.V0540RosterSchema;
import com.swirlds.state.spi.SchemaAware;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A {@link com.hedera.hapi.node.state.roster.Roster} implementation of the {@link SchemaAware} interface.
 * Registers the roster schemas with the {@link SchemaRegistry}.
 */
public class RosterSchemaAwareImpl implements SchemaAware {

    public static final String SCHEMA_NAME = "ROSTER_SCHEMA";

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        requireNonNull(registry);
        registry.register(new V0540RosterSchema());
    }

    @NonNull
    @Override
    public String getStateName() {
        return SCHEMA_NAME;
    }
}
