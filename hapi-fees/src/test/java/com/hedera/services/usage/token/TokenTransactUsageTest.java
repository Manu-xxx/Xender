package com.hedera.services.usage.token;

import com.hedera.services.test.IdUtils;
import com.hedera.services.usage.EstimatorFactory;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.TxnUsageEstimator;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TokenRefTransferList;
import com.hederahashgraph.api.proto.java.TokenTransfers;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.List;

import static com.hedera.services.test.IdUtils.asAccount;
import static com.hedera.services.test.UsageUtils.A_USAGES_MATRIX;
import static com.hedera.services.usage.SingletonUsageProperties.USAGE_PROPERTIES;
import static com.hedera.services.usage.token.TokenEntitySizes.TOKEN_ENTITY_SIZES;
import static org.junit.Assert.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
public class TokenTransactUsageTest {
	long now = 1_234_567L;
	int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	String symbol = "ABCDEFGH";

	AccountID a = asAccount("1.2.3");
	AccountID b = asAccount("2.3.4");
	AccountID c = asAccount("3.4.5");
	TokenID anId = IdUtils.asToken("0.0.75231");
	String aSymbol = "ABCDEFGH";
	String anotherSymbol = "HGFEDCBA";

	TokenTransfers op;
	TransactionBody txn;

	EstimatorFactory factory;
	TxnUsageEstimator base;
	TokenTransactUsage subject;

	@BeforeEach
	public void setUp() throws Exception {
		base = mock(TxnUsageEstimator.class);
		given(base.get()).willReturn(A_USAGES_MATRIX);

		factory = mock(EstimatorFactory.class);
		given(factory.get(any(), any(), any())).willReturn(base);

		TokenTransactUsage.estimatorFactory = factory;
	}

	@Test
	public void createsExpectedDeltaForTransferList() {
		givenOp();
		// and:
		subject = TokenTransactUsage.newEstimate(txn, sigUsage);

		// when:
		var actual = subject.get();

		// then:
		assertEquals(A_USAGES_MATRIX, actual);
		// and:
		verify(base).addBpt(aSymbol.length()
				+ 3 * (FeeBuilder.BASIC_ENTITY_ID_SIZE + 8)
				+ FeeBuilder.BASIC_ENTITY_ID_SIZE
				+ 2 * (FeeBuilder.BASIC_ENTITY_ID_SIZE + 8)
				+ anotherSymbol.length()
				+ 2 * (FeeBuilder.BASIC_ENTITY_ID_SIZE + 8));
		verify(base).addRbs(
				TOKEN_ENTITY_SIZES.bytesUsedToRecordTransfers(3, 7) *
						USAGE_PROPERTIES.legacyReceiptStorageSecs());
	}

	private void givenOp() {
		op = TokenTransfers.newBuilder()
				.addTokenTransfers(TokenRefTransferList.newBuilder()
						.setToken(TokenRef.newBuilder().setSymbol(aSymbol).build())
						.addAllTransfers(List.of(
								adjustFrom(a, -50),
								adjustFrom(b, 25),
								adjustFrom(c, 25)
						)))
				.addTokenTransfers(TokenRefTransferList.newBuilder()
						.setToken(TokenRef.newBuilder().setTokenId(anId).build())
						.addAllTransfers(List.of(
								adjustFrom(b, -100),
								adjustFrom(c, 100)
						)))
				.addTokenTransfers(TokenRefTransferList.newBuilder()
						.setToken(TokenRef.newBuilder().setSymbol(anotherSymbol).build())
						.addAllTransfers(List.of(
								adjustFrom(a, -15),
								adjustFrom(b, 15)
						)))
				.build();

		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setTokenTransfers(op)
				.build();
	}

	private AccountAmount adjustFrom(AccountID account, long amount) {
		return AccountAmount.newBuilder()
				.setAmount(amount)
				.setAccountID(account)
				.build();
	}
}
