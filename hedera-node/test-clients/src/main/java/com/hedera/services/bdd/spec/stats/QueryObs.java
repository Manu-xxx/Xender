/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.stats;

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseType;

public class QueryObs extends OpObs {
    private final ResponseType responseType;
    private final HederaFunctionality queryType;

    public QueryObs(ResponseType responseType, HederaFunctionality queryType) {
        this.responseType = responseType;
        this.queryType = queryType;
    }

    @Override
    public HederaFunctionality functionality() {
        return queryType;
    }

    public ResponseType type() {
        return responseType;
    }

    @Override
    public String toString() {
        return super.toString() + ":" + responseType;
    }
}
