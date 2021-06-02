package com.hedera.services.usage.crypto;

import com.hedera.services.test.AdapterUtils;
import com.hedera.services.test.IdUtils;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.test.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.*;

class OpsTransferUsageTest {
	private CryptoOpsUsage subject = new CryptoOpsUsage();

	@Test
	void matchesWithLegacy() {
		givenOp();
		// and:
		final var legacyProvider = CryptoTransferUsage.newEstimate(txn, sigUsage);
		final var expected = legacyProvider.givenTokenMultiplier(tokenMultiplier).get();

		// when:
		final var accum = new UsageAccumulator();
		subject.cryptoTransferUsage(
				sigUsage,
				new CryptoTransferMeta(tokenMultiplier, 3, 7),
				new BaseTransactionMeta(memo.getBytes().length, 3),
				accum);

		// then:
		assertEquals(expected, AdapterUtils.feeDataFrom(accum));
	}

	private final int tokenMultiplier = 60;
	private final int numSigs = 3, sigSize = 100, numPayerKeys = 1;
	private final String memo = "Yikes who knows";
	private final SigUsage sigUsage = new SigUsage(numSigs, sigSize, numPayerKeys);
	private final long now = 1_234_567L;
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("2.3.4");
	private final AccountID c = asAccount("3.4.5");
	private final TokenID anId = IdUtils.asToken("0.0.75231");
	private final TokenID anotherId = IdUtils.asToken("0.0.75232");
	private final TokenID yetAnotherId = IdUtils.asToken("0.0.75233");

	private TransactionBody txn;
	private  CryptoTransferTransactionBody op;

	private void givenOp() {
		var hbarAdjusts = TransferList.newBuilder()
				.addAccountAmounts(adjustFrom(a, -100))
				.addAccountAmounts(adjustFrom(b, 50))
				.addAccountAmounts(adjustFrom(c, 50))
				.build();
		op = CryptoTransferTransactionBody.newBuilder()
				.setTransfers(hbarAdjusts)
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -50),
								adjustFrom(b, 25),
								adjustFrom(c, 25)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anId)
						.addAllTransfers(List.of(
								adjustFrom(b, -100),
								adjustFrom(c, 100)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(yetAnotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -15),
								adjustFrom(b, 15)
						)))
				.build();

		setTxn();
	}

	private void setTxn() {
		txn = TransactionBody.newBuilder()
				.setMemo(memo)
				.setTransactionID(TransactionID.newBuilder()
						.setTransactionValidStart(Timestamp.newBuilder()
								.setSeconds(now)))
				.setCryptoTransfer(op)
				.build();
	}

	private AccountAmount adjustFrom(AccountID account, long amount) {
		return AccountAmount.newBuilder()
				.setAmount(amount)
				.setAccountID(account)
				.build();
	}
}