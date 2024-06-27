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

package com.hedera.node.app.services;

import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_KEYVALUE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_QUEUE;
import static com.swirlds.common.merkle.proto.MerkleNodeProtoFields.FIELD_STATENODE_SINGLETON;
import static java.util.Objects.requireNonNull;

import com.swirlds.state.spi.Schema;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.Service;
import com.swirlds.state.spi.StateDefinition;
import com.swirlds.state.spi.workflows.record.GenesisRecordsBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Set;
import javax.inject.Singleton;

/**
 * A registry providing access to all services registered with the application.
 */
@Singleton
public interface ServicesRegistry {
    /**
     * A record of a service registration.
     *
     * @param service The service that was registered
     * @param registry The schema registry for the service
     */
    record Registration(@NonNull Service service, @NonNull SchemaRegistry registry)
            implements Comparable<Registration> {
        private static final Comparator<Registration> COMPARATOR = Comparator.<Registration>comparingInt(
                        r -> r.service().migrationOrder())
                .thenComparing(r -> r.service().getServiceName());

        public Registration {
            requireNonNull(service);
            requireNonNull(registry);
        }

        @Override
        public int compareTo(@NonNull final Registration that) {
            return COMPARATOR.compare(this, that);
        }
    }

    /**
     * Gets the full set of services registered, sorted deterministically.
     *
     * @return The set of services. May be empty.
     */
    @NonNull
    Set<Registration> registrations();

    /**
     * Gets the genesis records builder.
     * @return The genesis records builder
     */
    @NonNull
    GenesisRecordsBuilder getGenesisRecords();

    /**
     * Register a service with the registry.
     * @param service The service to register
     */
    void register(@NonNull final Service service);

    @SuppressWarnings("rawtypes")
    default StateDefinition getStateDefinition(final String stateLabel) {
        final String[] labelComponents = stateLabel.split("\\.");
        assert labelComponents.length == 2;
        final String serviceName = labelComponents[0];
        final String stateKey = labelComponents[1];
        for (final Registration r : registrations()) {
            if (!serviceName.equals(r.service().getServiceName())) {
                continue;
            }
            final Iterator<Schema> schemas = r.registry().registeredSchemas();
            while (schemas.hasNext()) {
                final Schema schema = schemas.next();
                // FUTURE WORK: I am not sure about the line below
                final Set<StateDefinition> stateDefs = schema.statesToCreate();
                for (final StateDefinition def : stateDefs) {
                    if (stateKey.equals(def.stateKey())) {
                        return def;
                    }
                }
            }
        }
        return null;
    }
}
