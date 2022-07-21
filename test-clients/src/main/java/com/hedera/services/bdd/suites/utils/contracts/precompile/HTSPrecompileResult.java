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
package com.hedera.services.bdd.suites.utils.contracts.precompile;

import static com.hedera.services.parsing.ParsingConstants.ADDRESS;
import static com.hedera.services.parsing.ParsingConstants.ARRAY_BRACKETS;
import static com.hedera.services.parsing.ParsingConstants.BYTES32;
import static com.hedera.services.parsing.ParsingConstants.FIXED_FEE;
import static com.hedera.services.parsing.ParsingConstants.FRACTIONAL_FEE;
import static com.hedera.services.parsing.ParsingConstants.HEDERA_TOKEN;
import static com.hedera.services.parsing.ParsingConstants.RESPONSE_STATUS_AT_BEGINNING;
import static com.hedera.services.parsing.ParsingConstants.ROYALTY_FEE;
import static com.hedera.services.parsing.ParsingConstants.allowanceOfType;
import static com.hedera.services.parsing.ParsingConstants.balanceOfType;
import static com.hedera.services.parsing.ParsingConstants.burnReturnType;
import static com.hedera.services.parsing.ParsingConstants.decimalsType;
import static com.hedera.services.parsing.ParsingConstants.ercTransferType;
import static com.hedera.services.parsing.ParsingConstants.getApprovedType;
import static com.hedera.services.parsing.ParsingConstants.hapiAllowanceOfType;
import static com.hedera.services.parsing.ParsingConstants.hapiGetApprovedType;
import static com.hedera.services.parsing.ParsingConstants.hapiIsApprovedForAllType;
import static com.hedera.services.parsing.ParsingConstants.isApprovedForAllType;
import static com.hedera.services.parsing.ParsingConstants.mintReturnType;
import static com.hedera.services.parsing.ParsingConstants.nameType;
import static com.hedera.services.parsing.ParsingConstants.notSpecifiedType;
import static com.hedera.services.parsing.ParsingConstants.ownerOfType;
import static com.hedera.services.parsing.ParsingConstants.symbolType;
import static com.hedera.services.parsing.ParsingConstants.tokenUriType;
import static com.hedera.services.parsing.ParsingConstants.totalSupplyType;

import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.services.bdd.suites.utils.contracts.ContractCallResult;
import com.hedera.services.parsing.ParsingConstants;
import com.hedera.services.parsing.ParsingConstants.FunctionType;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;

public class HTSPrecompileResult implements ContractCallResult {
    private HTSPrecompileResult() {}

    public static final String TOKEN_INFO_REPLACED_ADDRESS =
        "("
            + HEDERA_TOKEN.replace(removeBrackets(ADDRESS), removeBrackets(BYTES32))
            + ",int64,bool,bool,bool,"
            + FIXED_FEE.replace(removeBrackets(ADDRESS), removeBrackets(BYTES32))
            + ARRAY_BRACKETS
            + ","
            + FRACTIONAL_FEE.replace("address", "bytes32")
            + ARRAY_BRACKETS
            + ","
            + ROYALTY_FEE.replace("address", "bytes32")
            + ARRAY_BRACKETS
            + ",string"
            + ")";
    public static final String FUNGIBLE_TOKEN_INFO_REPLACED_ADDRESS =
        "(" + TOKEN_INFO_REPLACED_ADDRESS + ",int32" + ")";
    public static final String NON_FUNGIBLE_TOKEN_INFO_REPLACED_ADDRESS =
        "(" + TOKEN_INFO_REPLACED_ADDRESS + ",int64,bytes32,int64,bytes,bytes32" + ")";

    public static final TupleType getTokenInfoTypeReplacedAddress =
        TupleType.parse(RESPONSE_STATUS_AT_BEGINNING + TOKEN_INFO_REPLACED_ADDRESS + ")");
    public static final TupleType getFungibleTokenInfoTypeReplacedAddress =
        TupleType.parse(
            RESPONSE_STATUS_AT_BEGINNING + FUNGIBLE_TOKEN_INFO_REPLACED_ADDRESS + ")");
    public static final TupleType getNonFungibleTokenInfoTypeReplacedAddress =
        TupleType.parse(
            RESPONSE_STATUS_AT_BEGINNING + NON_FUNGIBLE_TOKEN_INFO_REPLACED_ADDRESS + ")");


    public static HTSPrecompileResult htsPrecompileResult() {
        return new HTSPrecompileResult();
    }

    private FunctionType functionType = FunctionType.NOT_SPECIFIED;
    private TupleType tupleType = notSpecifiedType;
    private ResponseCodeEnum status;
    private long totalSupply;
    private long[] serialNumbers;
    private long serialNumber;
    private int decimals;
    private long creationTime;
    private byte[] owner;
    private byte[] approved;
    private String name;
    private String symbol;
    private String metadata;
    private long balance;
    private long allowance;
    private boolean ercFungibleTransferStatus;
    private boolean isApprovedForAllStatus;
    private TokenInfo tokenInfo;

    public HTSPrecompileResult forFunction(final FunctionType functionType) {
        tupleType = switch (functionType) {
            case HAPI_MINT -> mintReturnType;
            case HAPI_BURN ->  burnReturnType;
            case ERC_TOTAL_SUPPLY ->  totalSupplyType;
            case ERC_DECIMALS ->  decimalsType;
            case ERC_BALANCE -> balanceOfType;
            case ERC_OWNER ->  ownerOfType;
            case ERC_GET_APPROVED ->  getApprovedType;
            case ERC_NAME -> nameType;
            case ERC_SYMBOL -> symbolType;
            case ERC_TOKEN_URI ->  tokenUriType;
            case ERC_TRANSFER ->  ercTransferType;
            case ERC_ALLOWANCE ->  allowanceOfType;
            case ERC_IS_APPROVED_FOR_ALL ->  isApprovedForAllType;
            case HAPI_GET_APPROVED ->  hapiGetApprovedType;
            case HAPI_ALLOWANCE ->  hapiAllowanceOfType;
            case HAPI_IS_APPROVED_FOR_ALL ->  hapiIsApprovedForAllType;
            case HAPI_GET_TOKEN_INFO ->  getTokenInfoTypeReplacedAddress;
            case HAPI_GET_FUNGIBLE_TOKEN_INFO ->  getFungibleTokenInfoTypeReplacedAddress;
            case HAPI_GET_NON_FUNGIBLE_TOKEN_INFO ->  getNonFungibleTokenInfoTypeReplacedAddress;
            default ->  notSpecifiedType;
        };

        this.functionType = functionType;
        return this;
    }

    public HTSPrecompileResult withStatus(final ResponseCodeEnum status) {
        this.status = status;
        return this;
    }

    public HTSPrecompileResult withTotalSupply(final long totalSupply) {
        this.totalSupply = totalSupply;
        return this;
    }

    public HTSPrecompileResult withSerialNumbers(final long... serialNumbers) {
        this.serialNumbers = serialNumbers;
        return this;
    }

    public HTSPrecompileResult withDecimals(final int decimals) {
        this.decimals = decimals;
        return this;
    }

    public HTSPrecompileResult withBalance(final long balance) {
        this.balance = balance;
        return this;
    }

    public HTSPrecompileResult withOwner(final byte[] address) {
        this.owner = address;
        return this;
    }

    public HTSPrecompileResult withSpender(final byte[] spender) {
        this.approved = spender;
        return this;
    }

    public HTSPrecompileResult withApproved(final ResponseCodeEnum status, final byte[] approved) {
        this.status = status;
        this.approved = approved;
        return this;
    }

    public HTSPrecompileResult withName(final String name) {
        this.name = name;
        return this;
    }

    public HTSPrecompileResult withSymbol(final String symbol) {
        this.symbol = symbol;
        return this;
    }

    public HTSPrecompileResult withTokenUri(final String tokenUri) {
        this.metadata = tokenUri;
        return this;
    }

    public HTSPrecompileResult withSerialNumber(final long serialNumber) {
        this.serialNumber = serialNumber;
        return this;
    }

    public HTSPrecompileResult withCreationTime(final long creationTime) {
        this.creationTime = creationTime;
        return this;
    }

    public HTSPrecompileResult withErcFungibleTransferStatus(
            final boolean ercFungibleTransferStatus) {
        this.ercFungibleTransferStatus = ercFungibleTransferStatus;
        return this;
    }

    public HTSPrecompileResult withAllowance(final long allowance) {
        this.allowance = allowance;
        return this;
    }

    public HTSPrecompileResult withIsApprovedForAll(final boolean isApprovedForAllStatus) {
        this.isApprovedForAllStatus = isApprovedForAllStatus;
        return this;
    }

    public HTSPrecompileResult withIsApprovedForAll(
        final ResponseCodeEnum status, final boolean isApprovedForAllStatus) {
        this.status = status;
        this.isApprovedForAllStatus = isApprovedForAllStatus;
        return this;
    }

    public HTSPrecompileResult withTokenInfo(final TokenInfo tokenInfo) {
        this.tokenInfo = tokenInfo;
        return this;
    }

    @Override
    public Bytes getBytes() {
        if (ParsingConstants.FunctionType.ERC_OWNER.equals(functionType)) {
            return Bytes.wrap(expandByteArrayTo32Length(owner));
        } else if (ParsingConstants.FunctionType.ERC_GET_APPROVED.equals(functionType)) {
            return Bytes.wrap(expandByteArrayTo32Length(approved));
        }

        final Tuple result =

        switch (functionType) {
            case HAPI_MINT ->
                    Tuple.of(status.getNumber(), BigInteger.valueOf(totalSupply), serialNumbers);
            case HAPI_BURN ->  Tuple.of(status.getNumber(), BigInteger.valueOf(totalSupply));
            case ERC_TOTAL_SUPPLY ->  Tuple.of(BigInteger.valueOf(totalSupply));
            case ERC_DECIMALS -> Tuple.of(decimals);
            case ERC_BALANCE ->  Tuple.of(BigInteger.valueOf(balance));
            case ERC_NAME -> Tuple.of(name);
            case ERC_SYMBOL ->  Tuple.of(symbol);
            case ERC_TOKEN_URI -> Tuple.of(metadata);
            case ERC_TRANSFER ->  Tuple.of(ercFungibleTransferStatus);
            case ERC_IS_APPROVED_FOR_ALL -> Tuple.of(isApprovedForAllStatus);
            case ERC_ALLOWANCE ->  Tuple.of(BigInteger.valueOf(allowance));
            case HAPI_IS_APPROVED_FOR_ALL ->
                Tuple.of(status.getNumber(), isApprovedForAllStatus);
            case HAPI_ALLOWANCE ->
                    Tuple.of(status.getNumber(), BigInteger.valueOf(allowance));
            case HAPI_GET_APPROVED ->
                    Tuple.of(status.getNumber(), expandByteArrayTo32Length(approved));
            case HAPI_GET_TOKEN_INFO -> getTupleForGetTokenInfo();
            case HAPI_GET_FUNGIBLE_TOKEN_INFO -> getTupleForGetFungibleTokenInfo();
            case HAPI_GET_NON_FUNGIBLE_TOKEN_INFO -> getTupleForGetNonFungibleTokenInfo();
            default -> Tuple.of(status.getNumber());
        };

        return Bytes.wrap(tupleType.encode(result).array());
    }

    private Tuple getTupleForGetTokenInfo() {
        return Tuple.of(status.getNumber(), getTupleForTokenInfo());
    }

    private Tuple getTupleForGetFungibleTokenInfo() {
        return Tuple.of(status.getNumber(), Tuple.of(getTupleForTokenInfo(), decimals));
    }

    private Tuple getTupleForGetNonFungibleTokenInfo() {
        return Tuple.of(
                status.getNumber(),
                Tuple.of(
                        getTupleForTokenInfo(),
                        serialNumber,
                        expandByteArrayTo32Length(owner),
                        creationTime,
                        metadata.getBytes(),
                        expandByteArrayTo32Length(approved)));
    }

    private Tuple getTupleForTokenInfo() {
        return Tuple.of(
                getHederaTokenTuple(),
                tokenInfo.totalSupply(),
                tokenInfo.deleted(),
                tokenInfo.defaultKycStatus(),
                tokenInfo.pauseStatus(),
                getFixedFeesTuples(),
                getFractionalFeesTuples(),
                getRoyaltyFeesTuples(),
                tokenInfo.ledgerId());
    }

    private Tuple[] getFixedFeesTuples() {
        final var fixedFees = tokenInfo.fixedFees();
        final Tuple[] fixedFeesTuples = new Tuple[fixedFees.size()];
        for (int i = 0; i < fixedFees.size(); i++) {
            final var fixedFee = fixedFees.get(i);
            final var fixedFeeTuple =
                    Tuple.of(
                            fixedFee.amount(),
                            fixedFee.tokenId() != null
                                    ? fixedFee.tokenId().toArray()
                                    : new byte[32],
                            fixedFee.useHbarsForPayment(),
                            fixedFee.useCurrentTokenForPayment(),
                            fixedFee.feeCollector() != null
                                    ? fixedFee.feeCollector().toArray()
                                    : new byte[32]);
            fixedFeesTuples[i] = fixedFeeTuple;
        }

        return fixedFeesTuples;
    }

    private Tuple[] getFractionalFeesTuples() {
        final var fractionalFees = tokenInfo.fractionalFees();
        final Tuple[] fractionalFeesTuples = new Tuple[fractionalFees.size()];
        for (int i = 0; i < fractionalFees.size(); i++) {
            final var fractionalFee = fractionalFees.get(i);
            final var fractionalFeeTuple =
                    Tuple.of(
                            fractionalFee.numerator(),
                            fractionalFee.denominator(),
                            fractionalFee.minimumAmount(),
                            fractionalFee.maximumAmount(),
                            fractionalFee.netOfTransfers(),
                            fractionalFee.feeCollector() != null
                                    ? fractionalFee.feeCollector().toArray()
                                    : new byte[32]);
            fractionalFeesTuples[i] = fractionalFeeTuple;
        }

        return fractionalFeesTuples;
    }

    private Tuple[] getRoyaltyFeesTuples() {
        final var royaltyFees = tokenInfo.royaltyFees();
        final Tuple[] royaltyFeesTuples = new Tuple[royaltyFees.size()];
        for (int i = 0; i < royaltyFees.size(); i++) {
            final var royaltyFee = royaltyFees.get(i);
            final var royaltyFeeTuple =
                    Tuple.of(
                            royaltyFee.numerator(),
                            royaltyFee.denominator(),
                            royaltyFee.amount(),
                            royaltyFee.tokenId() != null
                                    ? royaltyFee.tokenId().toArray()
                                    : new byte[32],
                            royaltyFee.useHbarsForPayment(),
                            royaltyFee.feeCollector() != null
                                    ? royaltyFee.feeCollector().toArray()
                                    : new byte[32]);
            royaltyFeesTuples[i] = royaltyFeeTuple;
        }

        return royaltyFeesTuples;
    }

    private Tuple getHederaTokenTuple() {
        final var hederaToken = tokenInfo.token();
        final var expiry = hederaToken.expiry();
        final var expiryTuple =
                Tuple.of(
                        expiry.second(),
                        expiry.autoRenewAccount().toArray(),
                        expiry.autoRenewPeriod());

        return Tuple.of(
                hederaToken.name(),
                hederaToken.symbol(),
                hederaToken.treasury().toArray(),
                hederaToken.memo(),
                hederaToken.tokenSupplyType(),
                hederaToken.maxSupply(),
                hederaToken.freezeDefault(),
                getTokenKeysTuples(),
                expiryTuple);
    }

    private Tuple[] getTokenKeysTuples() {
        final var hederaToken = tokenInfo.token();
        final var tokenKeys = hederaToken.tokenKeys();
        final Tuple[] tokenKeysTuples = new Tuple[tokenKeys.size()];
        for (int i = 0; i < tokenKeys.size(); i++) {
            final var key = tokenKeys.get(i);
            final var keyValue = key.key();
            Tuple keyValueTuple =
                    Tuple.of(
                            keyValue.inheritAccountKey(),
                            keyValue.contractId() != null
                                    ? keyValue.contractId().toArray()
                                    : new byte[32],
                            keyValue.ed25519().toByteArray(),
                            keyValue.ECDSA_secp256k1().toByteArray(),
                            keyValue.delegatableContractId() != null
                                    ? keyValue.delegatableContractId().toArray()
                                    : new byte[32]);
            tokenKeysTuples[i] = (Tuple.of(BigInteger.valueOf(key.keyType()), keyValueTuple));
        }

        return tokenKeysTuples;
    }

    private static String removeBrackets(final String type) {
        final var typeWithRemovedOpenBracket = type.replace("(", "");
        return typeWithRemovedOpenBracket.replace(")", "");
    }

    public static byte[] expandByteArrayTo32Length(final byte[] bytesToExpand) {
        byte[] expandedArray = new byte[32];

        System.arraycopy(
                bytesToExpand,
                0,
                expandedArray,
                expandedArray.length - bytesToExpand.length,
                bytesToExpand.length);
        return expandedArray;
    }
}
