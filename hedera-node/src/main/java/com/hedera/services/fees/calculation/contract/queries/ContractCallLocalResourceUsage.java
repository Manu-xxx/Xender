package com.hedera.services.fees.calculation.contract.queries;

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

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.execution.CallLocalExecutor;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.fee.SmartContractFeeBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;

import static com.hedera.services.queries.contract.ContractCallLocalAnswer.CONTRACT_CALL_LOCAL_CTX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public final class ContractCallLocalResourceUsage implements QueryResourceUsageEstimator {
	private static final Logger log = LogManager.getLogger(ContractCallLocalResourceUsage.class);

	private final SmartContractFeeBuilder usageEstimator;
	private final GlobalDynamicProperties properties;

	private final CallLocalExecutor executor;

	@Inject
	public ContractCallLocalResourceUsage(
			final SmartContractFeeBuilder usageEstimator,
			final GlobalDynamicProperties properties,
			final CallLocalExecutor executor
	) {
		this.executor = executor;

		this.properties = properties;
		this.usageEstimator = usageEstimator;
	}

	@Override
	public boolean applicableTo(final Query query) {
		return query.hasContractCallLocal();
	}

	@Override
	public FeeData usageGivenType(final Query query, final StateView view, final ResponseType type) {
		return usageFor(query, type, null);
	}

	@Override
	public FeeData usageGiven(final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx) {
		return usageFor(query, query.getContractCallLocal().getHeader().getResponseType(), queryCtx);
	}

	private FeeData usageFor(final Query query, final ResponseType type, @Nullable final Map<String, Object> queryCtx) {
		try {
			final var op = query.getContractCallLocal();
			ContractCallLocalResponse response;
			if (null == queryCtx) {
				response = dummyResponse(op.getContractID());
			} else {
				response = executor.execute(op);
				queryCtx.put(CONTRACT_CALL_LOCAL_CTX_KEY, response);
			}
			final var nonGasUsage = usageEstimator.getContractCallLocalFeeMatrices(
					op.getFunctionParameters().size(),
					response.getFunctionResult(),
					type);
			return nonGasUsage.toBuilder()
					.setNodedata(nonGasUsage.getNodedata().toBuilder().setGas(op.getGas()))
					.build();
		} catch (final Exception internal) {
			log.warn("Usage estimation unexpectedly failed for {}!", query, internal);
			throw new IllegalStateException(internal);
		}
	}

	ContractCallLocalResponse dummyResponse(final ContractID target) {
		return ContractCallLocalResponse.newBuilder()
				.setFunctionResult(ContractFunctionResult.newBuilder()
						.setContractCallResult(ByteString.copyFrom(new byte[properties.localCallEstRetBytes()]))
						.setContractID(target))
				.setHeader(ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(OK))
				.build();
	}
}
