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

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEES_LIST_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_FEE_SCHEDULE_KEY;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenFeeScheduleUpdateHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.CustomFeesValidator;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenFeeScheduleUpdateHandlerTest extends CryptoTokenHandlerTestBase {
    private TokenFeeScheduleUpdateHandler subject;
    private CustomFeesValidator validator;
    private TransactionBody txn;

    @Mock(strictness = LENIENT)
    private HandleContext context;

    @Mock
    private PreHandleContext preHandleContext;

    @BeforeEach
    void setup() {
        super.setUp();
        refreshWritableStores();
        validator = new CustomFeesValidator();
        subject = new TokenFeeScheduleUpdateHandler(validator);
        givenTxn();
        final var config = new HederaTestConfigBuilder()
                .withValue("tokens.maxCustomFeesAllowed", 1000)
                .getOrCreateConfig();
        given(context.configuration()).willReturn(config);
        given(context.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);
        given(context.readableStore(ReadableTokenRelationStore.class)).willReturn(readableTokenRelStore);
        given(context.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
    }

    @Test
    @DisplayName("fee schedule update works as expected for fungible token")
    void handleWorksAsExpectedForFungibleToken() {
        // before fee schedule update, validate no custom fees on the token
        final var originalToken = writableTokenStore.get(fungibleTokenId);
        assertThat(originalToken.customFees()).isEmpty();
        assertThat(writableTokenStore.modifiedTokens()).isEmpty();

        subject.handle(context);

        // validate after fee schedule update fixed and fractional custom fees are added to the token
        assertThat(writableTokenStore.modifiedTokens()).hasSize(1);
        assertThat(writableTokenStore.modifiedTokens()).hasSameElementsAs(Set.of(fungibleTokenNum));

        final var expectedToken = writableTokenStore.get(fungibleTokenId);
        assertThat(expectedToken.customFees()).hasSize(2);
        assertThat(expectedToken.customFees())
                .hasSameElementsAs(List.of(withFractionalFee(fractionalFee), withFixedFee(fixedFee)));
    }

    @Test
    @DisplayName("fee schedule update works as expected for non-fungible token")
    void handleWorksAsExpectedForNonFungibleToken() {
        final var tokenId =
                TokenID.newBuilder().tokenNum(nonFungibleTokenNum.longValue()).build();
        txn = TransactionBody.newBuilder()
                .tokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder()
                        .tokenId(tokenId)
                        .customFees(List.of(withRoyaltyFee(royaltyFee)))
                        .build())
                .build();
        given(context.body()).willReturn(txn);

        // before fee schedule update, validate no custom fees on the token
        final var originalToken = writableTokenStore.get(tokenId);
        assertThat(originalToken.customFees()).isEmpty();
        assertThat(writableTokenStore.modifiedTokens()).isEmpty();

        subject.handle(context);

        // validate after fee schedule update royalty custom fees are added to the token
        assertThat(writableTokenStore.modifiedTokens()).hasSize(1);
        assertThat(writableTokenStore.modifiedTokens()).hasSameElementsAs(Set.of(nonFungibleTokenNum));

        final var expectedToken = writableTokenStore.get(nonFungibleTokenId);
        assertThat(expectedToken.customFees()).hasSize(1);
        assertThat(expectedToken.customFees()).hasSameElementsAs(List.of(withRoyaltyFee(royaltyFee)));
    }

    @Test
    @DisplayName("fee schedule update fails if token has no fee schedule key")
    void validatesTokenHasFeeScheduleKey() {
        final var tokenWithoutFeeScheduleKey =
                fungibleToken.copyBuilder().feeScheduleKey((Key) null).build();
        writableTokenState = MapWritableKVState.<EntityNum, Token>builder(TOKENS)
                .value(fungibleTokenNum, tokenWithoutFeeScheduleKey)
                .build();
        given(writableStates.<EntityNum, Token>get(TOKENS)).willReturn(writableTokenState);
        writableTokenStore = new WritableTokenStore(writableStates);
        given(context.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);

        assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_HAS_NO_FEE_SCHEDULE_KEY));
    }

    @Test
    @DisplayName("fee schedule update fails if token does not exist")
    void rejectsInvalidTokenId() {
        writableTokenState = emptyWritableTokenState();
        given(writableStates.<EntityNum, Token>get(TOKENS)).willReturn(writableTokenState);
        writableTokenStore = new WritableTokenStore(writableStates);
        given(context.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);

        assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_ID));
    }

    @Test
    @DisplayName("fee schedule update fails if custom fees list is too long")
    void failsIfTooManyCustomFees() {
        final var config = new HederaTestConfigBuilder()
                .withValue("tokens.maxCustomFeesAllowed", 1)
                .getOrCreateConfig();
        given(context.configuration()).willReturn(config);
        assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(CUSTOM_FEES_LIST_TOO_LONG));
    }

    @Test
    @DisplayName("fee schedule update fails in pre-handle if transaction has no tokenId specified")
    void failsIfTxnHasNoTokenId() {
        txn = TransactionBody.newBuilder()
                .tokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder()
                        .customFees(customFees)
                        .build())
                .build();
        given(preHandleContext.body()).willReturn(txn);
        assertThatThrownBy(() -> subject.preHandle(preHandleContext))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TOKEN_ID));
    }

    private void givenTxn() {
        txn = TransactionBody.newBuilder()
                .tokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder()
                        .tokenId(TokenID.newBuilder()
                                .tokenNum(fungibleTokenNum.longValue())
                                .build())
                        .customFees(customFees)
                        .build())
                .build();
        given(context.body()).willReturn(txn);
    }
}
