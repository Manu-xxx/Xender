package com.hedera.services.sysfiles.domain.throttling;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.hedera.services.TestUtils;
import com.hedera.services.throttles.ConcurrentThrottleTestHelper;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ThrottleBucketTest {
	@Test
	void beanMethodsWork() {
		final var subject = new ThrottleBucket();

		subject.setBurstPeriod(123);
		subject.setBurstPeriodMs(123L);
		subject.setName("Thom");

		assertEquals(123, subject.getBurstPeriod());
		assertEquals(123L, subject.getBurstPeriodMs());
		assertEquals("Thom", subject.getName());
	}

	@Test
	void factoryWorks() throws IOException {
		final var proto = TestUtils.protoDefs("bootstrap/throttles.json");

		final var bucketA = proto.getThrottleBuckets(0);

		assertEquals(bucketA, ThrottleBucket.fromProto(bucketA).toProto());
	}

	@ParameterizedTest
	@CsvSource({"2, bootstrap/insufficient-capacity-throttles.json", "24, bootstrap/overdone-throttles.json"})
	void failsWhenConstructingThrottlesThatNeverPermitAnOperationAtNodeLevel(final int n, final String string)
			throws IOException {
		final var defs = TestUtils.pojoDefs(string);
		final var subject = defs.getBuckets().get(0);

		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(n));
	}

	@Test
	void failsWhenConstructingThrottlesWithZeroGroups() {
		Assertions.assertThrows(IllegalStateException.class, () -> new ThrottleBucket().asThrottleMapping(1));
	}

	@Test
	void failsWhenConstructingThrottlesWithZeroOpsPerSecForAGroup() throws IOException {
		final var n = 1;
		final var defs = TestUtils.pojoDefs("bootstrap/undersupplied-throttles.json");
		final var subject = defs.getBuckets().get(0);

		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(n));
	}

	@Test
	void constructsExpectedABucketMappingForGlobalThrottle() throws IOException {
		final var defs = TestUtils.pojoDefs("bootstrap/throttles.json");
		final var subject = defs.getBuckets().get(0);

		/* Bucket A includes groups with opsPerSec of 12, 3000, and 10_000 so the
		logical operations are, respectively, 30_000 / 12 = 2500, 30_000 / 3_000 = 10,
		and 30_000 / 10_000 = 3. */
		final var expectedThrottle = DeterministicThrottle.withTpsAndBurstPeriod(30_000, 2);
		final var expectedReqs = List.of(
				Pair.of(HederaFunctionality.CryptoTransfer, 3),
				Pair.of(HederaFunctionality.CryptoCreate, 3),
				Pair.of(ContractCall, 2500),
				Pair.of(HederaFunctionality.TokenMint, 10));

		final var mapping = subject.asThrottleMapping(1);
		final var actualThrottle = mapping.getLeft();
		final var actualReqs = mapping.getRight();

		assertEquals(expectedThrottle, actualThrottle);
		assertEquals(expectedReqs, actualReqs);
	}

	@Test
	void constructsExpectedABucketMappingEvenWithRepetitions() throws IOException {
		final var defs = TestUtils.pojoDefs("bootstrap/throttles-repeating.json");
		final var subject = defs.getBuckets().get(0);

		/* Bucket A includes groups with opsPerSec of 12, 3000, and 10_000 so the
		logical operations are, respectively, 30_000 / 12 = 2500, 30_000 / 3_000 = 10,
		and 30_000 / 10_000 = 3. */
		final var expectedThrottle = DeterministicThrottle.withTpsAndBurstPeriod(30_000, 2);
		final var expectedReqs = List.of(
				Pair.of(HederaFunctionality.CryptoTransfer, 3),
				Pair.of(HederaFunctionality.CryptoCreate, 3),
				Pair.of(ContractCall, 2500),
				Pair.of(HederaFunctionality.TokenMint, 10));

		final var mapping = subject.asThrottleMapping(1);
		final var actualThrottle = mapping.getLeft();
		final var actualReqs = mapping.getRight();

		assertEquals(expectedThrottle, actualThrottle);
		assertEquals(expectedReqs, actualReqs);
	}

	@Test
	void constructsExpectedABucketMappingForNetworkWith24Nodes() throws IOException {
		final var n = 24;
		final var defs = TestUtils.pojoDefs("bootstrap/throttles.json");

		final var subject = defs.getBuckets().get(0);
		final var expectedThrottle = DeterministicThrottle.withMtpsAndBurstPeriod((30_000 * 1_000) / n, 2);
		final var expectedReqs = List.of(
				Pair.of(HederaFunctionality.CryptoTransfer, 3),
				Pair.of(HederaFunctionality.CryptoCreate, 3),
				Pair.of(ContractCall, 2500),
				Pair.of(HederaFunctionality.TokenMint, 10));

		final var mapping = subject.asThrottleMapping(n);
		final var actualThrottle = mapping.getLeft();
		final var actualReqs = mapping.getRight();

		assertEquals(expectedThrottle, actualThrottle);
		assertEquals(expectedReqs, actualReqs);
	}

	@Test
	void constructedThrottleWorksAsExpected() throws InterruptedException, IOException {
		final var defs = TestUtils.pojoDefs("bootstrap/throttles.json");

		final var subject = defs.getBuckets().get(0);
		final var n = 14;
		final var expectedXferTps = (1.0 * subject.getThrottleGroups().get(0).getOpsPerSec()) / n;
		final var mapping = subject.asThrottleMapping(n);
		final var throttle = mapping.getLeft();
		final var opsForXfer = opsForFunction(mapping.getRight(), CryptoTransfer);
		throttle.resetUsageTo(new DeterministicThrottle.UsageSnapshot(
				throttle.capacity() - DeterministicThrottle.capacityRequiredFor(opsForXfer),
				null));

		final var helper = new ConcurrentThrottleTestHelper(3, 10, opsForXfer);
		helper.runWith(throttle);

		helper.assertTolerableTps(expectedXferTps, 1.00, opsForXfer);
	}

	private int opsForFunction(final List<Pair<HederaFunctionality, Integer>> source,
							   final HederaFunctionality function) {
		for (var pair : source) {
			if (pair.getLeft() == function) {
				return pair.getRight();
			}
		}
		Assertions.fail("Function " + function + " was missing!");
		return 0;
	}

	@ParameterizedTest
	@ValueSource(strings = {"bootstrap/never-true-throttles.json",
			"bootstrap/overflow-throttles.json",
			"bootstrap/repeated-op-throttles.json"})
	void throwOnBucketWithSmallCapacityOrOverflowingLogicalOpsOrRepeatedOp(final String string) throws IOException {
		final var defs = TestUtils.pojoDefs(string);

		final var subject = defs.getBuckets().get(0);

		Assertions.assertThrows(IllegalStateException.class, () -> subject.asThrottleMapping(1));
	}
}
