package com.hedera.services.store.tokens;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.fcmap.FCMap;

import java.util.List;
import java.util.function.Supplier;

public class ExplicitOwnersUniqTokenView implements UniqTokenView {
	private final Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts;
	private final Supplier<FCOneToManyRelation<EntityId,MerkleUniqueTokenId>> nftsByType;
	private final Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner;

	public ExplicitOwnersUniqTokenView(
			Supplier<FCMap<MerkleUniqueTokenId, MerkleUniqueToken>> nfts,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByType,
			Supplier<FCOneToManyRelation<EntityId, MerkleUniqueTokenId>> nftsByOwner
	) {
		this.nfts = nfts;
		this.nftsByType = nftsByType;
		this.nftsByOwner = nftsByOwner;
	}

	@Override
	public List<TokenNftInfo> ownedAssociations(AccountID owner, long start, long end) {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public List<TokenNftInfo> typedAssociations(TokenID type, long start, long end) {
		throw new AssertionError("Not implemented!");
	}
}
