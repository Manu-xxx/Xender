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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.SELF_DESTRUCT_BENEFICIARIES;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_FUNGIBLE_RELATION;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CANONICAL_ALIAS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CIVILIAN_OWNED_NFT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NFT_SERIAL_NO;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.PARANOID_SOMEBODY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOMEBODY;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.LAZY_CREATION_MEMO;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.synthHollowAccountCreation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.DeleteCapableTransactionRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleHederaNativeOperationsTest {
    @Mock
    private HandleContext context;

    @Mock
    private MessageFrame frame;

    @Mock
    private ReadableTokenStore tokenStore;

    @Mock
    private CryptoCreateRecordBuilder cryptoCreateRecordBuilder;

    @Mock
    private ReadableTokenRelationStore relationStore;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private Predicate<Key> signatureTest;

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private TokenServiceApi tokenServiceApi;

    @Mock
    private ReadableNftStore nftStore;

    private final Deque<MessageFrame> stack = new ArrayDeque<>();

    private HandleHederaNativeOperations subject;

    @BeforeEach
    void setUp() {
        subject = new HandleHederaNativeOperations(context);
    }

    @Test
    void getAccountUsesContextReadableStore() {
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountById(NON_SYSTEM_ACCOUNT_ID)).willReturn(Account.DEFAULT);
        assertSame(Account.DEFAULT, subject.getAccount(NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow()));
    }

    @Test
    void getAccountKeyUsesContextReadableStore() {
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountById(NON_SYSTEM_ACCOUNT_ID)).willReturn(SOMEBODY);
        assertSame(SOMEBODY.keyOrThrow(), subject.getAccountKey(NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow()));
    }

    @Test
    void getAccountKeyReturnsNullForMissing() {
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        assertNull(subject.getAccountKey(NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow()));
    }

    @Test
    void resolveAliasReturnsMissingNumIfNotPresent() {
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        assertEquals(MISSING_ENTITY_NUMBER, subject.resolveAlias(tuweniToPbjBytes(EIP_1014_ADDRESS)));
    }

    @Test
    void resolveAliasReturnsNumIfPresent() {
        final var alias = tuweniToPbjBytes(EIP_1014_ADDRESS);
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountIDByAlias(alias)).willReturn(NON_SYSTEM_ACCOUNT_ID);
        assertEquals(NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow(), subject.resolveAlias(alias));
    }

    @Test
    void getTokenUsesStore() {
        given(context.readableStore(ReadableTokenStore.class)).willReturn(tokenStore);
        given(tokenStore.get(FUNGIBLE_TOKEN_ID)).willReturn(FUNGIBLE_TOKEN);
        assertSame(FUNGIBLE_TOKEN, subject.getToken(FUNGIBLE_TOKEN_ID.tokenNum()));
    }

    @Test
    void createsHollowAccountByDispatching() {
        final var synthLazyCreate = TransactionBody.newBuilder()
                .cryptoCreateAccount(synthHollowAccountCreation(CANONICAL_ALIAS))
                .build();
        given(context.payer()).willReturn(A_NEW_ACCOUNT_ID);

        when(context.dispatchRemovablePrecedingTransaction(
                        eq(synthLazyCreate), eq(CryptoCreateRecordBuilder.class), eq(null), eq(A_NEW_ACCOUNT_ID)))
                .thenReturn(cryptoCreateRecordBuilder);

        final var synthLazyCreateFees = new Fees(1L, 2L, 3L);
        given(context.dispatchComputeFees(synthLazyCreate, A_NEW_ACCOUNT_ID)).willReturn(synthLazyCreateFees);

        final var synthFinalizatonFees = new Fees(4L, 5L, 6L);
        final var synthFinalizationTxn = TransactionBody.newBuilder()
                .cryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
                        .key(Key.newBuilder().ecdsaSecp256k1(Bytes.EMPTY)))
                .build();
        given(context.dispatchComputeFees(synthFinalizationTxn, A_NEW_ACCOUNT_ID))
                .willReturn(synthFinalizatonFees);

        given(cryptoCreateRecordBuilder.status()).willReturn(OK);

        final var status = subject.createHollowAccount(CANONICAL_ALIAS);
        assertEquals(OK, status);

        verify(cryptoCreateRecordBuilder).memo(LAZY_CREATION_MEMO);
        verify(cryptoCreateRecordBuilder)
                .transactionFee(synthLazyCreateFees.totalFee() + synthFinalizatonFees.totalFee());
    }

    @Test
    void createsHollowAccountByDispatchingDoesNotThrowErrors() {
        final var synthLazyCreate = TransactionBody.newBuilder()
                .cryptoCreateAccount(synthHollowAccountCreation(CANONICAL_ALIAS))
                .build();
        given(context.payer()).willReturn(A_NEW_ACCOUNT_ID);
        given(context.dispatchRemovablePrecedingTransaction(
                        eq(synthLazyCreate), eq(CryptoCreateRecordBuilder.class), eq(null), eq(A_NEW_ACCOUNT_ID)))
                .willReturn(cryptoCreateRecordBuilder);

        final var synthLazyCreateFees = new Fees(1L, 2L, 3L);
        given(context.dispatchComputeFees(synthLazyCreate, A_NEW_ACCOUNT_ID)).willReturn(synthLazyCreateFees);

        final var synthFinalizatonFees = new Fees(4L, 5L, 6L);
        final var synthFinalizationTxn = TransactionBody.newBuilder()
                .cryptoUpdateAccount(CryptoUpdateTransactionBody.newBuilder()
                        .key(Key.newBuilder().ecdsaSecp256k1(Bytes.EMPTY)))
                .build();
        given(context.dispatchComputeFees(synthFinalizationTxn, A_NEW_ACCOUNT_ID))
                .willReturn(synthFinalizatonFees);
        given(cryptoCreateRecordBuilder.status()).willReturn(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        final var status = assertDoesNotThrow(() -> subject.createHollowAccount(CANONICAL_ALIAS));
        assertThat(status).isEqualTo(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        verify(cryptoCreateRecordBuilder).memo(LAZY_CREATION_MEMO);
        verify(cryptoCreateRecordBuilder)
                .transactionFee(synthLazyCreateFees.totalFee() + synthFinalizatonFees.totalFee());
    }

    @Test
    void finalizeHollowAccountAsContractUsesApiAndStore() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountIDByAlias(CANONICAL_ALIAS)).willReturn(A_NEW_ACCOUNT_ID);

        subject.finalizeHollowAccountAsContract(CANONICAL_ALIAS);

        verify(tokenServiceApi).finalizeHollowAccountAsContract(A_NEW_ACCOUNT_ID);
        verify(context).newEntityNum();
    }

    @Test
    void transferWithReceiverSigCheckUsesApi() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var contractAccountId = AccountID.newBuilder()
                .accountNum(NON_SYSTEM_CONTRACT_ID.contractNumOrThrow())
                .build();
        given(accountStore.getAccountById(contractAccountId)).willReturn(PARANOID_SOMEBODY);
        given(verificationStrategy.asSignatureTestIn(context)).willReturn(signatureTest);
        given(signatureTest.test(PARANOID_SOMEBODY.keyOrThrow())).willReturn(true);

        final var result = subject.transferWithReceiverSigCheck(
                1L,
                NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow(),
                NON_SYSTEM_CONTRACT_ID.contractNumOrThrow(),
                verificationStrategy);
        assertEquals(OK, result);
        verify(tokenServiceApi).transferFromTo(NON_SYSTEM_ACCOUNT_ID, contractAccountId, 1L);
    }

    @Test
    void transferWithReceiverSigCheckReturnsInvalidSigIfAppropriate() {
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var contractAccountId = AccountID.newBuilder()
                .accountNum(NON_SYSTEM_CONTRACT_ID.contractNumOrThrow())
                .build();
        given(accountStore.getAccountById(contractAccountId)).willReturn(PARANOID_SOMEBODY);
        given(verificationStrategy.asSignatureTestIn(context)).willReturn(signatureTest);
        given(signatureTest.test(PARANOID_SOMEBODY.keyOrThrow())).willReturn(false);

        final var result = subject.transferWithReceiverSigCheck(
                1L,
                NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow(),
                NON_SYSTEM_CONTRACT_ID.contractNumOrThrow(),
                verificationStrategy);
        assertEquals(INVALID_SIGNATURE, result);
        verify(tokenServiceApi, never()).transferFromTo(NON_SYSTEM_ACCOUNT_ID, contractAccountId, 1L);
    }

    @Test
    void transferWithReceiverSigCheckSkipsCheckWithoutRequirement() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        final var contractAccountId = AccountID.newBuilder()
                .accountNum(NON_SYSTEM_CONTRACT_ID.contractNumOrThrow())
                .build();
        given(accountStore.getAccountById(contractAccountId)).willReturn(SOMEBODY);

        final var result = subject.transferWithReceiverSigCheck(
                1L,
                NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow(),
                NON_SYSTEM_CONTRACT_ID.contractNumOrThrow(),
                verificationStrategy);
        assertEquals(OK, result);
        verify(tokenServiceApi).transferFromTo(NON_SYSTEM_ACCOUNT_ID, contractAccountId, 1L);
    }

    @Test
    void trackDeletionUpdatesMap() {
        final DeleteCapableTransactionRecordBuilder beneficiaries = mock(DeleteCapableTransactionRecordBuilder.class);
        given(frame.getMessageFrameStack()).willReturn(stack);
        stack.push(frame);
        given(frame.getContextVariable(SELF_DESTRUCT_BENEFICIARIES)).willReturn(beneficiaries);
        subject.trackSelfDestructBeneficiary(1L, 2L, frame);
        verify(beneficiaries)
                .addBeneficiaryForDeletedAccount(
                        AccountID.newBuilder().accountNum(1L).build(),
                        AccountID.newBuilder().accountNum(2L).build());
    }

    @Test
    void settingNonceUsesApi() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);

        subject.setNonce(123L, 456L);

        verify(tokenServiceApi).setNonce(AccountID.newBuilder().accountNum(123L).build(), 456L);
    }

    @Test
    void getRelationshipUsesStore() {
        given(context.readableStore(ReadableTokenRelationStore.class)).willReturn(relationStore);
        given(relationStore.get(A_NEW_ACCOUNT_ID, FUNGIBLE_TOKEN_ID)).willReturn(A_FUNGIBLE_RELATION);
        assertSame(
                A_FUNGIBLE_RELATION,
                subject.getTokenRelation(A_NEW_ACCOUNT_ID.accountNumOrThrow(), FUNGIBLE_TOKEN_ID.tokenNum()));
    }

    @Test
    void getNftUsesStore() {
        given(context.readableStore(ReadableNftStore.class)).willReturn(nftStore);
        given(nftStore.get(CIVILIAN_OWNED_NFT.nftIdOrThrow())).willReturn(CIVILIAN_OWNED_NFT);
        assertSame(CIVILIAN_OWNED_NFT, subject.getNft(NON_FUNGIBLE_TOKEN_ID.tokenNum(), NFT_SERIAL_NO));
    }

    @Test
    void customFeesCheckUsesApi() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        given(tokenServiceApi.checkForCustomFees(CryptoTransferTransactionBody.DEFAULT))
                .willReturn(true);
        final var result = subject.checkForCustomFees(CryptoTransferTransactionBody.DEFAULT);
        assertTrue(result);
    }
}
