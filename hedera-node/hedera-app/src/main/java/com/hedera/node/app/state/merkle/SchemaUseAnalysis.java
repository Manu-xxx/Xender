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

package com.hedera.node.app.state.merkle;

import static com.hedera.node.app.state.merkle.SchemaUseType.MIGRATION;
import static com.hedera.node.app.state.merkle.SchemaUseType.RESTART;
import static com.hedera.node.app.state.merkle.SchemaUseType.STATE_DEFINITIONS;
import static com.hedera.node.app.state.merkle.VersionUtils.isSameVersion;
import static com.hedera.node.app.state.merkle.VersionUtils.isSoOrdered;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.spi.state.Schema;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.EnumSet;
import java.util.Set;

/**
 * Analyzes the ways in which a {@link Schema} should be used by the {@link MerkleSchemaRegistry}.
 *
 * @see SchemaUseType
 */
public class SchemaUseAnalysis {
    /**
     * Computes the set of {@link SchemaUseType}s that should be used for the given {@link Schema}.
     *
     * @param deserializedVersion the version of the deserialized state
     * @param latestVersion the latest schema version of the relevant service
     * @param schema the schema to analyze
     * @return the ways the schema should be used
     */
    public Set<SchemaUseType> computeUses(
            @Nullable final SemanticVersion deserializedVersion,
            @NonNull final SemanticVersion latestVersion,
            @NonNull final Schema schema) {
        requireNonNull(schema);
        requireNonNull(latestVersion);
        final var uses = EnumSet.noneOf(SchemaUseType.class);
        // Always add state definitions, even if later schemas will remove them all
        if (hasStateDefinitions(schema)) {
            uses.add(STATE_DEFINITIONS);
        }
        // We only skip migration if the deserialized version is at least as new as the schema
        // version (which implies the deserialized state already went through this migration)
        if (deserializedVersion == null || isSoOrdered(deserializedVersion, schema.getVersion())) {
            uses.add(MIGRATION);
        }
        // We only do restart if the schema is the latest one available
        if (isSameVersion(latestVersion, schema.getVersion())) {
            uses.add(RESTART);
        }
        return uses;
    }

    private boolean hasStateDefinitions(@NonNull final Schema schema) {
        return !schema.statesToCreate().isEmpty() || !schema.statesToRemove().isEmpty();
    }
}
