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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_REFERENCE_REPEATED;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenReference;
import com.hedera.hapi.node.token.TokenRejectTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.TokenRejectHandler;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenRejectHandlerTest extends CryptoTransferHandlerTestBase {
    public static final String LEDGER_TOKEN_REJECTS_MAX_LEN = "ledger.tokenRejects.maxLen";

    private final TokenReference tokenRefFungible =
            TokenReference.newBuilder().fungibleToken(fungibleTokenId).build();
    private final TokenReference tokenRefNFT =
            TokenReference.newBuilder().nft(nftIdSl1).build();

    private Configuration config;

    private TokenRejectHandler subject;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        subject = new TokenRejectHandler();
    }

    @Test
    void handleNullArgs() {
        Assertions.assertThatThrownBy(() -> subject.handle(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void happyPathWorks() {
        // Given:
        refreshWritableStores();
        final var txn = newTokenReject(ownerId, tokenRefFungible, tokenRefNFT);
        given(handleContext.body()).willReturn(txn);
        givenStoresAndConfig(handleContext);
        given(handleContext.payer()).willReturn(ownerId);
        given(handleContext.body()).willReturn(txn);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(configProvider.getConfiguration()).willReturn(versionedConfig);

        final var initialSenderBalance = writableAccountStore.get(ownerId).tinybarBalance();
        final var initialSenderTokenBalance =
                writableTokenRelStore.get(ownerId, fungibleTokenId).balance();
        final var initialTreasuryTokenBalance =
                writableTokenRelStore.get(treasuryId, fungibleTokenId).balance();
        final var initialFeeCollectorBalance =
                writableAccountStore.get(feeCollectorId).tinybarBalance();

        // When:
        subject.handle(handleContext);

        // Then:
        assertThat(writableAccountStore.modifiedAccountsInState()).hasSize(2); // includes fee collector for custom fees
        assertThat(writableAccountStore.modifiedAccountsInState()).contains(ownerId, treasuryId);
        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(2);

        // Verify balance removal
        final var endSenderTokenBalance =
                writableTokenRelStore.get(ownerId, fungibleTokenId).balance();
        assertThat(endSenderTokenBalance).isZero();
        final var endTreasuryTokenBalance =
                writableTokenRelStore.get(treasuryId, fungibleTokenId).balance();
        assertThat(endTreasuryTokenBalance).isEqualTo(initialSenderTokenBalance + initialTreasuryTokenBalance);

        // No fees collected for token transfers
        final var endSenderBalance = writableAccountStore.get(ownerId).tinybarBalance();
        assertThat(endSenderBalance).isEqualTo(initialSenderBalance);
        final var feeCollectorBalance = writableAccountStore.get(feeCollectorId).tinybarBalance();
        assertThat(feeCollectorBalance).isEqualTo(initialFeeCollectorBalance);
    }

    @Test
    void handleExceedsMaxTokenTransfers() {
        config = defaultConfig().withValue(LEDGER_TOKEN_REJECTS_MAX_LEN, 1).getOrCreateConfig();
        final var txn = newTokenReject(ACCOUNT_3333, tokenRefFungible, tokenRefNFT);
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
    }

    @Test
    void handleRepeatedTokenReferences() {
        final var txn = newTokenReject(ACCOUNT_3333, tokenRefFungible, tokenRefFungible);

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(TOKEN_REFERENCE_REPEATED));
    }

    @Test
    void handleRejectsTransactionWithInvalidNftId() {
        final var invalidNftRef = TokenReference.newBuilder()
                .nft(NftID.newBuilder()
                        .tokenId(nonFungibleTokenId)
                        .serialNumber(0)
                        .build())
                .build();
        final var txn = newTokenReject(ACCOUNT_3333, invalidNftRef);

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TOKEN_NFT_SERIAL_NUMBER));
    }

    @Test
    void handleRejectsTransactionWithEmptyRejections() {
        final var txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(ACCOUNT_3333))
                .tokenReject(TokenRejectTransactionBody.newBuilder()
                        .owner(ACCOUNT_3333)
                        .build())
                .build();

        Assertions.assertThatThrownBy(() -> subject.pureChecks(txn))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
    }

    private HandleContext mockContext(final TransactionBody txn) {
        final var context = mock(HandleContext.class);
        given(context.configuration()).willReturn(config);
        given(context.body()).willReturn(txn);
        return context;
    }

    private static TestConfigBuilder defaultConfig() {
        return HederaTestConfigBuilder.create().withValue(LEDGER_TOKEN_REJECTS_MAX_LEN, 10);
    }

    private TransactionBody newTokenReject(final AccountID payerId, final TokenReference... tokenReferences) {
        return newTokenReject(Arrays.stream(tokenReferences).toList(), payerId);
    }

    private TransactionBody newTokenReject(final List<TokenReference> tokenReferences, final AccountID payerId) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp))
                .tokenReject(TokenRejectTransactionBody.newBuilder().rejections(tokenReferences))
                .build();
    }
}
