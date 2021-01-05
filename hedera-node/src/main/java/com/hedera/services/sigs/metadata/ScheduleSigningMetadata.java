package com.hedera.services.sigs.metadata;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSchedule;

import java.util.Optional;

public class ScheduleSigningMetadata {
    private final byte[] transactionBody;
    private final Optional<JKey> adminKey;

    private ScheduleSigningMetadata(
            byte[] transactionBody,
            Optional<JKey> adminKey
    ) {
        this.transactionBody = transactionBody;
        this.adminKey = adminKey;
    }

    public static ScheduleSigningMetadata from(MerkleSchedule schedule) {
        return new ScheduleSigningMetadata(
                schedule.transactionBody(),
                schedule.adminKey());
    }

    public byte[] transactionBody() { return transactionBody; }

    public Optional<JKey> adminKey() {
        return adminKey;
    }
}
