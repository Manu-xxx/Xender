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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.TokenSupplyChangeOpsValidator;
import com.hedera.node.app.service.token.records.TokenMintRecordBuilder;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class TokenMintHandlerTest extends CryptoTokenHandlerTestBase {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    private final Bytes metadata1 = Bytes.wrap("memo".getBytes());
    private final Bytes metadata2 = Bytes.wrap("memo2".getBytes());
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L);
    private SingleTransactionRecordBuilderImpl recordBuilder;
    private TokenMintHandler subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();
        givenStoresAndConfig(handleContext);
        subject = new TokenMintHandler(new TokenSupplyChangeOpsValidator());
        recordBuilder = new SingleTransactionRecordBuilderImpl(consensusNow);
    }

    @Test
    void rejectsNftMintsWhenNftsNotEnabled() {
        givenMintTxn(nonFungibleTokenId, List.of(metadata1, metadata2), null);
        configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.nfts.areEnabled", false)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NOT_SUPPORTED));
    }

    @Test
    void acceptsValidFungibleTokenMintTxn() {
        givenMintTxn(fungibleTokenId, null, 10L);

        assertThat(writableTokenRelStore.get(treasuryId, fungibleTokenId).balance())
                .isEqualTo(1000L);
        assertThat(writableAccountStore.get(treasuryId).tinybarBalance()).isEqualTo(10000L);
        assertThat(writableAccountStore.get(treasuryId).numberPositiveBalances())
                .isEqualTo(2);
        assertThat(writableTokenStore.get(fungibleTokenId).totalSupply()).isEqualTo(1000L);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        // treasury relation balance will increase
        assertThat(writableTokenRelStore.get(treasuryId, fungibleTokenId).balance())
                .isEqualTo(1010L);
        // tinybar balance should not get affected
        assertThat(writableAccountStore.get(treasuryId).tinybarBalance()).isEqualTo(10000L);

        // since there are positive balances for this token relation already, it will not be increased.
        assertThat(writableAccountStore.get(treasuryId).numberPositiveBalances())
                .isEqualTo(2);
        // supply of fungible token increases
        assertThat(writableTokenStore.get(fungibleTokenId).totalSupply()).isEqualTo(1010L);
    }

    @Test
    void acceptsValidNonFungibleTokenMintTxn() {
        givenMintTxn(nonFungibleTokenId, List.of(metadata1, metadata2), null);

        assertThat(writableTokenRelStore.get(treasuryId, nonFungibleTokenId).balance())
                .isEqualTo(1L);
        assertThat(writableAccountStore.get(treasuryId).tinybarBalance()).isEqualTo(10000L);
        assertThat(writableAccountStore.get(treasuryId).numberOwnedNfts()).isEqualTo(2);
        assertThat(writableTokenStore.get(nonFungibleTokenId).totalSupply()).isEqualTo(1000L);
        assertThat(recordBuilder.build().transactionRecord().receipt().serialNumbers())
                .isEmpty();

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        // treasury relation balance will increase by metadata list size
        assertThat(writableTokenRelStore.get(treasuryId, nonFungibleTokenId).balance())
                .isEqualTo(3L);
        // tinybar balance should not get affected
        assertThat(writableAccountStore.get(treasuryId).tinybarBalance()).isEqualTo(10000L);

        // number of owned NFTs should increase
        assertThat(writableAccountStore.get(treasuryId).numberOwnedNfts()).isEqualTo(4);
        // token total supply should be increased
        assertThat(writableTokenStore.get(nonFungibleTokenId).totalSupply()).isEqualTo(1002L);
        assertThat(recordBuilder.build().transactionRecord().receipt().serialNumbers())
                .isEqualTo(List.of(3L, 4L));
    }

    @Test
    void failsOnMissingToken() {
        givenMintTxn(TokenID.newBuilder().tokenNum(100000L).build(), List.of(metadata1, metadata2), null);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_ID));
    }

    @Test
    void failsOnDeletedToken() {
        final var deletedToken = givenValidFungibleToken(payerId, true, false, false, false, true);
        writableTokenStore.put(deletedToken);
        givenMintTxn(fungibleTokenId, List.of(metadata1, metadata2), null);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_WAS_DELETED));
    }

    @Test
    void failsOnPausedToken() {
        final var pausedToken = givenValidFungibleToken(payerId, false, true, false, false, true);
        writableTokenStore.put(pausedToken);
        givenMintTxn(fungibleTokenId, List.of(metadata1, metadata2), null);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_IS_PAUSED));
    }

    @Test
    void failsOnNegativeAmount() {
        givenMintTxn(fungibleTokenId, null, -2L);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_MINT_AMOUNT));
    }

    @Test
    void allowsZeroAmount() {
        givenMintTxn(fungibleTokenId, null, 0L);
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void rejectsBothAMountAndMetadataFields() throws PreCheckException {
        final var txn = givenMintTxn(fungibleTokenId, List.of(metadata1), 2L);
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        assertThatThrownBy(() -> subject.preHandle(context))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
    }

    @Test
    void allowsTxnBodyWithNoProps() throws PreCheckException {
        final var txn = givenMintTxn(fungibleTokenId, null, null);
        refreshReadableStores();
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        context.registerStore(ReadableTokenStore.class, readableTokenStore);

        assertThatNoException().isThrownBy(() -> subject.preHandle(context));
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void propagatesErrorOnBadMetadata() {
        givenMintTxn(nonFungibleTokenId, List.of(Bytes.wrap("test".getBytes())), null);

        configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.nfts.maxMetadataBytes", 1)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(METADATA_TOO_LONG));
    }

    @Test
    void propagatesErrorOnMaxBatchSizeReached() {
        givenMintTxn(nonFungibleTokenId, List.of(metadata1, metadata2), null);

        configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.nfts.maxBatchSizeMint", 1)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(BATCH_SIZE_LIMIT_EXCEEDED));
    }

    @Test
    void validatesMintingResourcesLimit() {
        givenMintTxn(nonFungibleTokenId, List.of(Bytes.wrap("test".getBytes()), Bytes.wrap("test1".getBytes())), null);

        configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.nfts.maxAllowedMints", 1)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED));
    }

    private TransactionBody givenMintTxn(final TokenID tokenId, final List<Bytes> metadata, final Long amount) {
        final var transactionID =
                TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp);
        final var builder = TokenMintTransactionBody.newBuilder().token(tokenId);
        if (metadata != null) {
            builder.metadata(metadata);
        }
        if (amount != null) {
            builder.amount(amount);
        }
        final var txnBody = TransactionBody.newBuilder()
                .transactionID(transactionID)
                .tokenMint(builder.build())
                .build();

        final var txn = Transaction.newBuilder().body(txnBody).build();
        recordBuilder.transaction(txn);

        given(handleContext.body()).willReturn(txnBody);
        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.recordBuilder(TokenMintRecordBuilder.class)).willReturn(recordBuilder);

        return txnBody;
    }
}
