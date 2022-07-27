package com.hedera.services.ledger.properties;

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

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.UniqueTokenValue;
import org.junit.jupiter.api.Test;

import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static org.junit.jupiter.api.Assertions.assertEquals;

class NftPropertyTest {
	private final byte[] aMeta = "abcdefgh".getBytes();
	private final byte[] bMeta = "hgfedcba".getBytes();
	private final EntityId aEntity = new EntityId(0, 0, 3);
	private final EntityId bEntity = new EntityId(0, 0, 4);
	private final RichInstant aInstant = new RichInstant(1_234_567L, 1);
	private final RichInstant bInstant = new RichInstant(1_234_567L, 2);

    @Test
    void gettersWork() {
        // given:
        final var aSubject = new UniqueTokenValue(aEntity.num(), 0, aMeta, aInstant);

		// expect:
		assertEquals(aEntity, NftProperty.OWNER.getter().apply(aSubject));
		assertEquals(aMeta, NftProperty.METADATA.getter().apply(aSubject));
		assertEquals(aSubject.getPackedCreationTime(), NftProperty.CREATION_TIME.getter().apply(aSubject));
	}

    @Test
    void setterWorks() {
        final var aSubject = new UniqueTokenValue(aEntity.num(), 0, aMeta, aInstant);
        final var bSubject = new UniqueTokenValue(bEntity.num(), 0, bMeta, bInstant);

		NftProperty.OWNER.setter().accept(aSubject, bEntity);
		NftProperty.CREATION_TIME.setter().accept(aSubject, packedTime(bInstant.getSeconds(), bInstant.getNanos()));
		NftProperty.METADATA.setter().accept(aSubject, bMeta);

		// expect:
		assertEquals(bSubject, aSubject);
	}
}
