package com.hedera.services.throttling;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.utils.TxnAccessor;
import com.hederahashgraph.api.proto.java.Query;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TimedFunctionalityThrottlingTest {

	@Mock
	Query query;

	@Test
	void defaultImplsAsExpected() {
		// setup:
		final var accessor = mock(TxnAccessor.class);

		// given:
		final var subject = mock(TimedFunctionalityThrottling.class);
		// and:
		Mockito.doCallRealMethod().when(subject).shouldThrottleQuery(FileGetInfo, query);
		Mockito.doCallRealMethod().when(subject).shouldThrottleTxn(accessor);

		// when:
		subject.shouldThrottleQuery(FileGetInfo, query);
		subject.shouldThrottleTxn(accessor);

		// then:
		verify(subject).shouldThrottleQuery(eq(FileGetInfo), any());
		verify(subject).shouldThrottleTxn(eq(accessor), any());
	}
}
