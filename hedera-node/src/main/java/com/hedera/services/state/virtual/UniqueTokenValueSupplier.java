package com.hedera.services.state.virtual;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

public class UniqueTokenValueSupplier implements SelfSerializableSupplier<UniqueTokenValue> {
    static final long CLASS_ID = 0xc4d512c6695451d4L;
    static final int CURRENT_VERSION = 1;

    @Override
    public void deserialize(SerializableDataInputStream in, int version) {
        /* No operations since no state needs to be restored. */
    }

    @Override
    public void serialize(SerializableDataOutputStream out) {
        /* No operations since no state needs to be saved. */
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
    public UniqueTokenValue get() {
        return new UniqueTokenValue();
    }
}
