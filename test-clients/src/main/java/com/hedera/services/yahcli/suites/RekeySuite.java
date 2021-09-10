package com.hedera.services.yahcli.suites;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.yahcli.suites.Utils.extractAccount;

public class RekeySuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RekeySuite.class);

	private final String account;
	private final String replKeyLoc;
	private final String replTarget;
	private final boolean genNewKey;
	private final Map<String, String> specConfig;

	public RekeySuite(
			Map<String, String> specConfig,
			String account,
			String replKeyLoc,
			boolean genNewKey,
			String replTarget
	) {
		this.specConfig = specConfig;
		this.replKeyLoc = replKeyLoc;
		this.genNewKey = genNewKey;
		this.replTarget = replTarget;
		this.account = extractAccount(account);
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(rekey());
	}

	private HapiApiSpec rekey() {
		final var replKey = "replKey";
		final var newKeyLoc = replTarget.endsWith(".pem")
				? replTarget
				: replTarget.replace(".pem", ".words");
		final var newKeyPass = TxnUtils.randomAlphaNumeric(12);

		return HapiApiSpec.customHapiSpec("rekey" + account)
				.withProperties(specConfig).given(
						genNewKey
								? newKeyNamed(replKey).exportingTo(newKeyLoc, newKeyPass).yahcliLogged()
								: keyFromFile(replKey, replKeyLoc).exportingTo(newKeyLoc, newKeyPass).yahcliLogged()
				).when(
						cryptoUpdate(account)
								.signedBy(DEFAULT_PAYER, replKey)
								.key(replKey)
								.noLogging()
								.yahcliLogging()
				).then(
						withOpContext((spec, opLog) -> {
							if (replTarget.endsWith(".words")) {
								new File(replTarget).delete();
							}
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
