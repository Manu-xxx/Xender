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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

class ThrottleDefinitionsTest {
	@Test
	void factoryWorks() throws IOException {
		// given:
		var proto = TestUtils.protoDefs("bootstrap/throttles.json");

		// expect:
		Assertions.assertEquals(proto, ThrottleDefinitions.fromProto(proto).toProto());
	}

	@Test
	void totalAllowedGasPerSecFrontend() throws IOException {
		// given:
		var proto = TestUtils.protoDefs("bootstrap/throttles.json");

		// expect:
		Assertions.assertEquals(proto.getTotalAllowedGasPerSecFrontend(), ThrottleDefinitions.fromProto(proto).toProto().getTotalAllowedGasPerSecFrontend());
	}

	@Test
	void totalAllowedGasPerSecConsensus() throws IOException {
		// given:
		var proto = TestUtils.protoDefs("bootstrap/throttles.json");

		// expect:
		Assertions.assertEquals(proto.getTotalAllowedGasPerSecConsensus(), ThrottleDefinitions.fromProto(proto).toProto().getTotalAllowedGasPerSecConsensus());
	}
}
