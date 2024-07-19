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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.updatetokencustomfees;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FixedFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UpdateTokenCustomFeesDecoder {

    private static final int TOKEN_ADDRESS = 0;
    private static final int FIXED_FEE = 1;
    private static final int FRACTIONAL_FEE = 2;
    private static final int ROYALTY_FEE = 2;

    private static final int FIXED_FEE_AMOUNT = 0;
    private static final int FIXED_FEE_TOKEN_ID = 1;
    private static final int FIXED_FEE_USE_HBARS_FOR_PAYMENT = 2;
    private static final int FIXED_FEE_USE_CURRENT_TOKEN_FOR_PAYMENT = 3;
    private static final int FIXED_FEE_FEE_COLLECTOR = 4;

    private static final int FRACTIONAL_FEE_NUMERATOR = 0;
    private static final int FRACTIONAL_FEE_DENOMINATOR = 1;
    private static final int FRACTIONAL_FEE_MIN_AMOUNT = 2;
    private static final int FRACTIONAL_FEE_MAX_AMOUNT = 3;
    private static final int FRACTIONAL_FEE_NET_OF_TRANSFERS = 4;
    private static final int FRACTIONAL_FEE_FEE_COLLECTOR = 5;

    private static final int ROYALTY_FEE_NUMERATOR = 0;
    private static final int ROYALTY_FEE_DENOMINATOR = 1;
    private static final int ROYALTY_FEE_AMOUNT = 2;
    private static final int ROYALTY_FEE_TOKEN_ID = 3;
    private static final int ROYALTY_FEE_USE_HBARS_FOR_PAYMENT = 4;
    private static final int ROYALTY_FEE_FEE_COLLECTOR = 5;

    @Inject
    public UpdateTokenCustomFeesDecoder() {}

    public @Nullable TransactionBody decodeUpdateFungibleTokenCustomFees(final HtsCallAttempt attempt) {
        final var call = UpdateTokenCustomFeesTranslator.UPDATE_FUNGIBLE_TOKEN_CUSTOM_FEES_FUNCTION.decodeCall(
                attempt.inputBytes());
        return TransactionBody.newBuilder()
                .tokenFeeScheduleUpdate(updateTokenCustomFees(call, attempt.addressIdConverter(), true))
                .build();
    }

    public TransactionBody decodeUpdateNonFungibleTokenCustomFees(final HtsCallAttempt attempt) {
        final var call = UpdateTokenCustomFeesTranslator.UPDATE_NON_FUNGIBLE_TOKEN_CUSTOM_FEES_FUNCTION.decodeCall(
                attempt.inputBytes());
        return TransactionBody.newBuilder()
                .tokenFeeScheduleUpdate(updateTokenCustomFees(call, attempt.addressIdConverter(), false))
                .build();
    }

    private TokenFeeScheduleUpdateTransactionBody updateTokenCustomFees(
            @NonNull final Tuple call, @NonNull final AddressIdConverter addressIdConverter, final boolean isFungible) {
        final Address tokenAddress = call.get(TOKEN_ADDRESS);
        final var tokenId = ConversionUtils.asTokenId(tokenAddress);
        return TokenFeeScheduleUpdateTransactionBody.newBuilder()
                .tokenId(tokenId)
                .customFees(
                        isFungible
                                ? customFungibleFees(call, addressIdConverter, tokenId)
                                : customNonFungibleFees(call, addressIdConverter, tokenId))
                .build();
    }

    private List<CustomFee> customFungibleFees(
            @NonNull Tuple call, @NonNull final AddressIdConverter addressIdConverter, @NonNull final TokenID tokenId) {
        return Stream.concat(
                        decodeFixedFees(call.get(FIXED_FEE), addressIdConverter, tokenId),
                        decodeFractionalFees(call.get(FRACTIONAL_FEE), addressIdConverter))
                .toList();
    }

    private List<CustomFee> customNonFungibleFees(
            @NonNull final Tuple call,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final TokenID tokenId) {
        return Stream.concat(
                        decodeFixedFees(call.get(FIXED_FEE), addressIdConverter, tokenId),
                        decodeRoyaltyFees(call.get(ROYALTY_FEE), addressIdConverter))
                .toList();
    }

    private Stream<CustomFee> decodeFixedFees(
            @NonNull final Tuple[] fixedFee,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final TokenID tokenId) {
        return Arrays.stream(fixedFee).map(fee -> CustomFee.newBuilder()
                .fixedFee(FixedFee.newBuilder()
                        .amount(fee.get(FIXED_FEE_AMOUNT))
                        .denominatingTokenId(determineDenominatingToken(fee, tokenId))
                        .build())
                .feeCollectorAccountId(addressIdConverter.convert(fee.get(FIXED_FEE_FEE_COLLECTOR)))
                .build());
    }

    private Stream<CustomFee> decodeFractionalFees(
            @NonNull final Tuple[] fractionalFees, @NonNull final AddressIdConverter addressIdConverter) {
        return Arrays.stream(fractionalFees).map(fee -> CustomFee.newBuilder()
                .fractionalFee(FractionalFee.newBuilder()
                        .fractionalAmount(Fraction.newBuilder()
                                .numerator(fee.get(FRACTIONAL_FEE_NUMERATOR))
                                .denominator(fee.get(FRACTIONAL_FEE_DENOMINATOR))
                                .build())
                        .minimumAmount(fee.get(FRACTIONAL_FEE_MIN_AMOUNT))
                        .maximumAmount(fee.get(FRACTIONAL_FEE_MAX_AMOUNT))
                        .netOfTransfers(fee.get(FRACTIONAL_FEE_NET_OF_TRANSFERS))
                        .build())
                .feeCollectorAccountId(addressIdConverter.convert(fee.get(FRACTIONAL_FEE_FEE_COLLECTOR)))
                .build());
    }

    private Stream<CustomFee> decodeRoyaltyFees(
            @NonNull final Tuple[] royaltyFees, @NonNull final AddressIdConverter addressIdConverter) {
        return Arrays.stream(royaltyFees).map(fee -> CustomFee.newBuilder()
                .royaltyFee(RoyaltyFee.newBuilder()
                        .exchangeValueFraction(Fraction.newBuilder()
                                .numerator(fee.get(ROYALTY_FEE_NUMERATOR))
                                .denominator(fee.get(ROYALTY_FEE_DENOMINATOR))
                                .build())
                        .fallbackFee(getFallbackFee(fee))
                        .build())
                .feeCollectorAccountId(addressIdConverter.convert(fee.get(ROYALTY_FEE_FEE_COLLECTOR)))
                .build());
    }

    // In the solidity structure for Fixed Fees we have the property `bool useCurrentTokenForPayment` that is not
    // present in the protobuf version.
    // In order to determine the denominating token we need to check if the property is present.
    private TokenID determineDenominatingToken(@NonNull final Tuple fee, @NonNull final TokenID tokenId) {
        final boolean useCurrentToken = fee.get(FIXED_FEE_USE_CURRENT_TOKEN_FOR_PAYMENT);
        return useCurrentToken
                ? tokenId
                : getIfPresent(fee.get(FIXED_FEE_TOKEN_ID), fee.get(FIXED_FEE_USE_HBARS_FOR_PAYMENT));
    }

    private TokenID getIfPresent(@NonNull final Address address, final boolean useHbarsForPayment) {
        final var tokenId = ConversionUtils.asTokenId(address);
        return useHbarsForPayment || tokenId.equals(TokenID.DEFAULT) ? null : tokenId;
    }

    private FixedFee getFallbackFee(@NonNull Tuple fee) {
        final Address tokenAddress = fee.get(ROYALTY_FEE_TOKEN_ID);
        final long amount = fee.get(ROYALTY_FEE_AMOUNT);
        return ConversionUtils.asTokenId(tokenAddress).equals(TokenID.DEFAULT) && amount == 0
                ? null
                : FixedFee.newBuilder()
                        .amount(amount)
                        .denominatingTokenId(getIfPresent(tokenAddress, fee.get(ROYALTY_FEE_USE_HBARS_FOR_PAYMENT)))
                        .build();
    }
}
