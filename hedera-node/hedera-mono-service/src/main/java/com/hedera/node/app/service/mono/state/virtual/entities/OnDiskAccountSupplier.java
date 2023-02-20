/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual.entities;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;
import java.io.IOException;

public class OnDiskAccountSupplier implements SelfSerializableSupplier<OnDiskAccount> {
    static final long CLASS_ID = 0xe5d01987257f5efcL;
    static final int CURRENT_VERSION = 1;

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // Nothing to do here
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // Nothing to do here
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public OnDiskAccount get() {
        return new OnDiskAccount();
    }
}
