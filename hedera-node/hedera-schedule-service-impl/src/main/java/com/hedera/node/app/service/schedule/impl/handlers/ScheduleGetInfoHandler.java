/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.schedule.impl.handlers;

import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryHandler;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ScheduleGetInfoResponse;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ScheduleGetInfo}.
 */
public class ScheduleGetInfoHandler implements QueryHandler {

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        final var content = query.getScheduleGetInfo();
        if (content == null) {
            throw new IllegalArgumentException("Query does not match expected type: " + query);
        }
        return content.getHeader();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        final var response = ScheduleGetInfoResponse.newBuilder().setHeader(header);
        return Response.newBuilder().setScheduleGetInfo(response).build();
    }

    /**
     * This method is called during the query workflow. It validates the query, but does not
     * determine the response yet.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param query the {@link Query} that should be validated
     * @throws NullPointerException if one of the arguments is {@code null}
     * @throws PreCheckException if validation fails
     */
    public void validate(@NonNull final Query query) throws PreCheckException {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * This method is called during the query workflow. It determines the requested value(s) and
     * returns the appropriate response.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param query the {@link Query} with the request
     * @param header the {@link ResponseHeader} that should be used, if the request was successful
     * @return a {@link Response} with the requested values
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public Response findResponse(@NonNull final Query query, @NonNull final ResponseHeader header) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
