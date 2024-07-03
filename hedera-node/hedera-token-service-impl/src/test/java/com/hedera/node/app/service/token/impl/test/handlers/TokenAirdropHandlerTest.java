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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.FeeContextImpl;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.impl.util.CryptoTransferFeeCalculator;
import com.hedera.node.app.service.token.impl.util.TokenAssociateToAccountFeeCalculator;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeCalculatorFactory;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.TokensConfig;
import com.swirlds.config.api.Configuration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class TokenAirdropHandlerTest extends CryptoTransferHandlerTestBase {

    private static final int MAX_TOKEN_TRANSFERS = 10;

    @Mock
    private Configuration config;

    @Mock
    private TokensConfig tokensConfig;

    @Mock
    private FeeContextImpl feeContext;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private FeeCalculatorFactory feeCalculatorFactory;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private ReadableTokenRelationStore readableTokenRelationStore;

    @SuppressWarnings("DataFlowIssue")
    @Test
    void pureChecksNullArgThrows() {
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void pureChecksFungibleTokenTransfersHasMissingTokenId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token((TokenID) null)
                .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                .build());
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_ID));
    }

    @Test
    void pureChecksFungibleTokenTransfersHasMissingAccountId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are TOKEN fungible amount transfers, not HBAR amount transfers
                .transfers(
                        ACCT_3333_MINUS_10,
                        ACCT_4444_PLUS_10
                                .copyBuilder()
                                .accountID((AccountID) null)
                                .build())
                .build());
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID));
    }

    @Test
    void pureChecksFungibleTokenTransfersHasRepeatedAccountId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(
                        ACCT_4444_MINUS_5,
                        ACCT_4444_MINUS_5.copyBuilder().amount(5).build())
                .build());
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksFungibleTokenTransfersHasNonZeroTokenSum() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(
                        ACCT_3333_MINUS_10,
                        ACCT_4444_PLUS_10.copyBuilder().amount(5).build())
                .build());
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN));
    }

    @Test
    void pureChecksHasValidFungibleTokenTransfers() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                .build());
        Assertions.assertThatCode(() -> tokenAirdropHandler.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasMissingTokenId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token((TokenID) null)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444)
                .build());
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_ID));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasInvalidNftId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(
                        SERIAL_1_FROM_3333_TO_4444.copyBuilder().serialNumber(0).build())
                .build());
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasMissingSenderId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // These are nft transfers, not hbar or fungible token transfers
                .nftTransfers(SERIAL_2_FROM_4444_TO_3333
                        .copyBuilder()
                        .senderAccountID((AccountID) null)
                        .build())
                .build());
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasMissingReceiverId() {
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444
                        .copyBuilder()
                        .receiverAccountID((AccountID) null)
                        .build())
                .build());
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID));
    }

    @Test
    void pureChecksNonFungibleTokenTransfersHasRepeatedNftId() {
        final var txn = newTokenAirdrop(
                TokenTransferList.newBuilder()
                        .token(TOKEN_2468)
                        .nftTransfers(SERIAL_1_FROM_3333_TO_4444)
                        .build(),
                TokenTransferList.newBuilder()
                        .token(TOKEN_2468)
                        .nftTransfers(SERIAL_2_FROM_4444_TO_3333)
                        .build());
        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST));
    }

    @Test
    void pureChecksHasValidNonFungibleTokenTransfers() {
        // Note: this test only checks for valid non-fungible token transfers (WITHOUT any hbar or fungible token
        // transfers)
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .nftTransfers(
                        SERIAL_1_FROM_3333_TO_4444,
                        SERIAL_2_FROM_4444_TO_3333,
                        SERIAL_1_FROM_3333_TO_4444.copyBuilder().serialNumber(3).build())
                .build());
        Assertions.assertThatCode(() -> tokenAirdropHandler.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksTokenTransfersDoesNotHaveFungibleOrNonFungibleAmount() {
        // This test checks that, if any token transfer is present, it must have at least one fungible or non-fungible
        // balance not equal to zero
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                // transfers and nftTransfers are intentionally empty (will result in a count of zero transfers)
                .transfers()
                .nftTransfers()
                .build());

        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksTokenTransferHasBothFungibleAndNonFungibleAmounts() {
        // This test checks that, if a transfer for a token is present, it must have ONLY a fungible transfer OR an NFT
        // transfer, but not both
        final var txn = newTokenAirdrop(TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(ACCT_3333_MINUS_10, ACCT_4444_PLUS_10)
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444, SERIAL_1_FROM_3333_TO_4444)
                .build());

        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_ACCOUNT_AMOUNTS));
    }

    @Test
    void pureChecksTokenTransfersAboveMax() {
        final var txn = newTokenAirdrop(transactionBodyAboveMaxTransferLimit());

        Assertions.assertThatThrownBy(() -> tokenAirdropHandler.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
    }

    @Test
    void pureChecksForEmptyHbarTransferAndEmptyTokenTransfers() {
        // It's actually valid to have no hbar transfers and no token transfers
        final var txn = newTokenAirdrop(Collections.emptyList());
        Assertions.assertThatCode(() -> tokenAirdropHandler.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void pureChecksHasValidHbarAndTokenTransfers() {
        // Tests that valid hbar transfers, fungible transfers, and non-fungible transfers are all valid when given
        // together
        final var token9753 = asToken(9753);
        final var token9754 = asToken(9754);
        final var txn = newTokenAirdrop(List.of(
                // Valid fungible token transfers
                TokenTransferList.newBuilder()
                        .token(TOKEN_2468)
                        .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                        .build(),
                TokenTransferList.newBuilder()
                        .token(token9754)
                        .transfers(ACCT_4444_MINUS_5, ACCT_3333_PLUS_5)
                        .build(),
                // Valid nft token transfers
                TokenTransferList.newBuilder()
                        .token(token9753)
                        .nftTransfers(SERIAL_1_FROM_3333_TO_4444, SERIAL_2_FROM_4444_TO_3333)
                        .build()));

        Assertions.assertThatCode(() -> tokenAirdropHandler.pureChecks(txn)).doesNotThrowAnyException();
    }

    @Test
    void calculateFeesWithNoAirdropBody() {
        when(feeContext.body()).thenReturn(TransactionBody.DEFAULT);
        assertThrows(NullPointerException.class, () -> tokenAirdropHandler.calculateFees(feeContext));
    }

    @Test
    void calculateFeesNotSupportedOperation() {
        setupAirdropMocks(TokenAirdropTransactionBody.DEFAULT, false);

        final var exception = assertThrows(HandleException.class, () -> tokenAirdropHandler.calculateFees(feeContext));
        assertEquals(ResponseCodeEnum.NOT_SUPPORTED, exception.getStatus());
    }

    @Test
    void calculateFeesShouldSumAllRequiredFees() {
        final var fungibleTransferList = TokenTransferList.newBuilder()
                .token(TOKEN_2468)
                .transfers(ACCT_4444_MINUS_5)
                .build();
        final var nonFungibleTransferList = TokenTransferList.newBuilder()
                .token(asToken(2469))
                .nftTransfers(SERIAL_1_FROM_3333_TO_4444)
                .build();
        final var airdropBody = TokenAirdropTransactionBody.newBuilder()
                .tokenTransfers(fungibleTransferList, nonFungibleTransferList)
                .build();
        setupAirdropMocks(airdropBody, true);

        final var defaultAirdropFees = new Fees(10, 10, 10);
        when(feeContext.feeCalculatorFactory()).thenReturn(feeCalculatorFactory);
        when(feeCalculatorFactory.feeCalculator(SubType.DEFAULT)).thenReturn(feeCalculator);
        when(feeCalculator.calculate()).thenReturn(defaultAirdropFees);

        when(feeContext.readableStore(ReadableTokenRelationStore.class)).thenReturn(readableTokenRelationStore);
        when(readableTokenRelationStore.get(any(), any())).thenReturn(null);

        try (var cryptoTransferFeeCalculator = mockStatic(CryptoTransferFeeCalculator.class);
                var tokenAssociateToAccountFeeCalculator = mockStatic(TokenAssociateToAccountFeeCalculator.class); ) {

            cryptoTransferFeeCalculator
                    .when(() -> CryptoTransferFeeCalculator.calculate(any(), any(), any()))
                    .thenReturn(new Fees(20, 20, 20));
            tokenAssociateToAccountFeeCalculator
                    .when(() -> TokenAssociateToAccountFeeCalculator.calculate(any(), any()))
                    .thenReturn(new Fees(30, 30, 30));

            final var fees = tokenAirdropHandler.calculateFees(feeContext);
            assertEquals(60, fees.networkFee());
            assertEquals(60, fees.nodeFee());
            assertEquals(60, fees.serviceFee());
        }
    }

    private void setupAirdropMocks(TokenAirdropTransactionBody body, boolean enableAirdrop) {
        when(feeContext.body()).thenReturn(transactionBody);
        when(transactionBody.tokenAirdropOrThrow()).thenReturn(body);

        when(feeContext.configuration()).thenReturn(config);
        when(config.getConfigData(TokensConfig.class)).thenReturn(tokensConfig);
        when(tokensConfig.airdropsEnabled()).thenReturn(enableAirdrop);
    }

    private List<TokenTransferList> transactionBodyAboveMaxTransferLimit() {
        List<TokenTransferList> result = new ArrayList<>();

        for (int i = 0; i <= MAX_TOKEN_TRANSFERS; i++) {
            result.add(TokenTransferList.newBuilder()
                    .token(TOKEN_2468)
                    .transfers(ACCT_4444_PLUS_10)
                    .nftTransfers(SERIAL_1_FROM_3333_TO_4444, SERIAL_1_FROM_3333_TO_4444)
                    .build());
        }

        return result;
    }
}
