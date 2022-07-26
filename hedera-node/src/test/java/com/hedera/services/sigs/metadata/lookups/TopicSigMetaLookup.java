/*
 * Copyright (C) 2020-2021 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.metadata.lookups;

import com.hedera.services.sigs.metadata.SafeLookupResult;
import com.hedera.services.sigs.metadata.TopicSigningMetadata;
import com.hederahashgraph.api.proto.java.TopicID;

/**
 * Defines a simple type that is able to recover metadata about signing activity associated with a
 * given HCS topic.
 */
public interface TopicSigMetaLookup {
    SafeLookupResult<TopicSigningMetadata> safeLookup(TopicID id);
}
