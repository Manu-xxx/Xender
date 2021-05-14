package com.hedera.services.bdd.suites.perf;

import com.hedera.services.bdd.suites.HapiApiSuite;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.reduceFeeFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;


/**
 * This is a suite for any reconnect tests with multiple clients that needs to adjust fee schedules
 */

public class AdjustFeeScheduleSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AdjustFeeScheduleSuite.class);

	public AdjustFeeScheduleSuite() {
	}

	public static void main(String... args) {
		new AdjustFeeScheduleSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				updateFeesFor()
		);
	}

	private HapiApiSpec updateFeesFor() {

		return defaultHapiSpec("updateFees")
				.given(
				).when(
				).then(
						reduceFeeFor(CryptoTransfer, 2L, 3L, 3L),
						reduceFeeFor(ConsensusSubmitMessage, 2L, 3L, 3L),
						sleepFor(30000)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

}
