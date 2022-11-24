/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.contracts.ParsingConstants.BYTES32;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.GetTokenExpiryInfoWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenExpiryWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class GetTokenExpiryInfoPrecompile extends AbstractReadOnlyPrecompile {
    private static final Function GET_TOKEN_EXPIRY_INFO_FUNCTION =
            new Function("getTokenExpiryInfo(address)");
    private static final Bytes GET_TOKEN_EXPIRY_INFO_SELECTOR =
            Bytes.wrap(GET_TOKEN_EXPIRY_INFO_FUNCTION.selector());
    private static final ABIType<Tuple> GET_TOKEN_EXPIRY_INFO_DECODER = TypeFactory.create(BYTES32);
    private final StateView stateView;

    public GetTokenExpiryInfoPrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils,
            final StateView stateView) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, pricingUtils);
        this.stateView = stateView;
    }

    @Override
    public Builder body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var getTokenExpiryInfoWrapper = decodeGetTokenExpiryInfo(input);
        tokenId = getTokenExpiryInfoWrapper.tokenID();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        validateTrue(stateView.tokenExists(tokenId), ResponseCodeEnum.INVALID_TOKEN_ID);
        final var tokenInfo = stateView.infoForToken(tokenId).orElse(null);

        if (tokenInfo == null) {
            throw new InvalidTransactionException(ResponseCodeEnum.INVALID_TOKEN_ID);
        }

        final var expiryInfo =
                new TokenExpiryWrapper(
                        tokenInfo.getExpiry().getSeconds(),
                        tokenInfo.getAutoRenewAccount(),
                        tokenInfo.getAutoRenewPeriod().getSeconds());

        return encoder.encodeGetTokenExpiryInfo(expiryInfo);
    }

    public static GetTokenExpiryInfoWrapper decodeGetTokenExpiryInfo(final Bytes input) {
        final Tuple decodedArguments =
                DecodingFacade.decodeFunctionCall(
                        input, GET_TOKEN_EXPIRY_INFO_SELECTOR, GET_TOKEN_EXPIRY_INFO_DECODER);

        final var tokenID = DecodingFacade.convertAddressBytesToTokenID(decodedArguments.get(0));
        return new GetTokenExpiryInfoWrapper(tokenID);
    }
}
