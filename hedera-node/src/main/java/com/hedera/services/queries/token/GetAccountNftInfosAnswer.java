package com.hedera.services.queries.token;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.queries.AbstractAnswer;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosQuery;
import com.hederahashgraph.api.proto.java.TokenGetAccountNftInfosResponse;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Optional;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetAccountNftInfos;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

@Singleton
public class GetAccountNftInfosAnswer extends AbstractAnswer {
    @Inject
    public GetAccountNftInfosAnswer() {
        super(TokenGetAccountNftInfos,
                query -> null,
                query -> query.getTokenGetAccountNftInfos().getHeader().getResponseType(),
                response -> response.getTokenGetAccountNftInfos().getHeader().getNodeTransactionPrecheckCode(),
                (query, view) -> NOT_SUPPORTED);
    }

    @Override
    public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
        TokenGetAccountNftInfosQuery op = query.getTokenGetAccountNftInfos();
        TokenGetAccountNftInfosResponse.Builder response = TokenGetAccountNftInfosResponse.newBuilder();

        ResponseType type = op.getHeader().getResponseType();
        if (type == COST_ANSWER) {
            response.setHeader(costAnswerHeader(NOT_SUPPORTED, 0L));
        } else {
            response.setHeader(answerOnlyHeader(NOT_SUPPORTED));
        }
        return Response.newBuilder()
                .setTokenGetAccountNftInfos(response)
                .build();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        return Optional.empty();
    }

    @Override
    public boolean needsAnswerOnlyCost(Query query) {
        return false;
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return false;
    }
}
