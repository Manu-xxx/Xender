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

import static com.hedera.node.app.service.mono.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hedera.node.app.service.mono.ledger.properties.AccountProperty.FUNGIBLE_TOKEN_ALLOWANCES;
import static com.hedera.services.contracts.ParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.contracts.ParsingConstants.ADDRESS_TRIO_RAW_TYPE;
import static com.hedera.services.contracts.ParsingConstants.INT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.FcTokenAllowanceId;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.TokenAllowanceWrapper;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class AllowancePrecompile extends AbstractReadOnlyPrecompile {
    private static final Function ERC_ALLOWANCE_FUNCTION =
            new Function("allowance(address,address)", INT);
    private static final Bytes ERC_ALLOWANCE_SELECTOR =
            Bytes.wrap(ERC_ALLOWANCE_FUNCTION.selector());
    private static final ABIType<Tuple> ERC_ALLOWANCE_DECODER =
            TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);
    private static final Function HAPI_ALLOWANCE_FUNCTION =
            new Function("allowance(address,address,address)", "(int,int)");
    private static final Bytes HAPI_ALLOWANCE_SELECTOR =
            Bytes.wrap(HAPI_ALLOWANCE_FUNCTION.selector());
    private static final ABIType<Tuple> HAPI_ALLOWANCE_DECODER =
            TypeFactory.create(ADDRESS_TRIO_RAW_TYPE);
    private TokenAllowanceWrapper allowanceWrapper;

    public AllowancePrecompile(
            final TokenID tokenId,
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, pricingUtils);
    }

    public AllowancePrecompile(
            final SyntheticTxnFactory syntheticTxnFactory,
            final WorldLedgers ledgers,
            final EncodingFacade encoder,
            final PrecompilePricingUtils pricingUtils) {
        this(null, syntheticTxnFactory, ledgers, encoder, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var nestedInput = tokenId == null ? input : input.slice(24);
        allowanceWrapper = decodeTokenAllowance(nestedInput, tokenId, aliasResolver);

        return super.body(input, aliasResolver);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Bytes getSuccessResultFor(final ExpirableTxnRecord.Builder childRecord) {
        Objects.requireNonNull(
                allowanceWrapper, "`body` method should be called before `getSuccessResultsFor`");

        final TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger =
                ledgers.accounts();
        validateTrueOrRevert(
                accountsLedger.contains(allowanceWrapper.owner()), INVALID_ALLOWANCE_OWNER_ID);
        final var allowances =
                (Map<FcTokenAllowanceId, Long>)
                        accountsLedger.get(allowanceWrapper.owner(), FUNGIBLE_TOKEN_ALLOWANCES);
        final var fcTokenAllowanceId =
                FcTokenAllowanceId.from(allowanceWrapper.tokenID(), allowanceWrapper.spender());
        final var value = allowances.getOrDefault(fcTokenAllowanceId, 0L);
        return tokenId == null
                ? encoder.encodeAllowance(SUCCESS.getNumber(), value)
                : encoder.encodeAllowance(value);
    }

    public static TokenAllowanceWrapper decodeTokenAllowance(
            final Bytes input,
            final TokenID impliedTokenId,
            final UnaryOperator<byte[]> aliasResolver) {
        final var offset = impliedTokenId == null ? 1 : 0;

        final Tuple decodedArguments =
                DecodingFacade.decodeFunctionCall(
                        input,
                        offset == 0 ? ERC_ALLOWANCE_SELECTOR : HAPI_ALLOWANCE_SELECTOR,
                        offset == 0 ? ERC_ALLOWANCE_DECODER : HAPI_ALLOWANCE_DECODER);

        final var tokenId =
                offset == 0
                        ? impliedTokenId
                        : DecodingFacade.convertAddressBytesToTokenID(decodedArguments.get(0));
        final var owner =
                DecodingFacade.convertLeftPaddedAddressToAccountId(
                        decodedArguments.get(offset), aliasResolver);
        final var spender =
                DecodingFacade.convertLeftPaddedAddressToAccountId(
                        decodedArguments.get(offset + 1), aliasResolver);

        return new TokenAllowanceWrapper(tokenId, owner, spender);
    }
}
