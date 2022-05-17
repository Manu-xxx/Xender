package com.hedera.services.ledger.backing;

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

import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;
import com.swirlds.virtualmap.VirtualMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class BackingNftsTest {
	private final NftId aNftId = new NftId(0, 0, 3, 4);
	private final NftId bNftId = new NftId(0, 0, 4, 5);
	private final NftId cNftId = new NftId(0, 0, 5, 6);
	private final UniqueTokenKey aKey = new UniqueTokenKey(3, 4);
	private final UniqueTokenKey bKey = new UniqueTokenKey(4, 5);
	private final UniqueTokenValue aValue = new UniqueTokenValue(
			3,
			MISSING_ENTITY_ID.num(),
			"abcdefgh".getBytes(),
			new RichInstant(1_234_567L, 1));
	private final UniqueTokenValue theToken = new UniqueTokenValue(
			MISSING_ENTITY_ID.num(),
			MISSING_ENTITY_ID.num(),
			"HI".getBytes(StandardCharsets.UTF_8),
			MISSING_INSTANT);
	private final UniqueTokenValue notTheToken = new UniqueTokenValue(
			MISSING_ENTITY_ID.num(),
			MISSING_ENTITY_ID.num(),
			"IH".getBytes(StandardCharsets.UTF_8),
			MISSING_INSTANT);

	private VirtualMap<UniqueTokenKey, UniqueTokenValue> delegate;

	private BackingNfts subject;

	@BeforeEach
	void setUp() {
		delegate = new VirtualMap<>();

		delegate.put(aKey, theToken);
		delegate.put(bKey, notTheToken);

		subject = new BackingNfts(() -> delegate);
	}

	@Test
	void doSupportGettingIdSet() {
		// when:
		subject = new BackingNfts(() -> delegate);

		// expect:
		assertNotNull(subject.idSet());
		assertEquals(2, subject.size());
	}

	@Test
	void containsWorks() {
		// expect:
		assertTrue(subject.contains(aNftId));
		assertTrue(subject.contains(bNftId));
		assertFalse(subject.contains(cNftId));
	}

	@Test
	void getRefDelegatesToGetForModify() {
		// when:
		final var mutable = subject.getRef(aNftId);

		// then:
		assertEquals(theToken, mutable);
		assertFalse(mutable.isImmutable());
	}

	@Test
	void getImmutableRefDelegatesToGet() {
		// when:
		final var immutable = subject.getImmutableRef(aNftId);

		// then:
		assertEquals(theToken, immutable);
	}

	@Test
	void putWorks() {
		// when:
		subject.put(aNftId, aValue);
		subject.put(cNftId, aValue);

		// then:
		assertEquals(aValue, subject.getImmutableRef(cNftId));
	}

	@Test
	void removeWorks() {
		// when:
		subject.remove(aNftId);

		// then:
		assertFalse(subject.contains(aNftId));
	}

	@Test
	void sizePropagatesCallToDelegate() {
		assertEquals(delegate.size(), subject.size());
	}
}
