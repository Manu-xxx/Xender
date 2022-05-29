package com.hedera.services.ledger;

import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.setup.Constructables;
import com.hedera.services.setup.InfrastructureBundle;
import com.hedera.services.setup.InfrastructureType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.List;
import java.util.Map;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.setup.InfrastructureManager.loadOrCreateBundle;
import static com.hedera.services.setup.InfrastructureType.ACCOUNTS_LEDGER;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 3, time = 30)
public class SimpleTransfersBench {
	private static final int NUM_NODES = 39;
	private static final int FIRST_NODE_I = 3;
	private static final int FIRST_USER_I = 1001;
	private static final int ADDEND = 17;
	private static final int MULTIPLIER = 31;
	private static final AccountID FUNDING_ID = AccountID.newBuilder().setAccountNum(98).build();

	@Param("100000")
	int userAccounts;
	@Param("10000")
	int transfersPerRound;
	private int i;
	private int n;
	private AccountID[] ids;
	private InfrastructureBundle bundle;
	private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

	// --- Fixtures ---
	@Setup(Level.Trial)
	public void setupInfrastructure() {
		Constructables.registerForAccounts();
		Constructables.registerForMerkleMap();
		bundle = loadOrCreateBundle(activeConfig(), requiredInfra());
		ledger = bundle.get(ACCOUNTS_LEDGER);
		ids = new AccountID[userAccounts + 1001];
		for (int j = 1; j < userAccounts + 1001; j++) {
			ids[j] = AccountID.newBuilder().setAccountNum(j).build();
		}
		i = n = 0;
	}

	@Setup(Level.Invocation)
	public void simulateRoundBoundary() {
		if (n > 0 && n % transfersPerRound == 0) {
			bundle.newRound();
		}
	}

	// --- Benchmarks ---
	@Benchmark
	public void simpleTransfers() {
		i = i * MULTIPLIER + ADDEND;
		final var nodeId = ids[FIRST_NODE_I + Math.floorMod(i, NUM_NODES)];
		i = i * MULTIPLIER + ADDEND;
		final var senderId = ids[FIRST_USER_I + Math.floorMod(i, userAccounts)];
		i = i * MULTIPLIER + ADDEND;
		final var receiverId = ids[FIRST_USER_I + Math.floorMod(i, userAccounts)];

		ledger.begin();
		ledger.set(FUNDING_ID, BALANCE, (long) ledger.get(FUNDING_ID, BALANCE) + 69_000);
		ledger.set(nodeId, BALANCE, (long) ledger.get(nodeId, BALANCE) + 420);
		ledger.set(senderId, BALANCE, (long) ledger.get(senderId, BALANCE) - 69_421);
		ledger.set(receiverId, BALANCE, (long) ledger.get(receiverId, BALANCE) + 1);
		ledger.commit();

		n++;
	}

	// --- Helpers ---
	private Map<String, Object> activeConfig() {
		return Map.of("userAccounts", userAccounts);
	}

	private List<InfrastructureType> requiredInfra() {
		return List.of(ACCOUNTS_LEDGER);
	}
}
