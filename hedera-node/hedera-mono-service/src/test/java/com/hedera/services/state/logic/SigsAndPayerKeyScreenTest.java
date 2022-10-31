/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.logic;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.sigs.Rationalization;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.txns.span.EthTxExpansion;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.RationalizedSigMeta;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.function.BiPredicate;
import java.util.function.Supplier;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class SigsAndPayerKeyScreenTest {
    @Mock private Rationalization rationalization;
    @Mock private PayerSigValidity payerSigValidity;
    @Mock private TransactionContext txnCtx;
    @Mock private MiscSpeedometers speedometers;
    @Mock private BiPredicate<JKey, TransactionSignature> validityTest;
    @Mock private PlatformTxnAccessor accessor;
    @Mock private Supplier<AccountStorageAdapter> accounts;
    @Mock private AccountStorageAdapter accountStorage;
    @Mock private MerkleAccount account;
    @Mock private EntityCreator creator;
    @Mock private ExpirableTxnRecord.Builder childRecordBuilder;
    @Mock private TxnReceipt.Builder txnReceiptBuilder;
    @Mock private SyntheticTxnFactory syntheticTxnFactory;
    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private RecordsHistorian recordsHistorian;
    @Mock private ExpandHandleSpanMapAccessor spanMapAccessor;
    @Mock private AliasManager aliasManager;
    @Mock private GlobalDynamicProperties properties;
    @Mock private RationalizedSigMeta sigMeta;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private SigsAndPayerKeyScreen subject;

    @BeforeEach
    void setUp() {
        subject =
                new SigsAndPayerKeyScreen(
                        rationalization,
                        payerSigValidity,
                        txnCtx,
                        speedometers,
                        validityTest,
                        accounts,
                        creator,
                        syntheticTxnFactory,
                        sigImpactHistorian,
                        recordsHistorian,
                        spanMapAccessor,
                        aliasManager,
                        properties);
    }

    @Test
    void propagatesRationalizedStatus() {
        given(rationalization.finalStatus()).willReturn(INVALID_ACCOUNT_ID);

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(rationalization).performFor(accessor);
        verifyNoInteractions(speedometers);
        // and:
        Assertions.assertEquals(INVALID_ACCOUNT_ID, result);
    }

    @Test
    void marksPayerSigActiveAndPreparesWhenVerified() {
        givenOkRationalization();
        given(payerSigValidity.test(accessor, validityTest)).willReturn(true);

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(txnCtx).payerSigIsKnownActive();
        // and:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void hollowAccountCompletionSucceeds() {
        givenOkRationalization();
        given(accessor.getSigMeta()).willReturn(sigMeta);
        given(accessor.getSigMap()).willReturn(SignatureMap.getDefaultInstance());
        given(payerSigValidity.test(accessor, validityTest)).willReturn(true);
        given(sigMeta.hasReplacedHollowKey()).willReturn(true);
        given(accounts.get()).willReturn(accountStorage);
        given(txnCtx.activePayer()).willReturn(AccountID.getDefaultInstance());
        given(accountStorage.getForModify(any())).willReturn(account);
        given(sigMeta.payerKey())
                .willReturn(
                        new JECDSASecp256k1Key(ByteString.copyFromUtf8("payerKey").toByteArray()));
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any()))
                .willReturn(childRecordBuilder);
        given(childRecordBuilder.getReceiptBuilder()).willReturn(txnReceiptBuilder);
        given(txnReceiptBuilder.getAccountId()).willReturn(EntityId.fromNum(1));

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(account).setAccountKey(sigMeta.payerKey());
        // and:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void hollowAccountCompletionForEthereumTxnSucceeds() {
        givenOkRationalization();
        given(properties.isLazyCreationEnabled()).willReturn(true);
        given(spanMapAccessor.getEthTxExpansion(accessor)).willReturn(new EthTxExpansion(null, OK));
        var key = new JECDSASecp256k1Key(ByteString.copyFromUtf8("publicKey").toByteArray());
        given(spanMapAccessor.getEthTxSigsMeta(accessor))
                .willReturn(new EthTxSigs(key.getECDSASecp256k1Key(), new byte[0]));
        given(aliasManager.lookupIdBy(any())).willReturn(EntityNum.fromInt(1));
        given(accounts.get()).willReturn(accountStorage);
        given(accountStorage.getForModify(any())).willReturn(account);
        given(account.getAccountKey()).willReturn(EMPTY_KEY);
        given(creator.createSuccessfulSyntheticRecord(any(), any(), any()))
                .willReturn(childRecordBuilder);
        given(childRecordBuilder.getReceiptBuilder()).willReturn(txnReceiptBuilder);
        given(txnReceiptBuilder.getAccountId()).willReturn(EntityId.fromNum(1));

        // when:
        final var result = subject.applyTo(accessor);

        // then:
        verify(account).setAccountKey(key);
        // and:
        Assertions.assertEquals(OK, result);
    }

    @Test
    void warnsWhenPayerSigActivationThrows() {
        givenOkRationalization();
        given(payerSigValidity.test(accessor, validityTest))
                .willThrow(IllegalArgumentException.class);

        // when:
        subject.applyTo(accessor);

        // then:
        assertThat(
                logCaptor.warnLogs(),
                contains(
                        Matchers.startsWith(
                                "Unhandled exception while testing payer sig activation")));
    }

    @Test
    void cyclesSyncWhenUsed() {
        givenOkRationalization(true);

        // when:
        subject.applyTo(accessor);

        // then:
        verify(speedometers).cycleSyncVerifications();
    }

    @Test
    void doesntCyclesAsyncAnymore() {
        givenOkRationalization();

        subject.applyTo(accessor);

        verifyNoInteractions(speedometers);
    }

    private void givenOkRationalization() {
        givenOkRationalization(false);
    }

    private void givenOkRationalization(boolean usedSync) {
        given(rationalization.finalStatus()).willReturn(OK);
        if (usedSync) {
            given(rationalization.usedSyncVerification()).willReturn(true);
        }
    }
}
