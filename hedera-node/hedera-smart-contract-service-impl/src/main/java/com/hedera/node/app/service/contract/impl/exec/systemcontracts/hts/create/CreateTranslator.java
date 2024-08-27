/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create;

import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FIXED_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FIXED_FEE_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.FRACTIONAL_FEE_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_V1;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_V2;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_V3;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.HEDERA_TOKEN_WITH_METADATA;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE;
import static com.hedera.node.app.hapi.utils.contracts.ParsingConstants.ROYALTY_FEE_V2;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

public class CreateTranslator extends AbstractCallTranslator<HtsCallAttempt> {

    public static final Function CREATE_FUNGIBLE_TOKEN_V1 =
            new Function("createFungibleToken(" + HEDERA_TOKEN_V1 + ",uint,uint)", "(int64,address)");
    public static final Function CREATE_FUNGIBLE_TOKEN_V2 =
            new Function("createFungibleToken(" + HEDERA_TOKEN_V2 + ",uint64,uint32)", "(int64,address)");
    public static final Function CREATE_FUNGIBLE_TOKEN_V3 =
            new Function("createFungibleToken(" + HEDERA_TOKEN_V3 + ",int64,int32)", "(int64,address)");
    public static final Function CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1 = new Function(
            "createFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V1
                    + ",uint,uint,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ")",
            "(int64,address)");
    public static final Function CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2 = new Function(
            "createFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V2
                    + ",uint64,uint32,"
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE
                    + ARRAY_BRACKETS
                    + ")",
            "(int64,address)");
    public static final Function CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3 = new Function(
            "createFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V3
                    + ",int64,int32,"
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE_V2
                    + ARRAY_BRACKETS
                    + ")",
            "(int64,address)");

    public static final Function CREATE_NON_FUNGIBLE_TOKEN_V1 =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_V1 + ")", "(int64,address)");
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_V2 =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_V2 + ")", "(int64,address)");
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_V3 =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_V3 + ")", "(int64,address)");

    public static final Function CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1 = new Function(
            "createNonFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V1
                    + ","
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ")",
            "(int64,address)");
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2 = new Function(
            "createNonFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V2
                    + ","
                    + FIXED_FEE
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE
                    + ARRAY_BRACKETS
                    + ")",
            "(int64,address)");
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3 = new Function(
            "createNonFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_V3
                    + ","
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE_V2
                    + ARRAY_BRACKETS
                    + ")",
            "(int64,address)");

    public static final Function CREATE_FUNGIBLE_TOKEN_WITH_METADATA =
            new Function("createFungibleToken(" + HEDERA_TOKEN_WITH_METADATA + ",int64,int32)", "(int64,address)");
    public static final Function CREATE_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES = new Function(
            "createFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_WITH_METADATA
                    + ",int64,int32,"
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + FRACTIONAL_FEE_V2
                    + ARRAY_BRACKETS
                    + ")",
            "(int64,address)");
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA =
            new Function("createNonFungibleToken(" + HEDERA_TOKEN_WITH_METADATA + ")", "(int64,address)");
    public static final Function CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES = new Function(
            "createNonFungibleTokenWithCustomFees("
                    + HEDERA_TOKEN_WITH_METADATA
                    + ","
                    + FIXED_FEE_V2
                    + ARRAY_BRACKETS
                    + ","
                    + ROYALTY_FEE_V2
                    + ARRAY_BRACKETS
                    + ")",
            "(int64,address)");

    /**
     * A set of `Function` objects representing various create functions for fungible and non-fungible tokens.
     * This set is used in {@link com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor}
     * to determine if a given call attempt is a creation call, because we do not allow sending value to Hedera system contracts
     * except in the case of token creation
     */
    public static final Map<Function, CreateDecoderFunction> decoderMap = new HashMap<>();

    @Inject
    public CreateTranslator(final CreateDecoder decoder) {
        decoderMap.put(CREATE_FUNGIBLE_TOKEN_V1, decoder::decodeCreateFungibleTokenV1);
        decoderMap.put(CREATE_FUNGIBLE_TOKEN_V2, decoder::decodeCreateFungibleTokenV2);
        decoderMap.put(CREATE_FUNGIBLE_TOKEN_V3, decoder::decodeCreateFungibleTokenV3);
        decoderMap.put(CREATE_FUNGIBLE_TOKEN_WITH_METADATA, decoder::decodeCreateFungibleTokenWithMetadata);
        decoderMap.put(CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1, decoder::decodeCreateFungibleTokenWithCustomFeesV1);
        decoderMap.put(CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2, decoder::decodeCreateFungibleTokenWithCustomFeesV2);
        decoderMap.put(CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3, decoder::decodeCreateFungibleTokenWithCustomFeesV3);
        decoderMap.put(
                CREATE_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES,
                decoder::decodeCreateFungibleTokenWithMetadataAndCustomFees);
        decoderMap.put(CREATE_NON_FUNGIBLE_TOKEN_V1, decoder::decodeCreateNonFungibleV1);
        decoderMap.put(CREATE_NON_FUNGIBLE_TOKEN_V2, decoder::decodeCreateNonFungibleV2);
        decoderMap.put(CREATE_NON_FUNGIBLE_TOKEN_V3, decoder::decodeCreateNonFungibleV3);
        decoderMap.put(CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA, decoder::decodeCreateNonFungibleWithMetadata);
        decoderMap.put(CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1, decoder::decodeCreateNonFungibleWithCustomFeesV1);
        decoderMap.put(CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2, decoder::decodeCreateNonFungibleWithCustomFeesV2);
        decoderMap.put(CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3, decoder::decodeCreateNonFungibleWithCustomFeesV3);
        decoderMap.put(
                CREATE_NON_FUNGIBLE_TOKEN_WITH_METADATA_AND_CUSTOM_FEES,
                decoder::decodeCreateNonFungibleWithMetadataAndCustomFees);
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return attempt.isSelector(decoderMap.keySet().toArray(Function[]::new));
    }

    @Override
    public ClassicCreatesCall callFrom(@NonNull HtsCallAttempt attempt) {
        return new ClassicCreatesCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                nominalBodyFor(attempt),
                attempt.defaultVerificationStrategy(),
                attempt.senderAddress(),
                attempt.addressIdConverter());
    }

    private @Nullable TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        final var inputBytes = attempt.inputBytes();
        final var senderId = attempt.senderId();
        final var nativeOperations = attempt.nativeOperations();
        final var addressIdConverter = attempt.addressIdConverter();

        return decoderMap.entrySet().stream()
                .filter(entry -> attempt.isSelector(entry.getKey()))
                .map(entry -> entry.getValue().decode(inputBytes, senderId, nativeOperations, addressIdConverter))
                .findFirst()
                .orElse(null);
    }
}
