/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.version;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;

/**
 * An implementation of {@link SoftwareVersion} which can be saved in state and holds information about the HAPI and
 * Services versions of the running software.
 *
 * <p>The HAPI version is the version of the Hedera API. It may be that the HAPI version is less than the services
 * version if we had multiple services releases without touching the HAPI. In theory, these two versions could be
 * completely different from each other.
 *
 * <p>The Services version is the version of the node software itself.
 */
public class HederaSoftwareVersion implements SoftwareVersion {
    public static final long CLASS_ID = 0x6f2b1bc2df8cbd0bL;
    public static final int RELEASE_027_VERSION = 1;

    private SemanticVersion hapiVersion;
    private SemanticVersion servicesVersion;

    public HederaSoftwareVersion() {
        // For ConstructableRegistry. Do not use.
    }

    public HederaSoftwareVersion(final SemanticVersion hapiVersion, final SemanticVersion servicesVersion) {
        this.hapiVersion = hapiVersion;
        this.servicesVersion = servicesVersion;
    }

    public SemanticVersion getHapiVersion() {
        return hapiVersion;
    }

    public SemanticVersion getServicesVersion() {
        return servicesVersion;
    }

    // The software version can be null here because we use deserializedVersion as null
    // on genesis initialization.
    public boolean isAfter(@Nullable final SoftwareVersion other) {
        return compareTo(other) > 0;
    }

    // The software version can be null here because we use deserializedVersion as null
    // on genesis initialization.
    public boolean isBefore(@Nullable final SoftwareVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return RELEASE_027_VERSION;
    }

    @Override
    public int getMinimumSupportedVersion() {
        return RELEASE_027_VERSION;
    }

    @Override
    public int compareTo(SoftwareVersion softwareVersion) {
        return Integer.compare(getVersion(), softwareVersion.getVersion());
    }

    @Override
    public void deserialize(SerializableDataInputStream in, int i) throws IOException {
        hapiVersion = deserializeSemVer(in);
        servicesVersion = deserializeSemVer(in);
    }

    private static SemanticVersion deserializeSemVer(final SerializableDataInputStream in) throws IOException {
        final var ans = SemanticVersion.newBuilder();
        ans.major(in.readInt()).minor(in.readInt()).patch(in.readInt());
        if (in.readBoolean()) {
            ans.pre(in.readNormalisedString(Integer.MAX_VALUE));
        }
        if (in.readBoolean()) {
            ans.build(in.readNormalisedString(Integer.MAX_VALUE));
        }
        return ans.build();
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        serializeSemVer(hapiVersion, out);
        serializeSemVer(servicesVersion, out);
    }

    private static void serializeSemVer(final SemanticVersion semVer, final SerializableDataOutputStream out)
            throws IOException {
        out.writeInt(semVer.major());
        out.writeInt(semVer.minor());
        out.writeInt(semVer.patch());
        serializeIfUsed(semVer.pre(), out);
        serializeIfUsed(semVer.build(), out);
    }

    private static void serializeIfUsed(final String semVerPart, final SerializableDataOutputStream out)
            throws IOException {
        if (semVerPart.isBlank()) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeNormalisedString(semVerPart);
        }
    }
}
