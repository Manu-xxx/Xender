/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.grpc.marshalling;

import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.store.models.Id;
import java.util.Collections;
import java.util.List;

public record CustomFeeMeta(Id tokenId, Id treasuryId, List<FcCustomFee> customFees) {
    public static CustomFeeMeta forMissingLookupOf(final Id tokenId) {
        return new CustomFeeMeta(tokenId, Id.MISSING_ID, Collections.emptyList());
    }
}
