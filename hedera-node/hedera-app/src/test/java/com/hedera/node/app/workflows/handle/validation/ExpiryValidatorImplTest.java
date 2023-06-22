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

package com.hedera.node.app.workflows.handle.validation;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EXPIRATION_REDUCTION_NOT_ALLOWED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.EntityType;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiryValidatorImplTest {
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567L);
    private static final long A_TIME = 666_666_666L;
    private static final long B_TIME = 777_777_777L;
    private static final long A_PERIOD = 666_666L;
    private static final long B_PERIOD = 777_777L;
    private static final long AN_AUTO_RENEW_NUM = 888;

    @Mock
    private AttributeValidator attributeValidator;
    @Mock(strictness = LENIENT)
    private ReadableAccountStore accountStore;

    @Mock(strictness = LENIENT)
    private HandleContext context;

    private ExpiryValidatorImpl subject;

    @BeforeEach
    void setUp() {
        given(context.consensusNow()).willReturn(NOW);
        final var config = new HederaTestConfigBuilder().getOrCreateConfig();
        given(context.configuration()).willReturn(config);
        given(context.attributeValidator()).willReturn(attributeValidator);
        given(accountStore.getAccountById(any())).willReturn(Account.DEFAULT);
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);

        subject = new ExpiryValidatorImpl(context);
    }

    @Test
    void onCreationRequiresEitherExplicitValueOrFullAutoRenewMetaIfNotSelfFunding() {
        willThrow(new HandleException(INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(anyLong());
        final var expiryMeta1 = new ExpiryMeta(NA, NA, AN_AUTO_RENEW_NUM);
        assertThatThrownBy(() -> subject.resolveCreationAttempt(false, expiryMeta1))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_EXPIRATION_TIME));
        final var expiryMeta2 = new ExpiryMeta(NA, A_PERIOD, NA);
        assertThatThrownBy(() -> subject.resolveCreationAttempt(false, expiryMeta2))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_EXPIRATION_TIME));
    }

    @Test
    void validatesShard() {
        final var config = new HederaTestConfigBuilder()
                .withValue("hedera.shard", 1L)
                .getOrCreateConfig();
        given(context.configuration()).willReturn(config);
        final var newMeta = new ExpiryMeta(A_TIME, A_PERIOD, 2L, 2L, AN_AUTO_RENEW_NUM);

        assertThatThrownBy(() -> subject.resolveCreationAttempt(false, newMeta))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_AUTORENEW_ACCOUNT));
    }

    @Test
    void validatesRealm() {
        final var config = new HederaTestConfigBuilder()
                .withValue("hedera.shard", 1L)
                .withValue("hedera.realm", 2L)
                .getOrCreateConfig();
        given(context.configuration()).willReturn(config);
        final var newMeta = new ExpiryMeta(A_TIME, A_PERIOD, 1L, 3L, AN_AUTO_RENEW_NUM);

        assertThatThrownBy(() -> subject.resolveCreationAttempt(false, newMeta))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_AUTORENEW_ACCOUNT));
    }

    @Test
    void onCreationRequiresValidExpiryIfExplicit() {
        willThrow(new HandleException(INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(A_TIME);

        final var expiryMeta = new ExpiryMeta(A_TIME, NA, AN_AUTO_RENEW_NUM);
        assertThatThrownBy(() -> subject.resolveCreationAttempt(false, expiryMeta))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_EXPIRATION_TIME));
    }

    @Test
    void translatesFailureOnExplicitAutoRenewAccount() {
        given(accountStore.getAccountById(AccountID.newBuilder().accountNum(AN_AUTO_RENEW_NUM).build()))
                .willThrow(new InvalidTransactionException(
                        com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT));

        final var expiryMeta = new ExpiryMeta(A_TIME, NA, AN_AUTO_RENEW_NUM);
        assertThatThrownBy(() -> subject.resolveCreationAttempt(false, expiryMeta))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_AUTORENEW_ACCOUNT));
    }

    @Test
    void onCreationUsesAutoRenewPeriodEvenWithoutFullSpecIfSelfFunding() {
        assertThatCode(() -> subject.resolveCreationAttempt(true, new ExpiryMeta(NA, A_PERIOD, NA)))
                .doesNotThrowAnyException();
    }

    @Test
    void onCreationRequiresValidExpiryIfImplicit() {
        willThrow(new HandleException(INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(NOW.getEpochSecond() + A_PERIOD);

        final var expiryMeta = new ExpiryMeta(NA, A_PERIOD, AN_AUTO_RENEW_NUM);
        assertThatThrownBy(() -> subject.resolveCreationAttempt(false, expiryMeta))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_EXPIRATION_TIME));
    }

    @Test
    void validatesAutoRenewPeriodIfSet() {
        willThrow(new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(A_PERIOD);

        final var expiryMeta = new ExpiryMeta(A_TIME, A_PERIOD, NA);
        assertThatThrownBy(() -> subject.resolveCreationAttempt(false, expiryMeta))
                .isInstanceOf(HandleException.class)
                .has(responseCode(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @Test
    void validatesImpliedExpiry() {
        willThrow(new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(A_PERIOD);

        final var expiryMeta = new ExpiryMeta(A_TIME, A_PERIOD, NA);
        assertThatThrownBy(() -> subject.resolveCreationAttempt(false, expiryMeta))
                .isInstanceOf(HandleException.class)
                .has(responseCode(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @Test
    void summarizesExpiryOnlyCase() {
        assertThatCode(() -> subject.resolveCreationAttempt(false, new ExpiryMeta(A_TIME, NA, NA)))
                .doesNotThrowAnyException();
    }

    @Test
    void summarizesExpiryAndAutoRenewNumCase() {
        assertThatCode(() -> subject.resolveCreationAttempt(false, new ExpiryMeta(A_TIME, NA, AN_AUTO_RENEW_NUM)))
                .doesNotThrowAnyException();
    }

    @Test
    void summarizesExpiryAndValidAutoRenewPeriodCase() {
        assertThatCode(() -> subject.resolveCreationAttempt(false, new ExpiryMeta(A_TIME, A_PERIOD, NA)))
                .doesNotThrowAnyException();
    }

    @Test
    void summarizesFullAutoRenewSpecPeriodCase() {
        assertThatCode(() -> subject.resolveCreationAttempt(false, new ExpiryMeta(NOW.getEpochSecond() + A_PERIOD, A_PERIOD, AN_AUTO_RENEW_NUM)))
                .doesNotThrowAnyException();
    }

    @Test
    void updateCannotExplicitlyReduceExpiry() {
        final var current = new ExpiryMeta(A_TIME, NA, NA);
        final var update = new ExpiryMeta(A_TIME - 1, NA, NA);

        assertThatThrownBy(() -> subject.resolveUpdateAttempt(current, update))
                .isInstanceOf(HandleException.class)
                .has(responseCode(EXPIRATION_REDUCTION_NOT_ALLOWED));
    }

    @Test
    void explicitExpiryExtensionMustBeValid() {
        final var current = new ExpiryMeta(A_TIME, NA, NA);
        final var update = new ExpiryMeta(A_TIME - 1, NA, NA);

        assertThatThrownBy(() -> subject.resolveUpdateAttempt(current, update))
                .isInstanceOf(HandleException.class)
                .has(responseCode(EXPIRATION_REDUCTION_NOT_ALLOWED));
    }

    @Test
    void ifJustSettingAutoRenewAccountThenNetPeriodMustBeValid() {
        final var current = new ExpiryMeta(A_TIME, 0, NA);
        final var update = new ExpiryMeta(NA, NA, AN_AUTO_RENEW_NUM);

        willThrow(new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(0L);

        assertThatThrownBy(() -> subject.resolveUpdateAttempt(current, update))
                .isInstanceOf(HandleException.class)
                .has(responseCode(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @Test
    void ifSettingAutoRenewPeriodThenMustBeValid() {
        final var current = new ExpiryMeta(A_TIME, 0, NA);
        final var update = new ExpiryMeta(NA, B_PERIOD, AN_AUTO_RENEW_NUM);

        willThrow(new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(attributeValidator)
                .validateAutoRenewPeriod(B_PERIOD);

        assertThatThrownBy(() -> subject.resolveUpdateAttempt(current, update))
                .isInstanceOf(HandleException.class)
                .has(responseCode(AUTORENEW_DURATION_NOT_IN_RANGE));
    }

    @Test
    void ifUpdatingAutoRenewNumMustBeValid() {
        final var current = new ExpiryMeta(A_TIME, 0, NA);
        final var update = new ExpiryMeta(NA, B_PERIOD, AN_AUTO_RENEW_NUM);

        given(accountStore.getAccountById(AccountID.newBuilder().accountNum(AN_AUTO_RENEW_NUM).build()))
                .willThrow(new InvalidTransactionException(
                        com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT));

        assertThatThrownBy(() -> subject.resolveUpdateAttempt(current, update))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_AUTORENEW_ACCOUNT));
    }

    @Test
    void ifUpdatingExpiryMustBeValid() {
        final var current = new ExpiryMeta(A_TIME, 0, NA);
        final var update = new ExpiryMeta(B_TIME, B_PERIOD, AN_AUTO_RENEW_NUM);

        willThrow(new HandleException(INVALID_EXPIRATION_TIME))
                .given(attributeValidator)
                .validateExpiry(B_TIME);

        assertThatThrownBy(() -> subject.resolveUpdateAttempt(current, update))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_EXPIRATION_TIME));
    }

    @Test
    void canSetEverythingValidly() {
        final var current = new ExpiryMeta(A_TIME, 0, NA);
        final var update = new ExpiryMeta(B_TIME, B_PERIOD, AN_AUTO_RENEW_NUM);

        assertThat(subject.resolveUpdateAttempt(current, update)).isEqualTo(update);
    }

    @Test
    void canUseWildcardForRemovingAutoRenewAccount() {
        final var current = new ExpiryMeta(A_TIME, 0, NA);
        final var update = new ExpiryMeta(B_TIME, B_PERIOD, 0);

        assertThat(subject.resolveUpdateAttempt(current, update)).isEqualTo(update);
    }

    @Test
    void checksIfAccountIsDetachedIfBalanceZero() {
        assertThat(subject.expirationStatus(EntityType.ACCOUNT, false, 0)).isEqualTo(OK);
        assertThat(subject.isDetached(EntityType.ACCOUNT, false, 0)).isFalse();
    }

    @Test
    void failsIfAccountExpiredAndPendingRemoval() {
        assertThat(subject.expirationStatus(EntityType.ACCOUNT, true, 0L)).isEqualTo(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
        assertThat(subject.isDetached(EntityType.ACCOUNT, true, 0)).isTrue();

        assertThat(subject.expirationStatus(EntityType.CONTRACT, true, 0L)).isEqualTo(CONTRACT_EXPIRED_AND_PENDING_REMOVAL);
        assertThat(subject.isDetached(EntityType.CONTRACT, true, 0)).isTrue();
    }

    @Test
    void notDetachedIfAccountNotExpired() {
        assertThat(subject.expirationStatus(EntityType.ACCOUNT, false, 0L)).isEqualTo(OK);
        assertThat(subject.isDetached(EntityType.ACCOUNT, false, 10)).isFalse();
    }

    @Test
    void notDetachedIfAutoRenewDisabled() {
        assertThat(subject.expirationStatus(EntityType.ACCOUNT, false, 0L)).isEqualTo(OK);
        assertThat(subject.isDetached(EntityType.ACCOUNT, false, 0)).isFalse();
    }
}
