package com.hedera.services.bdd.suites.perf.file;


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
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.LongFunction;

import static com.hedera.services.bdd.spec.HapiSpecSetup.getDefaultNodeProps;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.BYTES_4K;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.mgmtOfIntProp;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.stdMgmtOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.concurrent.TimeUnit.MINUTES;

public class FileExpansionLoadProvider extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FileExpansionLoadProvider.class);

	private static final String MAX_FILE_SIZE_KB_PROP = "files.maxSizeKb";
	private static final String DEFAULT_MAX_FILE_SIZE_KB = getDefaultNodeProps().get(MAX_FILE_SIZE_KB_PROP);
	/* Useful for manipulating the # of FileCreates vs # of FileAppends */
	private static final String OVERRIDE_MAX_FILE_SIZE_KB = "512";

	private static final int DEFAULT_OPS_PER_SEC = 5;
	private static final int DEFAULT_NUM_FILES_BEING_EXPANDED = 1;

	private static final byte[] DATA_CHUNK = TxnUtils.randomUtf8Bytes(BYTES_4K / 4 * 5);

	private AtomicLong duration = new AtomicLong(5);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(DEFAULT_OPS_PER_SEC);
	private AtomicInteger numActiveTargets = new AtomicInteger(DEFAULT_NUM_FILES_BEING_EXPANDED);

	public static void main(String... args) {
		new FileExpansionLoadProvider().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						runFileExpansions(),
				}
		);
	}

	private HapiApiSpec runFileExpansions() {
		return HapiApiSpec.defaultHapiSpec("RunFileExpansions")
				.given(
						overriding(MAX_FILE_SIZE_KB_PROP, OVERRIDE_MAX_FILE_SIZE_KB),
						stdMgmtOf(duration, unit, maxOpsPerSec),
						mgmtOfIntProp(numActiveTargets, "numActiveTargets")
				).when(
						runWithProvider(fileExpansionsFactory())
								.lasting(duration::get, unit::get)
								.maxOpsPerSec(maxOpsPerSec::get)
				).then(
						overriding(MAX_FILE_SIZE_KB_PROP, DEFAULT_MAX_FILE_SIZE_KB)
				);
	}

	private Function<HapiApiSpec, OpProvider> fileExpansionsFactory() {
		final SplittableRandom r = new SplittableRandom();
		final Set<String> usableTargets = ConcurrentHashMap.newKeySet();
		final LongFunction<String> targetNameFn = i -> "expandingFile" + i;
		final AtomicInteger nextTargetNum = new AtomicInteger(numActiveTargets.get());
		final var key = "multi";
		final var waclShape = KeyShape.listOf(SIMPLE, threshOf(1, 3), listOf(2));

		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				final List<HapiSpecOperation> ops = new ArrayList<>();
				ops.add(newKeyNamed(key).shape(waclShape));
				for (int i = 0, n = numActiveTargets.get(); i < n; i++) {
					ops.add(fileCreate(targetNameFn.apply(i))
							.key(key)
							.noLogging()
							.contents(DATA_CHUNK)
							.payingWith(GENESIS));
				}
				return ops;
			}

			@Override
			public Optional<HapiSpecOperation> get() {
				HapiSpecOperation op;
				if (usableTargets.size() < numActiveTargets.get()) {
					final var name = targetNameFn.apply(nextTargetNum.getAndIncrement());
					op = fileCreate(name)
							.noLogging()
							.key(key)
							.contents(DATA_CHUNK)
							.payingWith(GENESIS)
							.deferStatusResolution()
							.exposingNumTo(num -> {
								usableTargets.add(name);
							});
				} else {
					final var skips  = r.nextInt(usableTargets.size());
					final var iter = usableTargets.iterator();
					try {
						for (int i = 0; i < skips; i++) {
							iter.next();
						}
						final var target = iter.next();
						op = fileAppend(target)
								.noLogging()
								.deferStatusResolution()
								.payingWith(GENESIS)
								.content(DATA_CHUNK)
								.hasKnownStatusFrom(MAX_FILE_SIZE_EXCEEDED, SUCCESS)
								.alertingPost(code -> {
									if (code == MAX_FILE_SIZE_EXCEEDED)	{
										log.info("File {} reached max size, no longer in rotation", target);
										usableTargets.remove(target);
									}
								});
					} catch (Exception ignore) {
						op = noOp();
					}
				}
				return Optional.of(op);
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
