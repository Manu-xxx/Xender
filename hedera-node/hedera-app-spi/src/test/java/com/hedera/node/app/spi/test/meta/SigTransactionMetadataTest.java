package com.hedera.node.app.spi.test.meta;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.SigTransactionMetadataBuilder;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.node.app.spi.test.meta.SigTransactionMetadataBuilderTest.A_COMPLEX_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SigTransactionMetadataTest {
    private AccountID payer = AccountID.newBuilder().setAccountNum(3L).build();
    private Key key = A_COMPLEX_KEY;
    @Mock
    private HederaKey payerKey;
    @Mock
    private HederaKey otherKey;
    @Mock AccountKeyLookup lookup;
    private TransactionMetadata  subject;

    @Test
    void gettersWork(){
        given(lookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
        final var txn = createAccountTransaction();
        subject = new SigTransactionMetadataBuilder(lookup)
                .payerKeyFor(payer)
                .txnBody(txn)
                .addToReqKeys(otherKey)
                .build();

        assertFalse(subject.failed());
        assertEquals(txn, subject.txnBody());
        assertEquals(ResponseCodeEnum.OK, subject.status());
        assertEquals(List.of(payerKey, otherKey), subject.requiredKeys());
    }

    @Test
    void gettersWorkOnFailure(){
        given(lookup.getKey(payer)).willReturn(KeyOrLookupFailureReason.withKey(payerKey));
        final var txn = createAccountTransaction();
        subject = new SigTransactionMetadataBuilder(lookup)
                .payerKeyFor(payer)
                .status(INVALID_ACCOUNT_ID)
                .txnBody(txn)
                .addToReqKeys(otherKey)
                .build();

        assertTrue(subject.failed());
        assertEquals(txn, subject.txnBody());
        assertEquals(INVALID_ACCOUNT_ID, subject.status());
        assertEquals(List.of(payerKey, otherKey), subject.requiredKeys());
    }

    private TransactionBody createAccountTransaction() {
        final var transactionID =
                TransactionID.newBuilder()
                        .setAccountID(payer)
                        .setTransactionValidStart(Timestamp.newBuilder().setSeconds(123_456L).build());
        final var createTxnBody =
                CryptoCreateTransactionBody.newBuilder()
                        .setKey(key)
                        .setReceiverSigRequired(true)
                        .setMemo("Create Account")
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setCryptoCreateAccount(createTxnBody)
                .build();
    }

}
