package com.hedera.services.bdd.spec.utilops;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;

import static com.hedera.services.bdd.spec.utilops.UtilStateChange.initializeEthereumAccountForSpec;
import static com.hedera.services.bdd.spec.utilops.UtilStateChange.isEthereumAccountCreatedForSpec;
import static com.hedera.services.bdd.suites.HapiApiSuite.ETH_SUFFIX;

public class CustomSpecAssert extends UtilOp {
	static final Logger log = LogManager.getLogger(CustomSpecAssert.class);

	public static void allRunFor(final HapiApiSpec spec, final List<HapiSpecOperation> ops) {
		if(spec.getSuitePrefix().endsWith(ETH_SUFFIX)) {
			if(!isEthereumAccountCreatedForSpec(spec.getName())) {
				initializeEthereumAccountForSpec(spec);
			}

			executeEthereumOps(spec, ops);
		} else {
			executeHederaOps(spec, ops);
		}
	}

	private static void executeHederaOps(final HapiApiSpec spec, final List<HapiSpecOperation> ops) {
		for (final HapiSpecOperation op : ops) {
			handleExec(spec, op);
		}
	}

	private static void executeEthereumOps(final HapiApiSpec spec, final List<HapiSpecOperation> ops) {
		for (final HapiSpecOperation op : ops) {
			final var convertedOp = UtilVerbs.convertHapiCallToEthereumCall(op);
			handleExec(spec, convertedOp);
		}
	}

	private static void handleExec(final HapiApiSpec spec, final HapiSpecOperation op) {
		Optional<Throwable>	error = op.execFor(spec);
		if (error.isPresent()) {
			log.error("Operation '" + op + "' :: " + error.get().getMessage());
			throw new IllegalStateException(error.get());
		}
	}

	public static void allRunFor(HapiApiSpec spec, HapiSpecOperation... ops) {
		allRunFor(spec, List.of(ops));
	}

	@FunctionalInterface
	public interface ThrowingConsumer {
		void assertFor(HapiApiSpec spec, Logger assertLog) throws Throwable;
	}

	private final ThrowingConsumer custom;

	public CustomSpecAssert(ThrowingConsumer custom) {
		this.custom = custom;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		custom.assertFor(spec, log);
		return false;
	}

	@Override
	public String toString() {
		return "CustomSpecAssert";
	}
}
