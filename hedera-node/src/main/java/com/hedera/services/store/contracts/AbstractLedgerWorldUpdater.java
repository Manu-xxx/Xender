package com.hedera.services.store.contracts;

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

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;
import org.hyperledger.besu.evm.worldstate.WorldView;
import org.hyperledger.besu.evm.worldstate.WrappedEvmAccount;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromSolidityAddress;

/**
 * Provides implementation help for both "base" and "stacked" {@link WorldUpdater}s.
 *
 * The key internal invariant of the class is that it makes consistent use of ts (1) {@code deletedAccounts} set
 * and {@code updatedAccounts} map; and (2) the {@code accounts} tracking ledger from its {@code trackingLedgers}.
 *
 * In the absence of HTS precompiles, the "internal" information flow is one-way, from (1) to (2).
 * There are three cases:
 * <ol>
 *     <li>When an address is added to the {@code deletedAccounts}, it is also marked deleted in the
 *     {@code accounts} ledger.</li>
 *     <li>When an address is added to the {@code updatedAccounts} map via
 *     {@link AbstractLedgerWorldUpdater#createAccount(Address, long, Wei)}, it is also spawned in the
 *     {@code accounts} ledger.</li>
 *     <li>When {@link UpdateTrackingLedgerAccount#setBalance(Wei)} is called on a (mutable) tracking
 *     account, the same balance change is made in the {@code accounts} ledger.</li>
 * </ol>
 *
 * 📝 TODO When an HTS precompile is executed, reflect ℏ balance changes in the {@code updatedAccounts} map.
 *
 * Concrete subclasses must then manage the "external" information flow from these data structures to their
 * wrapped {@link WorldView} in a {@link HederaWorldUpdater#commit()} implementation. This will certainly
 * involve calling {@link WorldLedgers#commit()}, and then merging the {@code deletedAccounts} and
 * {@code updatedAccounts} with the parent {@link org.hyperledger.besu.evm.worldstate.WorldState} in some way.
 *
 * @param <A>
 * 		the most specialized account type to be updated
 * @param <W>
 * 		the most specialized world updater to be used
 */
public abstract class AbstractLedgerWorldUpdater<W extends WorldView, A extends Account> implements WorldUpdater {
	private final W world;
	private final WorldLedgers trackingLedgers;

	protected Set<Address> deletedAccounts = new HashSet<>();
	protected Map<Address, UpdateTrackingLedgerAccount<A>> updatedAccounts = new HashMap<>();

	protected AbstractLedgerWorldUpdater(
			final W world,
			final WorldLedgers trackingLedgers
	) {
		this.world = world;
		this.trackingLedgers = trackingLedgers;
	}

	protected abstract A getForMutation(Address address);

	@Override
	public WorldUpdater updater() {
		return new StackedLedgerUpdater<>(this, trackingLedgers.wrapped());
	}

	@Override
	public EvmAccount createAccount(final Address address, final long nonce, final Wei balance) {
		final var newMutable = new UpdateTrackingLedgerAccount<A>(address, trackingLedgers.accounts());
		newMutable.setNonce(nonce);
		newMutable.setBalance(balance);


		if (trackingLedgers.areUsable()) {
			final var accounts = trackingLedgers.accounts();
			final var accountId = newMutable.getAccountId();
			accounts.create(accountId);
			accounts.set(accountId, BALANCE, balance.toLong());
		}

		return new WrappedEvmAccount(track(newMutable));
	}

	@Override
	public Account get(final Address address) {
		final var extantMutable = this.updatedAccounts.get(address);
		if (extantMutable != null) {
			return extantMutable;
		} else {
			return this.deletedAccounts.contains(address) ? null : this.world.get(address);
		}
	}

	@Override
	public EvmAccount getAccount(final Address address) {
		final var extantMutable = updatedAccounts.get(address);
		if (extantMutable != null) {
			return new WrappedEvmAccount(extantMutable);
		} else if (deletedAccounts.contains(address)) {
			return null;
		} else {
			final var origin = getForMutation(address);
			if (origin == null) {
				return null;
			}
			final var newMutable = new UpdateTrackingLedgerAccount<>(origin, trackingLedgers.accounts());
			return new WrappedEvmAccount(track(newMutable));
		}
	}

	@Override
	public void deleteAccount(final Address address) {
		deletedAccounts.add(address);
		updatedAccounts.remove(address);
		if (trackingLedgers.areUsable()) {
			final var accountId = accountParsedFromSolidityAddress(address);
			trackingLedgers.accounts().set(accountId, IS_DELETED, true);
		}
	}

	@Override
	public Optional<WorldUpdater> parentUpdater() {
		if (world instanceof WorldUpdater) {
			return Optional.of((WorldUpdater) world);
		} else {
			return Optional.empty();
		}
	}

	@Override
	public Collection<? extends Account> getTouchedAccounts() {
		return new ArrayList<>(updatedAccounts());
	}

	@Override
	public Collection<Address> getDeletedAccountAddresses() {
		return new ArrayList<>(deletedAccounts);
	}

	protected UpdateTrackingLedgerAccount<A> track(final UpdateTrackingLedgerAccount<A> account) {
		final var address = account.getAddress();
		updatedAccounts.put(address, account);
		deletedAccounts.remove(address);
		return account;
	}

	protected W wrappedWorldView() {
		return world;
	}

	protected Collection<Address> deletedAccounts() {
		return deletedAccounts;
	}

	protected Collection<UpdateTrackingLedgerAccount<A>> updatedAccounts() {
		return updatedAccounts.values();
	}

	protected WorldLedgers trackingLedgers() {
		return trackingLedgers;
	}

	protected TransactionalLedger<AccountID, AccountProperty, MerkleAccount> trackingAccounts() {
		return trackingLedgers.accounts();
	}
}
