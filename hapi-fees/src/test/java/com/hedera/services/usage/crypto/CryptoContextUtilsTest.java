package com.hedera.services.usage.crypto;

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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptoContextUtilsTest {
	@Test
	void getsNewSerials() {
		Map<AllowanceId, AllowanceDetails> newMap = new HashMap<>();
		Map<AllowanceId, AllowanceDetails> existingMap = new HashMap<>();

		newMap.put(new AllowanceId(1L, 2L),
				new AllowanceDetails(true, List.of(3L, 4L)));
		newMap.put(new AllowanceId(1L, 3L),
				new AllowanceDetails(false, List.of(1L, 2L, 3L, 100L)));

		existingMap.put(new AllowanceId(1L, 3L),
				new AllowanceDetails(false, List.of(1L)));

		assertEquals(6, CryptoContextUtils.getNewSerials(newMap));
	}

	@Test
	void getsChangedKeys() {
		Map<Long, Long> newMap = new HashMap<>();
		Map<Long, Long> existingMap = new HashMap<>();

		newMap.put(1L, 2L);
		newMap.put(3L, 2L);
		newMap.put(4L, 2L);

		existingMap.put(1L, 2L);
		existingMap.put(4L, 2L);
		existingMap.put(5L, 2L);

		assertEquals(1, CryptoContextUtils.getChangedCryptoKeys(newMap.keySet(), existingMap.keySet()));
	}

	@Test
	void getsChangedTokenKeys() {
		Map<AllowanceId, Long> newMap = new HashMap<>();
		Map<AllowanceId, Long> existingMap = new HashMap<>();

		newMap.put(new AllowanceId(1L, 2L), 2L);
		newMap.put(new AllowanceId(2L, 2L), 2L);
		newMap.put(new AllowanceId(3L, 2L), 2L);

		existingMap.put(new AllowanceId(1L, 2L), 2L);
		existingMap.put(new AllowanceId(4L, 2L), 2L);
		existingMap.put(new AllowanceId(3L, 5L), 2L);

		assertEquals(2, CryptoContextUtils.getChangedTokenKeys(newMap.keySet(), existingMap.keySet()));
	}
}
