package com.hedera.services.state.submerkle;

import com.swirlds.common.constructable.ConstructableRegistryException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.hedera.test.utils.TxnUtils.assertSerdeWorks;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordSerdeTest.randomAddress;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordSerdeTest.randomBytes;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordSerdeTest.randomEntityId;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordSerdeTest.randomEvmWord;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordSerdeTest.randomStateChangePair;
import static com.hedera.services.state.submerkle.ExpirableTxnRecordSerdeTest.registerRecordConstructables;
import static com.hedera.services.state.submerkle.SolidityFnResult.RELEASE_0240_VERSION;

class SolidityFnResultSerdeTest {
	@Test
	void serdeWorksWithNoStateChanges() throws IOException, ConstructableRegistryException {
		registerRecordConstructables();

		final var subject = new SolidityFnResult(
				randomEntityId(),
				randomBytes(128),
				"Mind the vase now!",
				randomBytes(32),
				123321,
				List.of(),
				List.of(),
				randomBytes(20),
				Collections.emptyMap());

		assertSerdeWorks(subject, SolidityFnResult::new, RELEASE_0240_VERSION);
	}

	@Test
	void serdeWorksWithStateChanges() throws IOException, ConstructableRegistryException {
		registerRecordConstructables();

		for (int i = 0; i < 10; i++) {
			final var subject = new SolidityFnResult(
					randomEntityId(),
					randomBytes(128),
					"Mind the vase now!",
					randomBytes(32),
					123321,
					List.of(),
					List.of(),
					randomBytes(20),
					randomStateChanges(5, 10));

			assertSerdeWorks(subject, SolidityFnResult::new, RELEASE_0240_VERSION);
		}
	}

	private static Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> randomStateChanges(int n, final int changesPerAddress) {
		final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> ans = new TreeMap<>();
		while (n-- > 0) {
			final var address = randomAddress();
			final Map<Bytes, Pair<Bytes, Bytes>> changes = new TreeMap<>();
			for (int i = 0; i < changesPerAddress; i++)	{
				changes.put(randomEvmWord(), randomStateChangePair());
			}
			ans.put(address, changes);
		}
		return ans;
	}
}