package com.hedera.services.yahcli.suites;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

public class SendSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SendSuite.class);

	private final Map<String, String> specConfig;
	private final String beneficiary;
	private final long tinybarsToSend;

	public SendSuite(final Map<String, String> specConfig, final String beneficiary, final long tinybarsToSend) {
		this.specConfig = specConfig;
		this.beneficiary = Utils.extractAccount(beneficiary);
		this.tinybarsToSend = tinybarsToSend;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		List<HapiApiSpec> specToRun = new ArrayList<>();
		return specToRun;
	}

	private HapiApiSpec doSend() {
		return HapiApiSpec.customHapiSpec("DoSend")
				.withProperties(specConfig)
				.given().when().then(
						TxnVerbs.cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, beneficiary, tinybarsToSend))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
