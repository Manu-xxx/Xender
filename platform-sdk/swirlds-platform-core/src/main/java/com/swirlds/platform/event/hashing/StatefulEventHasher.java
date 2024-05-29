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

package com.swirlds.platform.event.hashing;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * An implementation of {@link EventHasher} that is stateful and thus not safe to use by multiple threads concurrently.
 */
public class StatefulEventHasher implements EventHasher {
    private final HashingOutputStream hashingOutputStream = new HashingOutputStream(DigestType.SHA_384.buildDigest());
    private final SerializableDataOutputStream outputStream = new SerializableDataOutputStream(hashingOutputStream);

    @NonNull
    @Override
    public GossipEvent hashEvent(@NonNull final GossipEvent event) {
        try {
            event.serializeLegacyHashBytes(outputStream);
            event.setHash(new Hash(hashingOutputStream.getDigest(), DigestType.SHA_384));
            return event;
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }
}
