package com.hedera.services.state.merkle;

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

import com.google.common.base.MoreObjects;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractNaryMerkleInternal;
import com.swirlds.fcqueue.FCQueue;
import com.swirlds.virtualmap.VirtualValue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.hedera.services.state.merkle.SerializationHelper.read;
import static com.hedera.services.state.merkle.SerializationHelper.readInto;
import static com.hedera.services.state.merkle.SerializationHelper.write;

public class MerkleAccount extends AbstractNaryMerkleInternal implements MerkleInternal, VirtualValue {
	private static final Logger log = LogManager.getLogger(MerkleAccount.class);

	static Runnable stackDump = Thread::dumpStack;

	static final FCQueue<ExpirableTxnRecord> IMMUTABLE_EMPTY_FCQ = new FCQueue<>();

	static {
		IMMUTABLE_EMPTY_FCQ.copy();
	}

	static final int RELEASE_090_VERSION = 3;
	static final int MERKLE_VERSION = RELEASE_090_VERSION;

	static final long RUNTIME_CONSTRUCTABLE_ID = 0x950bcf7255691908L;

	static DomainSerdes serdes = new DomainSerdes();


	/* Order of Merkle node children */
	static class ChildIndices {
		static final int STATE = 0;
		static final int RELEASE_090_RECORDS = 1;
		static final int RELEASE_090_ASSOCIATED_TOKENS = 2;
		static final int NUM_090_CHILDREN = 3;
	}

	public MerkleAccount(List<MerkleNode> children, MerkleAccount immutableAccount) {
		super(immutableAccount);
		addDeserializedChildren(children, MERKLE_VERSION);
	}

	public MerkleAccount(List<MerkleNode> children) {
		super(ChildIndices.NUM_090_CHILDREN);
		addDeserializedChildren(children, MERKLE_VERSION);
	}

	public MerkleAccount() {
		addDeserializedChildren(List.of(
				new MerkleAccountState(),
				new FCQueue<ExpirableTxnRecord>(),
				new MerkleAccountTokens()), MERKLE_VERSION);
	}

	/* --- MerkleInternal --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public int getMinimumChildCount(int version) {
		return ChildIndices.NUM_090_CHILDREN;
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleAccount copy() {
		if (isImmutable()) {
			var msg = String.format("Copy called on immutable MerkleAccount by thread '%s'! Payer records mutable? %s",
					Thread.currentThread().getName(),
					records().isImmutable() ? "NO" : "YES");
			log.warn(msg);
			/* Ensure we get this stack trace in case a caller incorrectly suppresses the exception. */
			stackDump.run();
			throw new IllegalStateException("Tried to make a copy of an immutable MerkleAccount!");
		}

		setImmutable(true);
		return new MerkleAccount(List.of(
				state().copy(),
				records().copy(),
				tokens().copy()), this);
	}

	/* ---- Object ---- */
	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || MerkleAccount.class != o.getClass()) {
			return false;
		}
		var that = (MerkleAccount) o;
		return this.state().equals(that.state()) &&
				this.records().equals(that.records()) &&
				this.tokens().equals(that.tokens());
	}

	@Override
	public int hashCode() {
		return Objects.hash(state(), records(), tokens());
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleAccount.class)
				.add("state", state())
				.add("# records", records().size())
				.add("tokens", tokens().readableTokenIds())
				.toString();
	}

	/* ----  Merkle children  ---- */
	public MerkleAccountState state() {
		return getChild(ChildIndices.STATE);
	}

	public FCQueue<ExpirableTxnRecord> records() {
		return getChild(ChildIndices.RELEASE_090_RECORDS);
	}

	public void setRecords(FCQueue<ExpirableTxnRecord> payerRecords) {
		throwIfImmutable("Cannot change this account's transaction records if it's immutable.");
		setChild(ChildIndices.RELEASE_090_RECORDS, payerRecords);
	}

	public MerkleAccountTokens tokens() {
		return getChild(ChildIndices.RELEASE_090_ASSOCIATED_TOKENS);
	}

	public void setTokens(MerkleAccountTokens tokens) {
		throwIfImmutable("Cannot change this account's tokens if it's immutable.");
		setChild(ChildIndices.RELEASE_090_ASSOCIATED_TOKENS, tokens);
	}

	// TODO: get rid of these methods, these concerns belong to MerkleAccountState and should be fetched from there, leaking data
	/* ----  Bean  ---- */

	public long getNftsOwned() { return state().nftsOwned(); }

	public void setNftsOwned(long nftsOwned) {
		throwIfImmutable("Cannot change this account's owned NFTs if it's immutable.");
		state().setNftsOwned(nftsOwned);
	}

	public String getMemo() {
		return state().memo();
	}

	public void setMemo(String memo) {
		throwIfImmutable("Cannot change this account's memo if it's immutable.");
		state().setMemo(memo);
	}

	public boolean isSmartContract() {
		return state().isSmartContract();
	}

	public void setSmartContract(boolean smartContract) {
		throwIfImmutable("Cannot change this account's smart contract if it's immutable.");
		state().setSmartContract(smartContract);
	}

	public long getBalance() {
		return state().balance();
	}

	public void setBalance(long balance) throws NegativeAccountBalanceException {
		if (balance < 0) {
			throw new NegativeAccountBalanceException(String.format("Illegal balance: %d!", balance));
		}
		throwIfImmutable("Cannot change this account's hbar balance if it's immutable.");
		state().setHbarBalance(balance);
	}

	public void setBalanceUnchecked(long balance) {
		if (balance < 0) {
			throw new IllegalArgumentException("Cannot set an ℏ balance to " + balance);
		}
		throwIfImmutable("Cannot change this account's hbar balance if it's immutable.");
		state().setHbarBalance(balance);
	}

	public boolean isReceiverSigRequired() {
		return state().isReceiverSigRequired();
	}

	public void setReceiverSigRequired(boolean receiverSigRequired) {
		throwIfImmutable("Cannot change this account's receiver signature required setting if it's immutable.");
		state().setReceiverSigRequired(receiverSigRequired);
	}

	public JKey getKey() {
		return state().key();
	}

	public void setKey(JKey key) {
		throwIfImmutable("Cannot change this account's key if it's immutable.");
		state().setKey(key);
	}

	public EntityId getProxy() {
		return state().proxy();
	}

	public void setProxy(EntityId proxy) {
		throwIfImmutable("Cannot change this account's proxy if it's immutable.");
		state().setProxy(proxy);
	}

	public long getAutoRenewSecs() {
		return state().autoRenewSecs();
	}

	public void setAutoRenewSecs(long autoRenewSecs) {
		throwIfImmutable("Cannot change this account's auto renewal seconds if it's immutable.");
		state().setAutoRenewSecs(autoRenewSecs);
	}

	public boolean isDeleted() {
		return state().isDeleted();
	}

	public void setDeleted(boolean deleted) {
		throwIfImmutable("Cannot change this account's deleted status if it's immutable.");
		state().setDeleted(deleted);
	}

	public long getExpiry() {
		return state().expiry();
	}

	public void setExpiry(long expiry) {
		throwIfImmutable("Cannot change this account's expiry time if it's immutable.");
		state().setExpiry(expiry);
	}

	/* --- Helpers --- */
	public List<ExpirableTxnRecord> recordList() {
		return new ArrayList<>(records());
	}


	/* --- VirtualValue --- */

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		// Workaround until wire ByteBuffer into general hash() call from platform side.
		state().deserialize(in, version);
		records().deserialize(in, version);
		tokens().deserialize(in, version);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		// Workaround until wire ByteBuffer into general hash() call from platform side.
		state().serialize(out);
		records().serialize(out);
		tokens().serialize(out);
	}

	@Override
	public void serialize(ByteBuffer buffer) throws IOException {
		write(buffer, state());

		FCQueue<ExpirableTxnRecord> records = records();
		buffer.putInt(records.size());
		for (ExpirableTxnRecord record : records) {
			write(buffer, record);
		}

		write(buffer, tokens());
	}

	@Override
	public void deserialize(ByteBuffer buffer, int version) throws IOException {
		readInto(buffer, state());

		FCQueue<ExpirableTxnRecord> records = new FCQueue<>();
		int size = buffer.getInt();
		for (int i = 0; i < size; i++) {
			records.add(read(buffer, ExpirableTxnRecord::new));
		}
		setRecords(records);
		setTokens(read(buffer, MerkleAccountTokens::new));
	}

	@Override
	public VirtualValue asReadOnly() {
		MerkleAccount copy = new MerkleAccount(List.of(
				state().copy(),
				records().copy(),
				tokens().copy()), this);
		copy.setImmutable(true);
		return copy;
	}
}
