package com.hedera.services.store.tokens.views;

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

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.store.tokens.views.UniqTokenViewsManager.TargetFcotmr.NFTS_BY_OWNER;
import static com.hedera.services.store.tokens.views.UniqTokenViewsManager.TargetFcotmr.NFTS_BY_TYPE;
import static com.hedera.services.store.tokens.views.UniqTokenViewsManager.TargetFcotmr.TREASURY_NFTS_BY_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class UniqTokenViewsManagerTest {
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts;
	@Mock
	private FCOneToManyRelation<Integer, Long> nftsByType;
	@Mock
	private FCOneToManyRelation<Integer, Long> nftsByOwner;
	@Mock
	private FCOneToManyRelation<Integer, Long> treasuryNftsByType;

	private UniqTokenViewsManager subject;

	@Test
	void beginWorks() {
		setupTreasuryTrackingSubject();

		// when:
		subject.begin();

		// then:
		assertTrue(subject.isInTransaction());
		// and expect:
		assertThrows(IllegalStateException.class, subject::begin);
	}

	@Test
	void rollbackWorks() {
		setupTreasuryTrackingSubject();
		// and:
		subject.getChangesInTxn().add(change(NFTS_BY_OWNER, 1, 2L, true));

		// expect:
		assertThrows(IllegalStateException.class, subject::rollback);

		// when:
		subject.begin();
		// and:
		subject.rollback();

		// then:
		assertFalse(subject.isInTransaction());
		assertTrue(subject.getChangesInTxn().isEmpty());
	}

	@Test
	void commitWorksWithTreasuryTracking() {
		setupTreasuryTrackingSubject();

		// expect:
		assertThrows(IllegalStateException.class, subject::commit);

		// when:
		subject.begin();
		// and:
		subject.getChangesInTxn().add(change(NFTS_BY_TYPE, 1, 2L, true));
		subject.getChangesInTxn().add(change(NFTS_BY_OWNER, 2, 3L, true));
		subject.getChangesInTxn().add(change(TREASURY_NFTS_BY_TYPE, 3, 4L, true));
		subject.getChangesInTxn().add(change(TREASURY_NFTS_BY_TYPE, 4, 5L, false));
		// and:
		subject.commit();

		// then:
		verify(nftsByType).associate(1, 2L);
		verify(nftsByOwner).associate(2, 3L);
		verify(treasuryNftsByType).associate(3, 4L);
		verify(treasuryNftsByType).disassociate(4, 5L);
		// and:
		assertFalse(subject.isInTransaction());
		assertTrue(subject.getChangesInTxn().isEmpty());
	}

	@Test
	void treasuryExitWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.treasuryExitNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryExitWorksWithExplicitOwnersInTxn() {
		setupNonTreasuryTrackingSubject();

		// given:
		subject.begin();

		// when:
		subject.treasuryExitNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner, never()).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner, never()).associate(secondOwner.identityCode(), aOneNftId.identityCode());

		// and when:
		subject.commit();

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryExitWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.treasuryExitNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(treasuryNftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryReturnWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.treasuryReturnNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryReturnWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.treasuryReturnNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryReturnWorksWithTreasuryWildcardsInTxn() {
		setupTreasuryTrackingSubject();

		// given:
		subject.begin();

		// when:
		subject.treasuryReturnNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner, never()).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType, never()).associate(aTokenId.identityCode(), aOneNftId.identityCode());

		// and when:
		subject.commit();

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void exchangeWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.exchangeNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void exchangeWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.exchangeNotice(aOneNftId, firstOwner, secondOwner);

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void exchangeWorksWithTreasuryWildcardsViaTxn() {
		setupTreasuryTrackingSubject();

		// given:
		subject.begin();

		// when:
		subject.exchangeNotice(aOneNftId, firstOwner, secondOwner);
		// then:
		verify(nftsByOwner, never()).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner, never()).associate(secondOwner.identityCode(), aOneNftId.identityCode());

		// and when:
		subject.commit();

		// then:
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(secondOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void burnWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.burnNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void burnWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.burnNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void mintWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.mintNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(firstOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void mintWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.mintNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void wipeWorksWithExplicitOwners() {
		setupNonTreasuryTrackingSubject();

		// when:
		subject.wipeNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void wipeWorksWithTreasuryWildcards() {
		setupTreasuryTrackingSubject();

		// when:
		subject.wipeNotice(aOneNftId, firstOwner);

		// then:
		verify(nftsByType).disassociate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).disassociate(firstOwner.identityCode(), aOneNftId.identityCode());
	}

	@Test
	void treasuryTrackingSafelyRebuildsAndDifferentiatesTreasuryOwned() {
		setupTreasuryTrackingSubject();

		// expect:
		assertTrue(subject.isUsingTreasuryWildcards());

		// and:
		givenWellKnownNfts();
		// and:
		given(tokens.get(aTokenId.asMerkle())).willReturn(aToken);

		// when:
		subject.rebuildNotice(tokens, nfts);

		// then:
		verify(nftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(treasuryNftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		// and:
		verify(nftsByType).associate(bTokenId.identityCode(), bOneNftId.identityCode());
		verify(nftsByOwner).associate(firstOwner.identityCode(), bOneNftId.identityCode());
		// and:
		verifyNoMoreInteractions(nftsByType);
		verifyNoMoreInteractions(treasuryNftsByType);
	}

	@Test
	void nonTreasuryTrackingRebuildsEverythingExplicitly() {
		setupNonTreasuryTrackingSubject();

		// expect:
		assertFalse(subject.isUsingTreasuryWildcards());

		// and:
		givenWellKnownNfts();
		// and:
		given(tokens.get(aTokenId.asMerkle())).willReturn(aToken);

		// when:
		subject.rebuildNotice(tokens, nfts);

		// then:
		verify(nftsByType).associate(aTokenId.identityCode(), aOneNftId.identityCode());
		verify(nftsByOwner).associate(firstOwner.identityCode(), aOneNftId.identityCode());
		// and:
		verify(nftsByType).associate(bTokenId.identityCode(), bOneNftId.identityCode());
		verify(nftsByOwner).associate(firstOwner.identityCode(), bOneNftId.identityCode());
		// and:
		verifyNoMoreInteractions(nftsByType);
		verifyNoMoreInteractions(nftsByOwner);
	}

	@Test
	void toStringAsExpected() {
		// setup:
		final var desired = "PendingChange{targetFcotmr=NFTS_BY_TYPE, keyCode=1, valueCode=2, associate=true}";

		// given:
		final var c = change(NFTS_BY_TYPE, 1, 2L, true);

		// expect:
		assertEquals(desired, c.toString());
	}


	private void givenWellKnownNfts() {
		willAnswer(invocationOnMock -> {
			final BiConsumer<MerkleUniqueTokenId, MerkleUniqueToken> consumer = invocationOnMock.getArgument(0);
			consumer.accept(aOneNftId, firstOwnedANft);
			consumer.accept(bOneNftId, firstOwnedBNft);
			consumer.accept(missingTokenNftId, tokenDeletedNft);
			return null;
		}).given(nfts).forEach(any());
	}

	private void setupTreasuryTrackingSubject() {
		subject = new UniqTokenViewsManager(() -> nftsByType, () -> nftsByOwner, () -> treasuryNftsByType);
	}

	private void setupNonTreasuryTrackingSubject() {
		subject = new UniqTokenViewsManager(() -> nftsByType, () -> nftsByOwner);
	}

	private UniqTokenViewsManager.PendingChange change(
			UniqTokenViewsManager.TargetFcotmr target,
			int keyCode,
			long valueCode,
			boolean associate
	) {
		return new UniqTokenViewsManager.PendingChange(target, keyCode, valueCode, associate);
	}

	private final EntityId firstOwner = new EntityId(0, 0, 3);
	private final EntityId secondOwner = new EntityId(0, 0, 4);
	private final EntityId aTokenId = new EntityId(0, 0, 5);
	private final EntityId bTokenId = new EntityId(0, 0, 6);
	private final EntityId cTokenId = new EntityId(0, 0, 7);
	private final MerkleUniqueTokenId aOneNftId = new MerkleUniqueTokenId(aTokenId, 1L);
	private final MerkleUniqueTokenId bOneNftId = new MerkleUniqueTokenId(bTokenId, 1L);
	private final MerkleUniqueTokenId missingTokenNftId = new MerkleUniqueTokenId(cTokenId, 666L);
	private final MerkleToken aToken = new MerkleToken(
			1_234_567L, 1_234L, 1,
			"Hi", "EVERYBODY",
			false, true, firstOwner);
	private final MerkleToken bToken = new MerkleToken(
			1_234_567L, 1_234L, 1,
			"Bye", "YOU",
			false, true, secondOwner);
	private byte[] someMeta = "SOMETHING".getBytes(StandardCharsets.UTF_8);
	private byte[] otherMeta = "ELSE".getBytes(StandardCharsets.UTF_8);
	private final MerkleUniqueToken firstOwnedANft = new MerkleUniqueToken(MISSING_ENTITY_ID, someMeta, MISSING_INSTANT);
	private final MerkleUniqueToken firstOwnedBNft = new MerkleUniqueToken(firstOwner, otherMeta, MISSING_INSTANT);
	private final MerkleUniqueToken tokenDeletedNft = new MerkleUniqueToken(MISSING_ENTITY_ID, otherMeta, MISSING_INSTANT);
}
