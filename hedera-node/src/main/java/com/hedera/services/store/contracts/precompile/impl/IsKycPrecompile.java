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
package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.contracts.ParsingConstants.ADDRESS_PAIR_RAW_TYPE;
import static com.hedera.services.contracts.ParsingConstants.INT_BOOL_PAIR;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.convertLeftPaddedAddressToAccountId;
import static com.hedera.services.store.contracts.precompile.codec.DecodingFacade.decodeFunctionCall;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.GrantRevokeKycWrapper;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.UnaryOperator;
import org.apache.tuweni.bytes.Bytes;

public class IsKycPrecompile extends AbstractReadOnlyPrecompile {
    private static final Function IS_KYC_TOKEN_FUNCTION =
            new Function("isKyc(address,address)", INT_BOOL_PAIR);
    private static final Bytes IS_KYC_TOKEN_FUNCTION_SELECTOR =
            Bytes.wrap(IS_KYC_TOKEN_FUNCTION.selector());
    private static final ABIType<Tuple> IS_KYC_TOKEN_FUNCTION_DECODER =
            TypeFactory.create(ADDRESS_PAIR_RAW_TYPE);
    private AccountID accountId;

    public IsKycPrecompile(
            TokenID tokenId,
            SyntheticTxnFactory syntheticTxnFactory,
            WorldLedgers ledgers,
            EncodingFacade encoder,
            PrecompilePricingUtils pricingUtils) {
        super(tokenId, syntheticTxnFactory, ledgers, encoder, pricingUtils);
    }

    @Override
    public TransactionBody.Builder body(Bytes input, UnaryOperator<byte[]> aliasResolver) {
        final var tokenIsKycWrapper = decodeIsKyc(input, aliasResolver);
        tokenId = tokenIsKycWrapper.token();
        accountId = tokenIsKycWrapper.account();
        return super.body(input, aliasResolver);
    }

    @Override
    public Bytes getSuccessResultFor(ExpirableTxnRecord.Builder childRecord) {
        final boolean isKyc = ledgers.isKyc(accountId, tokenId);
        return encoder.encodeIsKyc(isKyc);
    }

    public static GrantRevokeKycWrapper decodeIsKyc(
            final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final Tuple decodedArguments =
                decodeFunctionCall(
                        input, IS_KYC_TOKEN_FUNCTION_SELECTOR, IS_KYC_TOKEN_FUNCTION_DECODER);

        final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
        final var accountID =
                convertLeftPaddedAddressToAccountId(decodedArguments.get(1), aliasResolver);

        return new GrantRevokeKycWrapper(tokenID, accountID);
    }
}
