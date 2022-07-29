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
package com.hedera.services.queries.meta;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.SemanticVersions;
import com.hedera.services.queries.AbstractAnswer;
import com.hederahashgraph.api.proto.java.NetworkGetVersionInfoResponse;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetVersionInfoAnswer extends AbstractAnswer {
    private final SemanticVersions semanticVersions;

    @Inject
    public GetVersionInfoAnswer(final SemanticVersions semanticVersions) {
        super(
                GetVersionInfo,
                query -> query.getNetworkGetVersionInfo().getHeader().getPayment(),
                query -> query.getNetworkGetVersionInfo().getHeader().getResponseType(),
                response ->
                        response.getNetworkGetVersionInfo()
                                .getHeader()
                                .getNodeTransactionPrecheckCode(),
                (query, view) -> OK);
        this.semanticVersions = semanticVersions;
    }

    @Override
    public Response responseGiven(
            final Query query,
            final StateView view,
            final ResponseCodeEnum validity,
            final long cost) {
        final var op = query.getNetworkGetVersionInfo();
        final var response = NetworkGetVersionInfoResponse.newBuilder();

        final var type = op.getHeader().getResponseType();
        if (validity != OK) {
            response.setHeader(header(validity, type, cost));
        } else {
            if (type == COST_ANSWER) {
                response.setHeader(costAnswerHeader(OK, cost));
            } else {
                response.setHeader(answerOnlyHeader(OK));
                final var answer = semanticVersions.getDeployed();
                response.setHapiProtoVersion(answer.protoSemVer());
                response.setHederaServicesVersion(answer.hederaSemVer());
            }
        }

        return Response.newBuilder().setNetworkGetVersionInfo(response).build();
    }
}
