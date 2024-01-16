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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CURRENT_TREASURY_STILL_OWNS_NFTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TREASURY_ACCOUNT_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_KYC_KEY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NAME_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_SYMBOL_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ENTITIES_MAX_LIFETIME;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.test.utils.KeyUtils.B_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator;
import com.hedera.node.app.service.token.impl.validators.TokenUpdateValidator;
import com.hedera.node.app.service.token.records.TokenUpdateRecordBuilder;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator;
import com.hedera.node.app.workflows.handle.validation.StandardizedExpiryValidator;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUpdateHandlerTest extends CryptoTokenHandlerTestBase {
    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = LENIENT)
    private PropertySource compositeProps;

    @Mock(strictness = LENIENT)
    private HederaNumbers hederaNumbers;

    @Mock(strictness = LENIENT)
    private GlobalDynamicProperties dynamicProperties;

    @Mock(strictness = LENIENT)
    private TokenUpdateRecordBuilder recordBuilder;

    private TransactionBody txn;
    private ExpiryValidator expiryValidator;
    private AttributeValidator attributeValidator;
    private TokenUpdateHandler subject;
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L);

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();
        final TokenUpdateValidator validator = new TokenUpdateValidator(new TokenAttributesValidator());
        subject = new TokenUpdateHandler(validator);
        given(handleContext.recordBuilder(any())).willReturn(recordBuilder);
        givenStoresAndConfig(handleContext);
        setUpTxnContext();
    }

    @Test
    // Suppressing the warning that we have too many assertions
    @SuppressWarnings("java:S5961")
    void happyPathForFungibleTokenUpdate() {
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);

        final var token = readableTokenStore.get(fungibleTokenId);
        assertThat(token.symbol()).isEqualTo(fungibleToken.symbol());
        assertThat(token.name()).isEqualTo(fungibleToken.name());
        assertThat(token.treasuryAccountId()).isEqualTo(fungibleToken.treasuryAccountId());
        assertThat(token.adminKey()).isEqualTo(fungibleToken.adminKey());
        assertThat(token.supplyKey()).isEqualTo(fungibleToken.supplyKey());
        assertThat(token.kycKey()).isEqualTo(fungibleToken.kycKey());
        assertThat(token.freezeKey()).isEqualTo(fungibleToken.freezeKey());
        assertThat(token.wipeKey()).isEqualTo(fungibleToken.wipeKey());
        assertThat(token.feeScheduleKey()).isEqualTo(fungibleToken.feeScheduleKey());
        assertThat(token.pauseKey()).isEqualTo(fungibleToken.pauseKey());
        assertThat(token.autoRenewAccountId()).isEqualTo(fungibleToken.autoRenewAccountId());
        assertThat(token.expirationSecond()).isEqualTo(fungibleToken.expirationSecond());
        assertThat(token.memo()).isEqualTo(fungibleToken.memo());
        assertThat(token.autoRenewSeconds()).isEqualTo(fungibleToken.autoRenewSeconds());
        assertThat(token.tokenType()).isEqualTo(FUNGIBLE_COMMON);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        assertThat(modifiedToken.symbol()).isEqualTo("TTT");
        assertThat(modifiedToken.name()).isEqualTo("TestToken1");
        assertThat(modifiedToken.treasuryAccountId()).isEqualTo(ownerId);
        assertThat(modifiedToken.adminKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.supplyKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.kycKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.freezeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.wipeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.feeScheduleKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.pauseKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.autoRenewAccountId()).isEqualTo(ownerId);
        assertThat(modifiedToken.expirationSecond()).isEqualTo(1234600L);
        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(modifiedToken.autoRenewSeconds()).isEqualTo(fungibleToken.autoRenewSeconds());
        assertThat(token.tokenType()).isEqualTo(FUNGIBLE_COMMON);
    }

    @Test
    // Suppressing the warning that we have too many assertions
    @SuppressWarnings("java:S5961")
    void happyPathForNonFungibleTokenUpdate() {
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);

        final var token = readableTokenStore.get(nonFungibleTokenId);
        assertThat(token.symbol()).isEqualTo(nonFungibleToken.symbol());
        assertThat(token.name()).isEqualTo(nonFungibleToken.name());
        assertThat(token.treasuryAccountId()).isEqualTo(nonFungibleToken.treasuryAccountId());
        assertThat(token.adminKey()).isEqualTo(nonFungibleToken.adminKey());
        assertThat(token.supplyKey()).isEqualTo(nonFungibleToken.supplyKey());
        assertThat(token.kycKey()).isEqualTo(nonFungibleToken.kycKey());
        assertThat(token.freezeKey()).isEqualTo(nonFungibleToken.freezeKey());
        assertThat(token.wipeKey()).isEqualTo(nonFungibleToken.wipeKey());
        assertThat(token.feeScheduleKey()).isEqualTo(nonFungibleToken.feeScheduleKey());
        assertThat(token.pauseKey()).isEqualTo(nonFungibleToken.pauseKey());
        assertThat(token.autoRenewAccountId()).isEqualTo(nonFungibleToken.autoRenewAccountId());
        assertThat(token.expirationSecond()).isEqualTo(nonFungibleToken.expirationSecond());
        assertThat(token.memo()).isEqualTo(nonFungibleToken.memo());
        assertThat(token.autoRenewSeconds()).isEqualTo(nonFungibleToken.autoRenewSeconds());
        assertThat(token.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        assertThat(modifiedToken.symbol()).isEqualTo("TTT");
        assertThat(modifiedToken.name()).isEqualTo("TestToken1");
        assertThat(modifiedToken.treasuryAccountId()).isEqualTo(ownerId);
        assertThat(modifiedToken.adminKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.supplyKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.kycKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.freezeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.wipeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.feeScheduleKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.pauseKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.autoRenewAccountId()).isEqualTo(ownerId);
        assertThat(modifiedToken.expirationSecond()).isEqualTo(1234600L);
        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(modifiedToken.autoRenewSeconds()).isEqualTo(fungibleToken.autoRenewSeconds());
        assertThat(token.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);
    }

    @Test
    void invalidTokenFails() {
        txn = new TokenUpdateBuilder().withToken(asToken(1000)).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_ID));
    }

    @Test
    void failsIfTokenImmutable() {
        final var copyToken = writableTokenStore
                .get(fungibleTokenId)
                .copyBuilder()
                .adminKey((Key) null)
                .build();
        writableTokenStore.put(copyToken);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_IS_IMMUTABLE));
    }

    @Test
    void failsIfTokenHasNoKycGrantedImmutable() {
        final var copyTokenRel = writableTokenRelStore
                .get(treasuryId, fungibleTokenId)
                .copyBuilder()
                .kycGranted(false)
                .build();
        writableTokenRelStore.put(copyTokenRel);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_HAS_NO_KYC_KEY));
    }

    @Test
    void failsIfTokenRelIsFrozen() {
        final var copyTokenRel = writableTokenRelStore
                .get(treasuryId, fungibleTokenId)
                .copyBuilder()
                .frozen(true)
                .build();
        writableTokenRelStore.put(copyTokenRel);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_FROZEN_FOR_TOKEN));
    }

    @Test
    void failsIfMemoTooLong() {
        txn = new TokenUpdateBuilder()
                .withMemo("12345678904634634563436462343254534e5365453435452454524541534353665324545435")
                .build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MEMO_TOO_LONG));
    }

    @Test
    void failsIfMemoHasZeroByte() {
        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(100);
        txn = new TokenUpdateBuilder().withMemo("\0").build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ZERO_BYTE_IN_STRING));
    }

    @Test
    void doesntFailForZeroLengthSymbolUpdate() {
        txn = new TokenUpdateBuilder().withSymbol("").build();
        given(handleContext.body()).willReturn(txn);
        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void doesntFailForNullSymbol() {
        setUpTxnContext();
        txn = new TokenUpdateBuilder().withSymbol(null).build();
        given(handleContext.body()).willReturn(txn);
        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void failsForVeryLongSymbol() {
        setUpTxnContext();
        txn = new TokenUpdateBuilder()
                .withSymbol("1234567890123456789012345678901234567890123456789012345678901234567890")
                .build();
        configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.maxSymbolUtf8Bytes", "10")
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        given(handleContext.body()).willReturn(txn);

        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        Assertions.assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_SYMBOL_TOO_LONG));
    }

    @Test
    void doesntFailForZeroLengthName() {
        txn = new TokenUpdateBuilder().withName("").build();
        given(handleContext.body()).willReturn(txn);
        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void doesntFailForNullName() {
        txn = new TokenUpdateBuilder().withName(null).build();
        given(handleContext.body()).willReturn(txn);
        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void failsForVeryLongName() {
        txn = new TokenUpdateBuilder()
                .withName("1234567890123456789012345678901234567890123456789012345678901234567890")
                .build();
        configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.maxTokenNameUtf8Bytes", "10")
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        given(handleContext.body()).willReturn(txn);

        assertThatNoException().isThrownBy(() -> subject.pureChecks(txn));
        Assertions.assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NAME_TOO_LONG));
    }

    @Test
    // Suppressing the warning that we have too many assertions
    @SuppressWarnings("java:S5961")
    void worksWithUnassociatedNewTreasuryIfAutoAssociationsAvailable() {
        txn = new TokenUpdateBuilder()
                .withTreasury(payerId)
                .withToken(fungibleTokenId)
                .build();
        given(handleContext.body()).willReturn(txn);
        writableTokenRelStore.remove(TokenRelation.newBuilder()
                .tokenId(fungibleTokenId)
                .accountId(payerId)
                .build());
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        assertThat(writableTokenRelStore.get(payerId, fungibleTokenId)).isNull();

        final var token = readableTokenStore.get(fungibleTokenId);
        assertThat(token.symbol()).isEqualTo(fungibleToken.symbol());
        assertThat(token.name()).isEqualTo(fungibleToken.name());
        assertThat(token.treasuryAccountId()).isEqualTo(fungibleToken.treasuryAccountId());
        assertThat(token.adminKey()).isEqualTo(fungibleToken.adminKey());
        assertThat(token.supplyKey()).isEqualTo(fungibleToken.supplyKey());
        assertThat(token.kycKey()).isEqualTo(fungibleToken.kycKey());
        assertThat(token.freezeKey()).isEqualTo(fungibleToken.freezeKey());
        assertThat(token.wipeKey()).isEqualTo(fungibleToken.wipeKey());
        assertThat(token.feeScheduleKey()).isEqualTo(fungibleToken.feeScheduleKey());
        assertThat(token.pauseKey()).isEqualTo(fungibleToken.pauseKey());
        assertThat(token.autoRenewAccountId()).isEqualTo(fungibleToken.autoRenewAccountId());
        assertThat(token.expirationSecond()).isEqualTo(fungibleToken.expirationSecond());
        assertThat(token.memo()).isEqualTo(fungibleToken.memo());
        assertThat(token.autoRenewSeconds()).isEqualTo(fungibleToken.autoRenewSeconds());
        assertThat(token.tokenType()).isEqualTo(FUNGIBLE_COMMON);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var rel = writableTokenRelStore.get(payerId, fungibleTokenId);

        assertThat(rel).isNotNull();
        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        assertThat(modifiedToken.symbol()).isEqualTo("TTT");
        assertThat(modifiedToken.name()).isEqualTo("TestToken1");
        assertThat(modifiedToken.treasuryAccountId()).isEqualTo(payerId);
        assertThat(modifiedToken.adminKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.supplyKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.kycKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.freezeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.wipeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.feeScheduleKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.pauseKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.autoRenewAccountId()).isEqualTo(ownerId);
        assertThat(modifiedToken.expirationSecond()).isEqualTo(1234600L);
        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(modifiedToken.autoRenewSeconds()).isEqualTo(fungibleToken.autoRenewSeconds());
        assertThat(modifiedToken.tokenType()).isEqualTo(FUNGIBLE_COMMON);

        assertThat(rel.frozen()).isFalse();
        assertThat(rel.kycGranted()).isTrue();

        verify(recordBuilder)
                .addAutomaticTokenAssociation(TokenAssociation.newBuilder()
                        .tokenId(fungibleTokenId)
                        .accountId(payerId)
                        .build());
    }

    @Test
    // Suppressing the warning that we have too many assertions
    @SuppressWarnings("java:S5961")
    void worksWithUnassociatedNewTreasuryIfAutoAssociationsAvailableForNFT() {
        txn = new TokenUpdateBuilder()
                .withTreasury(payerId)
                .withToken(nonFungibleTokenId)
                .build();
        given(handleContext.body()).willReturn(txn);
        writableTokenRelStore.remove(TokenRelation.newBuilder()
                .tokenId(nonFungibleTokenId)
                .accountId(payerId)
                .build());
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        assertThat(writableTokenRelStore.get(payerId, nonFungibleTokenId)).isNull();

        final var token = readableTokenStore.get(nonFungibleTokenId);
        assertThat(token.symbol()).isEqualTo(nonFungibleToken.symbol());
        assertThat(token.name()).isEqualTo(nonFungibleToken.name());
        assertThat(token.treasuryAccountId()).isEqualTo(nonFungibleToken.treasuryAccountId());
        assertThat(token.adminKey()).isEqualTo(nonFungibleToken.adminKey());
        assertThat(token.supplyKey()).isEqualTo(nonFungibleToken.supplyKey());
        assertThat(token.kycKey()).isEqualTo(nonFungibleToken.kycKey());
        assertThat(token.freezeKey()).isEqualTo(nonFungibleToken.freezeKey());
        assertThat(token.wipeKey()).isEqualTo(nonFungibleToken.wipeKey());
        assertThat(token.feeScheduleKey()).isEqualTo(nonFungibleToken.feeScheduleKey());
        assertThat(token.pauseKey()).isEqualTo(nonFungibleToken.pauseKey());
        assertThat(token.autoRenewAccountId()).isEqualTo(nonFungibleToken.autoRenewAccountId());
        assertThat(token.expirationSecond()).isEqualTo(nonFungibleToken.expirationSecond());
        assertThat(token.memo()).isEqualTo(nonFungibleToken.memo());
        assertThat(token.autoRenewSeconds()).isEqualTo(nonFungibleToken.autoRenewSeconds());
        assertThat(token.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);

        final var newTreasury = writableAccountStore.get(payerId);
        final var oldTreasury = writableAccountStore.get(treasuryId);
        assertThat(newTreasury.numberOwnedNfts()).isEqualTo(2);
        assertThat(oldTreasury.numberOwnedNfts()).isEqualTo(2);

        final var newTreasuryRel = writableTokenRelStore.get(payerId, nonFungibleTokenId);
        final var oldTreasuryRel = writableTokenRelStore.get(treasuryId, nonFungibleTokenId);

        assertThat(newTreasuryRel).isNull();
        assertThat(oldTreasuryRel.balance()).isEqualTo(1);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var rel = writableTokenRelStore.get(payerId, nonFungibleTokenId);

        assertThat(rel).isNotNull();
        final var modifiedToken = writableTokenStore.get(nonFungibleTokenId);
        assertThat(modifiedToken.symbol()).isEqualTo("TTT");
        assertThat(modifiedToken.name()).isEqualTo("TestToken1");
        assertThat(modifiedToken.treasuryAccountId()).isEqualTo(payerId);
        assertThat(rel.balance()).isEqualTo(1);
        assertThat(modifiedToken.adminKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.supplyKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.kycKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.freezeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.wipeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.feeScheduleKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.pauseKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.autoRenewAccountId()).isEqualTo(ownerId);
        assertThat(modifiedToken.expirationSecond()).isEqualTo(1234600L);
        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(modifiedToken.autoRenewSeconds()).isEqualTo(fungibleToken.autoRenewSeconds());
        assertThat(modifiedToken.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);

        assertThat(rel.frozen()).isFalse();
        assertThat(rel.kycGranted()).isTrue();

        final var modifiedNewTreasury = writableAccountStore.get(payerId);
        final var modifiedOldTreasury = writableAccountStore.get(treasuryId);
        assertThat(modifiedNewTreasury.numberOwnedNfts()).isEqualTo(3);
        assertThat(modifiedOldTreasury.numberOwnedNfts()).isEqualTo(1);

        verify(recordBuilder)
                .addAutomaticTokenAssociation(TokenAssociation.newBuilder()
                        .tokenId(nonFungibleTokenId)
                        .accountId(payerId)
                        .build());
    }

    @Test
    void failsIfNoAutoAssociationsAvailableForNewUnassociatedTreasury() {
        txn = new TokenUpdateBuilder()
                .withTreasury(payerId)
                .withToken(fungibleTokenId)
                .build();
        given(handleContext.body()).willReturn(txn);
        writableTokenRelStore.remove(TokenRelation.newBuilder()
                .tokenId(fungibleTokenId)
                .accountId(payerId)
                .build());
        writableAccountStore.put(account.copyBuilder()
                .maxAutoAssociations(0)
                .usedAutoAssociations(0)
                .build());
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(writableAccountStore);
        assertThat(writableTokenRelStore.get(payerId, fungibleTokenId)).isNull();

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NO_REMAINING_AUTOMATIC_ASSOCIATIONS));
    }

    @Test
    void failsOnInvalidNewTreasury() {
        txn = new TokenUpdateBuilder().withTreasury(asAccount(2000000)).build();
        given(handleContext.body()).willReturn(txn);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TREASURY_ACCOUNT_FOR_TOKEN));
    }

    @Test
    void failsOnDetachedNewTreasury() {
        txn = new TokenUpdateBuilder().withTreasury(payerId).build();
        writableAccountStore.put(account.copyBuilder()
                .expiredAndPendingRemoval(true)
                .tinybarBalance(0)
                .expirationSecond(consensusInstant.getEpochSecond() - 10000)
                .build());
        given(handleContext.body()).willReturn(txn);
        writableTokenRelStore.remove(TokenRelation.newBuilder()
                .tokenId(fungibleTokenId)
                .accountId(payerId)
                .build());
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(writableAccountStore);
        final var config = HederaTestConfigBuilder.create()
                .withValue("autoRenew.targetTypes", "CONTRACT,ACCOUNT")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 1));

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
    }

    @Test
    void failsOnDetachedOldTreasury() {
        txn = new TokenUpdateBuilder().build();
        writableAccountStore.put(treasuryAccount
                .copyBuilder()
                .expiredAndPendingRemoval(true)
                .tinybarBalance(0)
                .expirationSecond(consensusInstant.getEpochSecond() - 10000)
                .build());
        given(handleContext.body()).willReturn(txn);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(writableAccountStore);
        final var config = HederaTestConfigBuilder.create()
                .withValue("autoRenew.targetTypes", "CONTRACT,ACCOUNT")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 1));

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
    }

    @Test
    void failsOnDetachedNewAutoRenewAccount() {
        txn = new TokenUpdateBuilder().withAutoRenewAccount(payerId).build();
        writableAccountStore.put(account.copyBuilder()
                .expiredAndPendingRemoval(true)
                .tinybarBalance(0)
                .expirationSecond(consensusInstant.getEpochSecond() - 10000)
                .build());
        given(handleContext.body()).willReturn(txn);
        writableTokenRelStore.remove(TokenRelation.newBuilder()
                .tokenId(fungibleTokenId)
                .accountId(payerId)
                .build());
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(writableAccountStore);
        final var config = HederaTestConfigBuilder.create()
                .withValue("autoRenew.targetTypes", "CONTRACT,ACCOUNT")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 1));

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
    }

    @Test
    void failsOnDetachedOldAutoRenewAccount() {
        txn = new TokenUpdateBuilder().build();
        writableAccountStore.put(spenderAccount
                .copyBuilder()
                .expiredAndPendingRemoval(true)
                .tinybarBalance(0)
                .expirationSecond(consensusInstant.getEpochSecond() - 10000)
                .build());
        given(handleContext.body()).willReturn(txn);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(writableAccountStore);
        final var config = HederaTestConfigBuilder.create()
                .withValue("autoRenew.targetTypes", "CONTRACT,ACCOUNT")
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(config, 1));

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL));
    }

    @Test
    void failsOnDeletedOldAutoRenewAccount() {
        txn = new TokenUpdateBuilder().build();
        writableAccountStore.put(spenderAccount.copyBuilder().deleted(true).build());
        given(handleContext.body()).willReturn(txn);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(writableAccountStore);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_DELETED));
    }

    @Test
    void failsOnDeletedNewAutoRenewAccount() {
        txn = new TokenUpdateBuilder().withAutoRenewAccount(payerId).build();
        writableAccountStore.put(account.copyBuilder().deleted(true).build());
        given(handleContext.body()).willReturn(txn);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(writableAccountStore);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ACCOUNT_DELETED));
    }

    @Test
    void permitsExtendingOnlyExpiryWithoutAdminKey() {
        final var transactionID =
                TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp);
        final var body = TokenUpdateTransactionBody.newBuilder()
                .token(fungibleTokenId)
                .expiry(Timestamp.newBuilder().seconds(1234600L).build())
                .build();
        txn = TransactionBody.newBuilder()
                .transactionID(transactionID)
                .tokenUpdate(body)
                .build();

        given(handleContext.body()).willReturn(txn);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void failsOnReducedNewExpiry() {
        txn = new TokenUpdateBuilder()
                .withExpiry(consensusInstant.getEpochSecond() - 72000)
                .build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(EXPIRATION_REDUCTION_NOT_ALLOWED));
    }

    @Test
    void failsOnInvalidNewExpiry() {
        given(compositeProps.getLongProperty(ENTITIES_MAX_LIFETIME)).willReturn(3_000_000_000L);
        txn = new TokenUpdateBuilder().withExpiry(3_000_000_000L + 10).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_EXPIRATION_TIME));
    }

    @Test
    void failsOnAlreadyDeletedToken() {
        final var copyToken = writableTokenStore
                .get(fungibleTokenId)
                .copyBuilder()
                .deleted(true)
                .build();
        writableTokenStore.put(copyToken);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_WAS_DELETED));
    }

    @Test
    void failsOnPausedToken() {
        final var copyToken = writableTokenStore
                .get(fungibleTokenId)
                .copyBuilder()
                .paused(true)
                .build();
        writableTokenStore.put(copyToken);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);
        txn = new TokenUpdateBuilder().build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_IS_PAUSED));
    }

    @Test
    void doesntReplaceIdenticalTreasury() {
        final var transactionID =
                TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp);
        final var body = TokenUpdateTransactionBody.newBuilder()
                .token(fungibleTokenId)
                .treasury(treasuryId)
                .build();
        txn = TransactionBody.newBuilder()
                .transactionID(transactionID)
                .tokenUpdate(body)
                .build();
        given(handleContext.body()).willReturn(txn);

        final var oldRel = writableTokenRelStore.get(treasuryId, fungibleTokenId);
        assertThat(oldRel).isNotNull();
        final var oldToken = writableTokenStore.get(fungibleTokenId);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var newRel = writableTokenRelStore.get(treasuryId, fungibleTokenId);
        assertThat(newRel).isNotNull();

        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        assertThat(oldToken).isEqualTo(modifiedToken);
        assertThat(oldRel).isEqualTo(newRel);
    }

    @Test
    void followsHappyPathWithNewTreasuryAndZeroBalanceOldTreasury() {
        txn = new TokenUpdateBuilder()
                .withTreasury(payerId)
                .withToken(fungibleTokenId)
                .build();
        given(handleContext.body()).willReturn(txn);
        writableTokenRelStore.remove(TokenRelation.newBuilder()
                .tokenId(fungibleTokenId)
                .accountId(payerId)
                .build());
        writableAccountStore.put(account.copyBuilder().numberPositiveBalances(0).build());
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.readableStore(ReadableAccountStore.class)).willReturn(writableAccountStore);
        assertThat(writableTokenRelStore.get(payerId, fungibleTokenId)).isNull();

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var rel = writableTokenRelStore.get(payerId, fungibleTokenId);

        assertThat(rel).isNotNull();
        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        assertThat(modifiedToken.symbol()).isEqualTo("TTT");
        assertThat(modifiedToken.name()).isEqualTo("TestToken1");
        assertThat(modifiedToken.treasuryAccountId()).isEqualTo(payerId);
        assertThat(modifiedToken.adminKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.supplyKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.wipeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.feeScheduleKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.pauseKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.autoRenewAccountId()).isEqualTo(ownerId);
        assertThat(modifiedToken.expirationSecond()).isEqualTo(1234600L);
        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(modifiedToken.autoRenewSeconds()).isEqualTo(fungibleToken.autoRenewSeconds());
        assertThat(modifiedToken.tokenType()).isEqualTo(FUNGIBLE_COMMON);

        verify(recordBuilder)
                .addAutomaticTokenAssociation(TokenAssociation.newBuilder()
                        .tokenId(fungibleTokenId)
                        .accountId(payerId)
                        .build());
    }

    @Test
    void doesntGrantKycOrUnfreezeNewTreasuryIfNoKeyIsPresent() {
        txn = new TokenUpdateBuilder()
                .withTreasury(payerId)
                .withToken(fungibleTokenId)
                .withKycKey(null)
                .wthFreezeKey(null)
                .build();
        given(handleContext.body()).willReturn(txn);
        writableTokenRelStore.remove(TokenRelation.newBuilder()
                .tokenId(fungibleTokenId)
                .accountId(payerId)
                .build());
        given(handleContext.writableStore(WritableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        assertThat(writableTokenRelStore.get(payerId, fungibleTokenId)).isNull();

        writableTokenStore.put(fungibleToken
                .copyBuilder()
                .kycKey((Key) null)
                .freezeKey((Key) null)
                .build());
        given(handleContext.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(handleContext.readableStore(ReadableTokenStore.class)).willReturn(writableTokenStore);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var rel = writableTokenRelStore.get(payerId, fungibleTokenId);

        assertThat(rel).isNotNull();
        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        assertThat(modifiedToken.symbol()).isEqualTo("TTT");
        assertThat(modifiedToken.name()).isEqualTo("TestToken1");
        assertThat(modifiedToken.treasuryAccountId()).isEqualTo(payerId);
        assertThat(modifiedToken.adminKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.supplyKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.wipeKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.feeScheduleKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.pauseKey()).isEqualTo(B_COMPLEX_KEY);
        assertThat(modifiedToken.autoRenewAccountId()).isEqualTo(ownerId);
        assertThat(modifiedToken.expirationSecond()).isEqualTo(1234600L);
        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(modifiedToken.autoRenewSeconds()).isEqualTo(fungibleToken.autoRenewSeconds());
        assertThat(modifiedToken.tokenType()).isEqualTo(FUNGIBLE_COMMON);

        assertThat(rel.frozen()).isFalse();
        assertThat(rel.kycGranted()).isTrue();

        verify(recordBuilder)
                .addAutomaticTokenAssociation(TokenAssociation.newBuilder()
                        .tokenId(fungibleTokenId)
                        .accountId(payerId)
                        .build());
    }

    @Test
    void validatesUpdatingKeys() {
        txn = new TokenUpdateBuilder().withAdminKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ADMIN_KEY));

        txn = new TokenUpdateBuilder().withSupplyKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_SUPPLY_KEY));

        txn = new TokenUpdateBuilder().withWipeKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_WIPE_KEY));

        txn = new TokenUpdateBuilder().withFeeScheduleKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_CUSTOM_FEE_SCHEDULE_KEY));

        txn = new TokenUpdateBuilder().withPauseKey(Key.DEFAULT).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_PAUSE_KEY));
    }

    @Test
    void rejectsTreasuryUpdateIfNonzeroBalanceForNFTs() {
        final var copyTokenRel = writableTokenRelStore
                .get(treasuryId, nonFungibleTokenId)
                .copyBuilder()
                .balance(1)
                .build();
        configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.nfts.useTreasuryWildcards", "false")
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);
        writableTokenRelStore.put(copyTokenRel);
        given(handleContext.readableStore(ReadableTokenRelationStore.class)).willReturn(writableTokenRelStore);
        txn = new TokenUpdateBuilder().withToken(nonFungibleTokenId).build();
        given(handleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(CURRENT_TREASURY_STILL_OWNS_NFTS));
    }

    /* --------------------------------- Helpers --------------------------------- */
    /**
     * A builder for {@link com.hedera.hapi.node.transaction.TransactionBody} instances.
     */
    private class TokenUpdateBuilder {
        private AccountID payer = payerId;
        private AccountID treasury = ownerId;
        private Key adminKey = B_COMPLEX_KEY;
        private String name = "TestToken1";
        private String symbol = "TTT";
        private Key kycKey = B_COMPLEX_KEY;
        private Key freezeKey = B_COMPLEX_KEY;
        private Key wipeKey = B_COMPLEX_KEY;
        private Key supplyKey = B_COMPLEX_KEY;
        private Key feeScheduleKey = B_COMPLEX_KEY;
        private Key pauseKey = B_COMPLEX_KEY;
        private Timestamp expiry = Timestamp.newBuilder().seconds(1234600L).build();
        private AccountID autoRenewAccount = ownerId;
        private long autoRenewPeriod = autoRenewSecs;
        private String memo = "test token1";
        TokenID tokenId = fungibleTokenId;

        private TokenUpdateBuilder() {}

        public TransactionBody build() {
            final var transactionID =
                    TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
            final var createTxnBody = TokenUpdateTransactionBody.newBuilder()
                    .token(tokenId)
                    .symbol(symbol)
                    .name(name)
                    .treasury(treasury)
                    .adminKey(adminKey)
                    .supplyKey(supplyKey)
                    .kycKey(kycKey)
                    .freezeKey(freezeKey)
                    .wipeKey(wipeKey)
                    .feeScheduleKey(feeScheduleKey)
                    .pauseKey(pauseKey)
                    .autoRenewAccount(autoRenewAccount)
                    .expiry(expiry)
                    .memo(memo);
            if (autoRenewPeriod > 0) {
                createTxnBody.autoRenewPeriod(
                        Duration.newBuilder().seconds(autoRenewPeriod).build());
            }
            return TransactionBody.newBuilder()
                    .transactionID(transactionID)
                    .tokenUpdate(createTxnBody.build())
                    .build();
        }

        public TokenUpdateBuilder withToken(TokenID tokenId) {
            this.tokenId = tokenId;
            return this;
        }

        public TokenUpdateBuilder withFreezeKey(Key freezeKey) {
            this.freezeKey = freezeKey;
            return this;
        }

        public TokenUpdateBuilder withAutoRenewAccount(AccountID autoRenewAccount) {
            this.autoRenewAccount = autoRenewAccount;
            return this;
        }

        public TokenUpdateBuilder withSymbol(final String symbol) {
            this.symbol = symbol;
            return this;
        }

        public TokenUpdateBuilder withName(final String name) {
            this.name = name;
            return this;
        }

        public TokenUpdateBuilder withTreasury(final AccountID treasury) {
            this.treasury = treasury;
            return this;
        }

        public TokenUpdateBuilder withFeeScheduleKey(final Key key) {
            this.feeScheduleKey = key;
            return this;
        }

        public TokenUpdateBuilder withAdminKey(final Key key) {
            this.adminKey = key;
            return this;
        }

        public TokenUpdateBuilder withSupplyKey(final Key key) {
            this.supplyKey = key;
            return this;
        }

        public TokenUpdateBuilder withKycKey(final Key key) {
            this.kycKey = key;
            return this;
        }

        public TokenUpdateBuilder withWipeKey(final Key key) {
            this.wipeKey = key;
            return this;
        }

        public TokenUpdateBuilder withExpiry(final long expiry) {
            this.expiry = Timestamp.newBuilder().seconds(expiry).build();
            return this;
        }

        public TokenUpdateBuilder withAutoRenewPeriod(final long autoRenewPeriod) {
            this.autoRenewPeriod = autoRenewPeriod;
            return this;
        }

        public TokenUpdateBuilder withMemo(final String s) {
            this.memo = s;
            return this;
        }

        public TokenUpdateBuilder withPauseKey(final Key key) {
            this.pauseKey = key;
            return this;
        }

        public TokenUpdateBuilder wthFreezeKey(final Key key) {
            this.freezeKey = key;
            return this;
        }
    }

    private void setUpTxnContext() {
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(compositeProps.getLongProperty("entities.maxLifetime")).willReturn(7200000L);

        attributeValidator =
                new StandardizedAttributeValidator(consensusInstant::getEpochSecond, compositeProps, dynamicProperties);
        expiryValidator = new StandardizedExpiryValidator(
                id -> {
                    final var account = writableAccountStore.get(
                            AccountID.newBuilder().accountNum(id.num()).build());
                    validateTrue(account != null, INVALID_AUTORENEW_ACCOUNT);
                },
                attributeValidator,
                consensusInstant::getEpochSecond,
                hederaNumbers,
                configProvider);

        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(50);
        given(dynamicProperties.maxAutoRenewDuration()).willReturn(3000000L);
        given(dynamicProperties.minAutoRenewDuration()).willReturn(10L);
        given(configProvider.getConfiguration()).willReturn(versionedConfig);
    }
}
