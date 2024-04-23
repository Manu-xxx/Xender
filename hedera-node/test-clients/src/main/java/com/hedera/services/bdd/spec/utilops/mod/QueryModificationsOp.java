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

package com.hedera.services.bdd.spec.utilops.mod;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.Query;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class QueryModificationsOp extends UtilOp {
    private final Supplier<HapiQueryOp<?>> queryOpSupplier;
    private final Function<Query, List<QueryModification>> modificationsFn;

    public QueryModificationsOp(
            @NonNull final Supplier<HapiQueryOp<?>> queryOpSupplier,
            @NonNull final Function<Query, List<QueryModification>> modificationsFn) {
        this.queryOpSupplier = queryOpSupplier;
        this.modificationsFn = modificationsFn;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        final var unmodifiedOp = queryOpSupplier.get();
        allRunFor(
                spec,
                unmodifiedOp,
                sourcing(() -> blockingOrder(modificationsFn.apply(unmodifiedOp.getQuery()).stream()
                        .flatMap(modification -> {
                            final var op = queryOpSupplier.get();
                            modification.customize(op);
                            return Stream.of(logIt(modification.summary()), op);
                        })
                        .toArray(HapiSpecOperation[]::new))));
        return false;
    }
}
