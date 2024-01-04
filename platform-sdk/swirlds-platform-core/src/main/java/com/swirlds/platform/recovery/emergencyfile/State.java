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

package com.swirlds.platform.recovery.emergencyfile;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.jackson.HashDeserializer;
import com.swirlds.common.jackson.InstantDeserializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Data about the state written to disk, either during normal operation or at the end of event recovery.
 *
 * @param round
 * 		the round of the state. This value is required by the platform when reading a file.
 * @param hash
 * 		the hash of the state. This value is required by the platform when reading a file.
 * @param timestamp
 * 		the consensus timestamp of the state. This value is optional for the platform when reading a
 * 		file, but should always be populated with an accurate value when written by the platform.
 */
public record State(
        long round,
        @NonNull @JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = HashDeserializer.class)
                Hash hash,
        @NonNull @JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = InstantDeserializer.class)
                Instant timestamp) {}
