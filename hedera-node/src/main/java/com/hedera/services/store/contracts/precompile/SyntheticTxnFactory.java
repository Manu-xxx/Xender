package com.hedera.services.store.contracts.precompile;

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

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class SyntheticTxnFactory {
	@Inject
	public SyntheticTxnFactory() {
	}

	public TransactionBody.Builder createNonFungibleMint(
			final NftMint nftMint
	) {
		final var builder = TokenMintTransactionBody.newBuilder();

		builder.setToken(nftMint.getTokenType());
		builder.addAllMetadata(nftMint.getMetadata());

		return TransactionBody.newBuilder().setTokenMint(builder);
	}

	public TransactionBody.Builder createCryptoTransfer(
			final List<NftExchange> nftExchanges,
			final List<HbarTransfer> hbarTransfers,
			final List<FungibleTokenTransfer> fungibleTransfers
	) {
		final var builder = CryptoTransferTransactionBody.newBuilder();

		final var hbarBuilder = TransferList.newBuilder();
		for (final var hbarTransfer : hbarTransfers) {
			hbarBuilder.addAccountAmounts(hbarTransfer.senderAdjustment());
			hbarBuilder.addAccountAmounts(hbarTransfer.receiverAdjustment());
		}
		builder.setTransfers(hbarBuilder);

		for (final var nftExchange : nftExchanges) {
			builder.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(nftExchange.getTokenType())
					.addNftTransfers(nftExchange.nftTransfer()));
		}
		for (final var fungibleTransfer : fungibleTransfers) {
			builder.addTokenTransfers(TokenTransferList.newBuilder()
					.setToken(fungibleTransfer.getDenomination())
					.addTransfers(fungibleTransfer.senderAdjustment())
					.addTransfers(fungibleTransfer.receiverAdjustment()));
		}

		return TransactionBody.newBuilder().setCryptoTransfer(builder);
	}

	public TransactionBody.Builder createAssociate(final AssociateToken associateToken) {
		final var builder = TokenAssociateTransactionBody.newBuilder();

		builder.setAccount(associateToken.getAccountID());
		builder.addTokens(associateToken.getTokenID());

		return TransactionBody.newBuilder().setTokenAssociate(builder);
	}

	public TransactionBody.Builder createDissociate(final DissociateToken dissociateToken) {
		final var builder = TokenDissociateTransactionBody.newBuilder();

		builder.setAccount(dissociateToken.getAccountID());
		builder.addTokens(dissociateToken.getTokenID());

		return TransactionBody.newBuilder().setTokenDissociate(builder);
	}

	public static class HbarTransfer {
		protected final long amount;
		protected final AccountID sender;
		protected final AccountID receiver;

		public HbarTransfer(long amount, AccountID sender, AccountID receiver) {
			this.amount = amount;
			this.sender = sender;
			this.receiver = receiver;
		}

		public AccountAmount senderAdjustment() {
			return AccountAmount.newBuilder().setAccountID(sender).setAmount(-amount).build();
		}

		public AccountAmount receiverAdjustment() {
			return AccountAmount.newBuilder().setAccountID(receiver).setAmount(+amount).build();
		}
	}

	public static class FungibleTokenTransfer extends HbarTransfer {
		private final TokenID denomination;

		public FungibleTokenTransfer(long amount, TokenID denomination, AccountID sender, AccountID receiver) {
			super(amount, sender, receiver);
			this.denomination = denomination;
		}

		public TokenID getDenomination() {
			return denomination;
		}
	}

	public static class NftExchange {
		private final long serialNo;
		private final TokenID tokenType;
		private final AccountID sender;
		private final AccountID receiver;

		public NftExchange(long serialNo, TokenID tokenType, AccountID sender, AccountID receiver) {
			this.serialNo = serialNo;
			this.tokenType = tokenType;
			this.sender = sender;
			this.receiver = receiver;
		}

		public NftTransfer nftTransfer() {
			return NftTransfer.newBuilder()
					.setSenderAccountID(sender)
					.setReceiverAccountID(receiver)
					.setSerialNumber(serialNo)
					.build();
		}

		public TokenID getTokenType() {
			return tokenType;
		}
	}

	public static class NftMint {
		private final TokenID tokenType;
		private final List<ByteString> metadata;

		public NftMint(final TokenID tokenType, final List<ByteString> metadata) {
			this.tokenType = tokenType;
			this.metadata = metadata;
		}

		public TokenID getTokenType() {
			return tokenType;
		}

		public List<ByteString> getMetadata() {
			return metadata;
		}
	}

	public static class AssociateToken {
		private final AccountID accountID;
		private final TokenID tokenID;

		public AssociateToken(final AccountID accountID, final TokenID tokenID) {
			this.accountID  =accountID;
			this.tokenID = tokenID;
		}

		public AccountID getAccountID() {
			return accountID;
		}

		public TokenID getTokenID() {
			return tokenID;
		}
	}

	public static class DissociateToken {
		private final AccountID accountID;
		private final TokenID tokenID;

		public DissociateToken(final AccountID accountID, final TokenID tokenID) {
			this.accountID  =accountID;
			this.tokenID = tokenID;
		}

		public AccountID getAccountID() {
			return accountID;
		}

		public TokenID getTokenID() {
			return tokenID;
		}
	}
}
