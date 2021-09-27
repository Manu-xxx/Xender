package com.hedera.services.store.models;

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
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.internals.CopyOnWriteIds;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setMaxAutomaticAssociationsTo;
import static com.hedera.services.utils.MiscUtils.asFcKeyUnchecked;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;

/**
 * Encapsulates the state and operations of a Hedera account.
 * <p>
 * Operations are validated, and throw a {@link com.hedera.services.exceptions.InvalidTransactionException}
 * with response code capturing the failure when one occurs.
 *
 * <b>NOTE:</b> This implementation is incomplete, and includes
 * only the API needed to support the Hedera Token Service. The
 * memo field, for example, is not yet present.
 */
public class Account {
	private final Id id;

	private long expiry;
	private long balance;
	private boolean deleted = false;
	private boolean isSmartContract = false;
	private boolean isReceiverSigRequired = false;
	private CopyOnWriteIds associatedTokens;
	private long ownedNfts;
	private long autoRenewSecs;
	private JKey key;
	private String memo = "";
	private Id proxy;
	private int autoAssociationMetadata;
	private boolean isNew;

	public Account(Id id) {
		this.id = id;
	}

	/**
	 * Creates a new {@link Account} instance from the given gRPC.
	 *
	 * @param accountId
	 * 		specifies the id of the newly created account
	 * @param op
	 * 		gRPC Transaction body
	 * @param consensusTimestamp
	 * 		consensus timestamp of the current transaction
	 * @return Account
	 */
	public static Account createFromGrpc(final Id accountId, final CryptoCreateTransactionBody op, long consensusTimestamp) {
		final var created = new Account(accountId);

		final long autoRenewPeriod = op.getAutoRenewPeriod().getSeconds();
		final long expiry = consensusTimestamp + autoRenewPeriod;
		final var key = asFcKeyUnchecked(op.getKey());
		created.setKey(key);
		created.setMemo(op.getMemo());
		created.setExpiry(expiry);
		created.setAutoRenewSecs(autoRenewPeriod);
		created.setMaxAutomaticAssociations(op.getMaxAutomaticTokenAssociations());

		if (op.hasProxyAccountID()) {
			created.setProxy(Id.fromGrpcAccount(op.getProxyAccountID()));
		}
		created.setReceiverSigRequired(op.getReceiverSigRequired());
		created.setNew(true);

		return created;
	}

	/**
	 * Transfers the provided Hbar amount from the current Account model to the provided
	 *
	 * @param recipient
	 * 		the {@link Account} that will get the transferred Hbars
	 * @param amount
	 * 		amount to transfer
	 * @return The list of balance changes to be externalized
	 */
	public List<Pair<Account, BalanceChange>> transferHbar(final Account recipient, long amount) {
		validateTrue(getBalance() >= amount, INSUFFICIENT_ACCOUNT_BALANCE);

		final var balanceAdjustments = new ArrayList<Pair<Account, BalanceChange>>();
		balanceAdjustments.add(Pair.of(
				this,
				BalanceChange.hbarAdjust(getId(), -1 * amount)
		));
		balanceAdjustments.add(Pair.of(
				recipient,
				BalanceChange.hbarAdjust(recipient.getId(), amount)
		));
		return balanceAdjustments;
	}

	public void incrementOwnedNfts() {
		this.ownedNfts++;
	}

	public int getAutoAssociationMetadata() {
		return autoAssociationMetadata;
	}

	public void setAutoAssociationMetadata(int autoAssociationMetadata) {
		this.autoAssociationMetadata = autoAssociationMetadata;
	}

	public int getMaxAutomaticAssociations() {
		return getMaxAutomaticAssociationsFrom(autoAssociationMetadata);
	}

	public void setMaxAutomaticAssociations(int maxAutomaticAssociations) {
		autoAssociationMetadata = setMaxAutomaticAssociationsTo(autoAssociationMetadata, maxAutomaticAssociations);
	}

	public int getAlreadyUsedAutomaticAssociations() {
		return getAlreadyUsedAutomaticAssociationsFrom(autoAssociationMetadata);
	}

	public void setAlreadyUsedAutomaticAssociations(int alreadyUsedCount) {
		validateTrue(isValidAlreadyUsedCount(alreadyUsedCount), NO_REMAINING_AUTOMATIC_ASSOCIATIONS );
		autoAssociationMetadata = setAlreadyUsedAutomaticAssociationsTo(autoAssociationMetadata, alreadyUsedCount);
	}

	public void incrementUsedAutomaticAssocitions() {
		var count = getAlreadyUsedAutomaticAssociations();
		setAlreadyUsedAutomaticAssociations(++count);
	}

	public void decrementUsedAutomaticAssocitions() {
		var count = getAlreadyUsedAutomaticAssociations();
		setAlreadyUsedAutomaticAssociations(--count);
	}

	public void associateWith(List<Token> tokens, int maxAllowed, boolean automaticAssociation) {
		final var alreadyAssociated = associatedTokens.size();
		final var proposedNewAssociations = tokens.size() + alreadyAssociated;
		validateTrue(proposedNewAssociations <= maxAllowed, TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED);

		final Set<Id> uniqueIds = new HashSet<>();
		for (var token : tokens) {
			final var id = token.getId();
			validateFalse(associatedTokens.contains(id), TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT);
			if (automaticAssociation) {
				incrementUsedAutomaticAssocitions();
			}
			uniqueIds.add(id);
		}

		associatedTokens.addAllIds(uniqueIds);
	}

	/**
	 * Applies the given list of {@link Dissociation}s, validating that this account is
	 * indeed associated to each involved token.
	 *
	 * @param dissociations
	 * 		the dissociations to perform.
	 * @param validator
	 * 		the validator to use for each dissociation
	 */
	public void dissociateUsing(List<Dissociation> dissociations, OptionValidator validator) {
		final Set<Id> dissociatedTokenIds = new HashSet<>();
		for (var dissociation : dissociations) {
			validateTrue(id.equals(dissociation.dissociatingAccountId()), FAIL_INVALID);
			dissociation.updateModelRelsSubjectTo(validator);
			dissociatedTokenIds.add(dissociation.dissociatedTokenId());
			if (dissociation.dissociatingAccountRel().isAutomaticAssociation()) {
				decrementUsedAutomaticAssocitions();
			}
		}
		associatedTokens.removeAllIds(dissociatedTokenIds);
	}

	public Id getId() {
		return id;
	}

	public CopyOnWriteIds getAssociatedTokens() {
		return associatedTokens;
	}

	public void setAssociatedTokens(CopyOnWriteIds associatedTokens) {
		this.associatedTokens = associatedTokens;
	}

	public boolean isAssociatedWith(Id token) {
		return associatedTokens.contains(token);
	}

	private boolean isValidAlreadyUsedCount(int alreadyUsedCount) {
		return alreadyUsedCount >= 0 && alreadyUsedCount <= getMaxAutomaticAssociations();
	}

	/* NOTE: The object methods below are only overridden to improve
	readability of unit tests; this model object is not used in hash-based
	collections, so the performance of these methods doesn't matter. */
	@Override
	public boolean equals(final Object o) {
		return EqualsBuilder.reflectionEquals(this, o);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		final var assocTokenRepr = Optional.ofNullable(associatedTokens)
				.map(CopyOnWriteIds::toReadableIdList)
				.orElse("<N/A>");
		return MoreObjects.toStringHelper(Account.class)
				.add("id", id)
				.add("expiry", expiry)
				.add("balance", balance)
				.add("deleted", deleted)
				.add("tokens", assocTokenRepr)
				.add("ownedNfts", ownedNfts)
				.add("alreadyUsedAutoAssociations", getAlreadyUsedAutomaticAssociations())
				.add("maxAutoAssociations", getMaxAutomaticAssociations())
				.toString();
	}

	public long getExpiry() {
		return expiry;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(final boolean deleted) {
		this.deleted = deleted;
	}

	public boolean isSmartContract() {
		return isSmartContract;
	}

	public void setSmartContract(boolean val) {
		this.isSmartContract = val;
	}

	public boolean isReceiverSigRequired() {
		return this.isReceiverSigRequired;
	}

	public void setReceiverSigRequired(boolean isReceiverSigRequired) {
		this.isReceiverSigRequired = isReceiverSigRequired;
	}

	public long getBalance() {
		return balance;
	}

	public void setBalance(long balance) {
		this.balance = balance;
	}

	public long getOwnedNfts() {
		return ownedNfts;
	}

	public void setOwnedNfts(long ownedNfts) {
		this.ownedNfts = ownedNfts;
	}

	public long getAutoRenewSecs() {
		return autoRenewSecs;
	}

	public void setAutoRenewSecs(final long autoRenewSecs) {
		this.autoRenewSecs = autoRenewSecs;
	}

	public JKey getKey() {
		return key;
	}

	public void setKey(final JKey key) {
		this.key = key;
	}

	public String getMemo() {
		return memo;
	}

	public void setMemo(final String memo) {
		this.memo = memo;
	}

	public Id getProxy() {
		return proxy;
	}

	public void setProxy(final Id proxy) {
		this.proxy = proxy;
	}

	public boolean isNew() {
		return this.isNew;
	}

	public void setNew(boolean isNew) {
		this.isNew = isNew;
	}

}
