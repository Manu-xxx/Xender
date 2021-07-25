package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.tokens.utils.GrpcUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;

@ExtendWith(MockitoExtension.class)
class ExplicitOwnersUniqTokenViewTest {
	@Mock
	private GrpcUtils grpcUtils;
	@Mock
	private Iterator<MerkleUniqueTokenId> firstMockRange;
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> nfts;
	@Mock
	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> nftsByType;
	@Mock
	private FCOneToManyRelation<EntityId, MerkleUniqueTokenId> nftsByOwner;

	private ExplicitOwnersUniqTokenView subject;

	@BeforeEach
	void setUp() {
		subject = new ExplicitOwnersUniqTokenView(() -> tokens, () -> nfts, () -> nftsByType, () -> nftsByOwner);
	}

	@Test
	void interpolatesAccountIdForWildcard() {
		setupFirstMockRange();
		subject.setGrpcUtils(grpcUtils);

		given(nftsByOwner.get(someOwnerId, start, end)).willReturn(firstMockRange);
		given(nfts.get(someExplicitNftId)).willReturn(someExplicitNft);
		given(nfts.get(wildcardNftId)).willReturn(wildcardNft);
		given(grpcUtils.reprOf(
				someTokenId.toGrpcTokenId(),
				someSerial,
				someExplicitNft,
				someOwnerId.toGrpcAccountId())).willReturn(mockExplicitInfo);
		given(grpcUtils.reprOf(
				otherTokenId.toGrpcTokenId(),
				wildcardSerial,
				wildcardNft,
				someOwnerId.toGrpcAccountId())).willReturn(mockInterpolatedInfo);

		// when:
		final var actual = subject.ownedAssociations(someOwnerId.toGrpcAccountId(), start, end);

		// then:
		Assertions.assertEquals(List.of(mockExplicitInfo, mockInterpolatedInfo), actual);
	}

	private void setupFirstMockRange() {
		willAnswer(invocationOnMock -> {
			final Consumer<MerkleUniqueTokenId> consumer = invocationOnMock.getArgument(0);
			consumer.accept(someExplicitNftId);
			consumer.accept(wildcardNftId);
			return null;
		}).given(firstMockRange).forEachRemaining(any());
	}

	private final int start = 123;
	private final int end = 456;
	private final long someSerial = 1L;
	private final long wildcardSerial = 2L;
	private final byte[] someMeta = "As you wish...".getBytes(StandardCharsets.UTF_8);
	private final byte[] wildMeta = "...caution to the wind, then!".getBytes(StandardCharsets.UTF_8);
	private final RichInstant someCreationTime = new RichInstant(1_234_567L, 890);
	private final EntityId someTokenId = new EntityId(6, 6, 6);
	private final EntityId otherTokenId = new EntityId(7, 7, 7);
	private final EntityId someOwnerId = new EntityId(1, 2, 3);
	private final MerkleUniqueToken someExplicitNft = new MerkleUniqueToken(someOwnerId, someMeta, someCreationTime);
	private final MerkleUniqueToken wildcardNft = new MerkleUniqueToken(MISSING_ENTITY_ID, wildMeta, someCreationTime);
	private final MerkleUniqueTokenId someExplicitNftId = new MerkleUniqueTokenId(someTokenId, someSerial);
	private final MerkleUniqueTokenId wildcardNftId = new MerkleUniqueTokenId(otherTokenId, wildcardSerial);
	final TokenNftInfo mockExplicitInfo = TokenNftInfo.newBuilder()
			.setNftID(NftID.getDefaultInstance())
			.setAccountID(AccountID.getDefaultInstance())
			.build();
	final TokenNftInfo mockInterpolatedInfo = TokenNftInfo.newBuilder()
			.setNftID(NftID.getDefaultInstance())
			.setAccountID(AccountID.getDefaultInstance())
			.build();
}