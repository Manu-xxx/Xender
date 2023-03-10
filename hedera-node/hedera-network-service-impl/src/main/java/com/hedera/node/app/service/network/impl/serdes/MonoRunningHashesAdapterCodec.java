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

package com.hedera.node.app.service.network.impl.serdes;

import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.pbj.runtime.Codec;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import com.hedera.pbj.runtime.io.DataInput;
import com.hedera.pbj.runtime.io.DataOutput;

import java.io.IOException;

public class MonoRunningHashesAdapterCodec implements Codec<RecordsRunningHashLeaf> {
    @NonNull
    @Override
    public RecordsRunningHashLeaf parse(final @NonNull DataInput input) throws IOException {
        if (input instanceof SerializableDataInputStream in) {
            final var context = new RecordsRunningHashLeaf();
            context.deserialize(in, RecordsRunningHashLeaf.RELEASE_0280_VERSION);
            return context;
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataInputStream");
        }
    }

    @Override
    public void write(final @NonNull RecordsRunningHashLeaf item, final @NonNull DataOutput output) throws IOException {
        if (output instanceof SerializableDataOutputStream out) {
            item.serialize(out);
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataOutputStream");
        }
    }

    @Override
    public int measure(@NonNull DataInput input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int measureRecord(final @NonNull RecordsRunningHashLeaf recordsRunningHashLeaf) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fastEquals(@NonNull RecordsRunningHashLeaf item, @NonNull DataInput input) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public RecordsRunningHashLeaf parseStrict(@NonNull DataInput dataInput) throws IOException {
        return parse(dataInput);
    }
}
