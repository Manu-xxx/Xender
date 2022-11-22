/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app;

import com.google.protobuf.Parser;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.annotation.Nonnull;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * This record keeps a list of everything that is used per-thread
 *
 * @param queryParser a parser for {@link Query}
 * @param txParser a parser for {@link Transaction}
 * @param signedParser a parser for {@link SignedTransaction}
 * @param txBodyParser a parser for {@link TransactionBody}
 */
public record SessionContext(
        @Nonnull Parser<Query> queryParser,
        @Nonnull Parser<Transaction> txParser,
        @Nonnull Parser<SignedTransaction> signedParser,
        @Nonnull Parser<TransactionBody> txBodyParser) {

    public SessionContext(
            @Nonnull final Parser<Query> queryParser,
            @Nonnull final Parser<Transaction> txParser,
            @Nonnull final Parser<SignedTransaction> signedParser,
            @Nonnull final Parser<TransactionBody> txBodyParser) {
        this.queryParser = requireNonNull(queryParser);
        this.txParser = requireNonNull(txParser);
        this.signedParser = requireNonNull(signedParser);
        this.txBodyParser = requireNonNull(txBodyParser);
    }
}
