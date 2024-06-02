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

package com.hedera.services.bdd.spec.dsl.operations;

import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.dsl.SpecEntity;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Optional;

/**
 * Provides implementation support for a {@link SpecOperation} that depends on
 * one or more {@link SpecEntity}s to be present in the {@link HapiSpec} before
 * it can be executed.
 */
public abstract class AbstractSpecOperation implements SpecOperation {
    protected final List<SpecEntity> entities;

    protected AbstractSpecOperation(@NonNull final List<SpecEntity> entities) {
        this.entities = requireNonNull(entities);
    }

    protected abstract @NonNull SpecOperation computeDelegate(@NonNull final HapiSpec spec);

    /**
     * {@inheritDoc}
     *
     * <p>Executes the operation for the given {@link HapiSpec} by first ensuring all entities
     * are registered with the spec, then computing its delegate operation and submitting it.
     *
     * @param spec the {@link HapiSpec} to execute the operation for
     * @return an optional containing any failure that was thrown
     */
    @Override
    public Optional<Throwable> execFor(@NonNull final HapiSpec spec) {
        requireNonNull(spec);
        entities.forEach(entity -> entity.registerOrCreateWith(spec));
        final var delegate = computeDelegate(spec);
        return delegate.execFor(spec);
    }
}
