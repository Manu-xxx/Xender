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

package com.hedera.node.app.service.token.impl.test.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.AccountApprovalForAllAllowance;
import com.hedera.hapi.node.token.CryptoAllowance;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.hapi.node.token.TokenAllowance;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.ApproveAllowanceValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ApproveAllowanceValidatorTest extends CryptoTokenHandlerTestBase {
    private ApproveAllowanceValidator subject;

    @Mock(strictness = LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @BeforeEach
    public void setUp() {
        super.setUp();
        givenStoresAndConfig(configProvider, handleContext);
        subject = new ApproveAllowanceValidator(configProvider);
    }

    @Test
    void notSupportedFails() {
        givenApproveAllowanceTxn(id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
        final var configuration = new HederaTestConfigBuilder()
                .withValue("hedera.allowances.isEnabled", false)
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NOT_SUPPORTED));
    }

    @Test
    void returnsValidationOnceFailed() {
        // each serial number is considered as one allowance for nft allowances
        givenApproveAllowanceTxn(id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
        final var configuration = new HederaTestConfigBuilder()
                .withValue("hedera.allowances.maxTransactionLimit", 1)
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MAX_ALLOWANCES_EXCEEDED));
    }

    @Test
    void succeedsWithEmptyLists() {
        givenApproveAllowanceTxn(id, false, List.of(), List.of(tokenAllowance), List.of(nftAllowance));

        assertThatNoException().isThrownBy(() -> subject.validate(handleContext, account, readableAccountStore));

        givenApproveAllowanceTxn(id, false, List.of(cryptoAllowance), List.of(), List.of(nftAllowance));

        assertThatNoException().isThrownBy(() -> subject.validate(handleContext, account, readableAccountStore));

        givenApproveAllowanceTxn(id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of());

        assertThatNoException().isThrownBy(() -> subject.validate(handleContext, account, readableAccountStore));
    }

    @Test
    void validatesSpenderSameAsOwner() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance.copyBuilder().spender(ownerId).build()),
                List.of(tokenAllowance),
                List.of(nftAllowance));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(SPENDER_ACCOUNT_SAME_AS_OWNER));

        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance.copyBuilder().spender(ownerId).build()),
                List.of(nftAllowance));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(SPENDER_ACCOUNT_SAME_AS_OWNER));

        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance),
                List.of(nftAllowance.copyBuilder().spender(ownerId).build()));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(SPENDER_ACCOUNT_SAME_AS_OWNER));
    }

    @Test
    void validateNegativeAmounts() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(CryptoAllowance.newBuilder()
                        .amount(-1L)
                        .owner(ownerId)
                        .spender(spenderId)
                        .build()),
                List.of(tokenAllowance),
                List.of(nftAllowance));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NEGATIVE_ALLOWANCE_AMOUNT));

        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(TokenAllowance.newBuilder()
                        .amount(-1L)
                        .owner(ownerId)
                        .spender(spenderId)
                        .tokenId(fungibleTokenId)
                        .build()),
                List.of(nftAllowance));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NEGATIVE_ALLOWANCE_AMOUNT));
    }

    @Test
    void failsWhenExceedsMaxTokenSupply() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(TokenAllowance.newBuilder()
                        .amount(100001)
                        .owner(ownerId)
                        .spender(spenderId)
                        .tokenId(fungibleTokenId)
                        .build()),
                List.of(nftAllowance));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(AMOUNT_EXCEEDS_TOKEN_MAX_SUPPLY));
    }

    @Test
    void failsForNftInFungibleTokenAllowances() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(TokenAllowance.newBuilder()
                        .amount(10)
                        .owner(ownerId)
                        .spender(spenderId)
                        .tokenId(nonFungibleTokenId)
                        .build()),
                List.of(nftAllowance));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NFT_IN_FUNGIBLE_TOKEN_ALLOWANCES));
    }

    @Test
    void cannotGrantApproveForAllWhenDelegatingSpenderIsSet() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(),
                List.of(),
                List.of(nftAllowance
                        .copyBuilder()
                        .owner(ownerId)
                        .spender(spenderId)
                        .delegatingSpender(delegatingSpenderId)
                        .approvedForAll(true)
                        .build()));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(DELEGATING_SPENDER_CANNOT_GRANT_APPROVE_FOR_ALL));
    }

    @Test
    void canGrantNftSerialAllowanceIfDelegatingSpenderHasNoApproveForAllAllowance() {
        assertThat(ownerAccount
                        .approveForAllNftAllowances()
                        .contains(AccountApprovalForAllAllowance.newBuilder()
                                .spenderNum(spenderId.accountNum())
                                .tokenNum(nonFungibleTokenId.tokenNum())
                                .build()))
                .isTrue();

        givenApproveAllowanceTxn(
                id,
                false,
                List.of(),
                List.of(),
                List.of(nftAllowance
                        .copyBuilder()
                        .delegatingSpender(delegatingSpenderId)
                        .build()));

        assertThatNoException().isThrownBy(() -> subject.validate(handleContext, account, readableAccountStore));
    }

    @Test
    void cannotGrantNftSerialAllowanceIfDelegatingSpenderHasNoApproveForAllAllowance() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(),
                List.of(),
                List.of(nftAllowance
                        .copyBuilder()
                        .spender(id)
                        .delegatingSpender(transferAccountId)
                        .build()));

        assertThat(readableAccountStore
                        .getAccountById(ownerId)
                        .approveForAllNftAllowances()
                        .contains(AccountApprovalForAllAllowance.newBuilder()
                                .spenderNum(id.accountNum())
                                .tokenNum(nonFungibleTokenId.tokenNum())
                                .build()))
                .isFalse();

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(DELEGATING_SPENDER_DOES_NOT_HAVE_APPROVE_FOR_ALL));
    }

    @Test
    void failsWhenTokenNotAssociatedToAccount() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance.copyBuilder().owner(delegatingSpenderId).build()),
                List.of(),
                List.of());
        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));

        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance.copyBuilder().owner(delegatingSpenderId).build()),
                List.of());
        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));

        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance),
                List.of(nftAllowance.copyBuilder().owner(delegatingSpenderId).build()));
        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT));
    }

    @Test
    void happyPath() {
        givenApproveAllowanceTxn(id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
        assertThatNoException().isThrownBy(() -> subject.validate(handleContext, account, readableAccountStore));
    }

    @Test
    void fungibleInNFTAllowances() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance),
                List.of(NftAllowance.newBuilder()
                        .owner(ownerId)
                        .spender(spenderId)
                        .tokenId(fungibleTokenId)
                        .approvedForAll(Boolean.TRUE)
                        .build()));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(FUNGIBLE_TOKEN_IN_NFT_ALLOWANCES));
    }

    @Test
    void validateSerialsExistence() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance),
                List.of(NftAllowance.newBuilder()
                        .owner(ownerId)
                        .spender(spenderId)
                        .tokenId(nonFungibleTokenId)
                        .serialNumbers(List.of(1L, 2L, 3L))
                        .build()));

        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_NFT_SERIAL_NUMBER));
    }

    @Test
    void validateNegativeSerials() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance),
                List.of(NftAllowance.newBuilder()
                        .owner(ownerId)
                        .spender(spenderId)
                        .tokenId(nonFungibleTokenId)
                        .serialNumbers(List.of(1L, 2L, -3L))
                        .build()));

        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_NFT_SERIAL_NUMBER));
    }

    @Test
    void validatesAndFiltersRepeatedSerials() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance),
                List.of(NftAllowance.newBuilder()
                        .owner(ownerId)
                        .spender(spenderId)
                        .tokenId(nonFungibleTokenId)
                        .serialNumbers(List.of(1L, 2L, 2L, 1L))
                        .build()));

        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        assertThatNoException().isThrownBy(() -> subject.validate(handleContext, account, readableAccountStore));
    }

    @Test
    void validatesTotalAllowancesInTxn() {
        // each serial number is considered as one allowance
        givenApproveAllowanceTxn(id, false, List.of(cryptoAllowance), List.of(tokenAllowance), List.of(nftAllowance));
        final var configuration = new HederaTestConfigBuilder()
                .withValue("hedera.allowances.maxTransactionLimit", 1)
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));

        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(MAX_ALLOWANCES_EXCEEDED));
    }

    @Test
    void validatesMissingOwnerAccount() {
        final var missingOwner = AccountID.newBuilder().accountNum(1_234L).build();
        final var missingCryptoAllowance = CryptoAllowance.newBuilder()
                .owner(missingOwner)
                .spender(spenderId)
                .amount(10L)
                .build();
        final var missingTokenAllowance = TokenAllowance.newBuilder()
                .owner(missingOwner)
                .spender(spenderId)
                .amount(10L)
                .tokenId(fungibleTokenId)
                .build();
        final var missingNftAllowance = NftAllowance.newBuilder()
                .owner(missingOwner)
                .spender(spenderId)
                .tokenId(nonFungibleTokenId)
                .serialNumbers(List.of(1L))
                .build();
        givenApproveAllowanceTxn(id, false, List.of(missingCryptoAllowance), List.of(), List.of());
        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ALLOWANCE_OWNER_ID));

        givenApproveAllowanceTxn(id, false, List.of(), List.of(missingTokenAllowance), List.of());
        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ALLOWANCE_OWNER_ID));

        givenApproveAllowanceTxn(id, false, List.of(), List.of(), List.of(missingNftAllowance));
        assertThatThrownBy(() -> subject.validate(handleContext, account, readableAccountStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_ALLOWANCE_OWNER_ID));
    }

    @Test
    void considersPayerIfOwnerMissing() {
        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance.copyBuilder().owner(AccountID.DEFAULT).build()),
                List.of(),
                List.of());
        assertThatNoException().isThrownBy(() -> subject.validate(handleContext, account, readableAccountStore));

        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance.copyBuilder().owner(AccountID.DEFAULT).build()),
                List.of());
        assertThatNoException().isThrownBy(() -> subject.validate(handleContext, account, readableAccountStore));

        givenApproveAllowanceTxn(
                id,
                false,
                List.of(cryptoAllowance),
                List.of(tokenAllowance),
                List.of(nftAllowance.copyBuilder().owner(AccountID.DEFAULT).build()));
        assertThatNoException().isThrownBy(() -> subject.validate(handleContext, account, readableAccountStore));
    }

    private TransactionBody givenApproveAllowanceTxn(
            final AccountID id,
            final boolean isWithDelegatingSpender,
            final List<CryptoAllowance> cryptoAllowance,
            final List<TokenAllowance> tokenAllowance,
            final List<NftAllowance> nftAllowance) {
        final var transactionID = TransactionID.newBuilder().accountID(id).transactionValidStart(consensusTimestamp);
        final var allowanceTxnBody = CryptoApproveAllowanceTransactionBody.newBuilder()
                .cryptoAllowances(cryptoAllowance)
                .tokenAllowances(tokenAllowance)
                .nftAllowances(isWithDelegatingSpender ? List.of(nftAllowanceWithDelegatingSpender) : nftAllowance)
                .build();
        final var txn = TransactionBody.newBuilder()
                .transactionID(transactionID)
                .cryptoApproveAllowance(allowanceTxnBody)
                .build();
        given(handleContext.body()).willReturn(txn);
        return txn;
    }
}
