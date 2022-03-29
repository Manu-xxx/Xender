package com.hedera.services.contracts.sources;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.ActivationTest;
import com.hedera.services.ledger.accounts.ContractAliases;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.crypto.TransactionSignature;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.function.BiPredicate;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;

@Singleton
public class TxnAwareEvmSigsVerifier implements EvmSigsVerifier {
	private final ActivationTest activationTest;
	private final TransactionContext txnCtx;
	private final BiPredicate<JKey, TransactionSignature> cryptoValidity;

	@Inject
	public TxnAwareEvmSigsVerifier(
			final ActivationTest activationTest,
			final TransactionContext txnCtx,
			final BiPredicate<JKey, TransactionSignature> cryptoValidity
	) {
		this.txnCtx = txnCtx;
		this.activationTest = activationTest;
		this.cryptoValidity = cryptoValidity;
	}

	@Override
	public boolean hasActiveKey(
			final boolean isDelegateCall,
			@NotNull final Address accountAddress,
			@NotNull final Address activeContract,
			@NotNull final WorldLedgers worldLedgers
	) {
		final var accountId = EntityIdUtils.accountIdFromEvmAddress(accountAddress);
		validateTrue(worldLedgers.accounts().exists(accountId), INVALID_ACCOUNT_ID);

		if (accountAddress.equals(activeContract)) {
			return true;
		}

		final var accountKey = (JKey) worldLedgers.accounts().get(accountId, AccountProperty.KEY);
		return accountKey != null && isActiveInFrame(accountKey, isDelegateCall,
				activeContract,
				worldLedgers.aliases());
	}

	@Override
	public boolean hasActiveSupplyKey(
			final boolean isDelegateCall,
			@NotNull final Address tokenAddress,
			@NotNull final Address activeContract,
			@NotNull final WorldLedgers worldLedgers
	) {
		final var tokenId = EntityIdUtils.tokenIdFromEvmAddress(tokenAddress);
		validateTrue(worldLedgers.tokens().exists(tokenId), INVALID_TOKEN_ID);

		final var supplyKey = (JKey) worldLedgers.tokens().get(tokenId, TokenProperty.SUPPLY_KEY);
		validateTrue(supplyKey != null, TOKEN_HAS_NO_SUPPLY_KEY);

		return isActiveInFrame(supplyKey, isDelegateCall, activeContract, worldLedgers.aliases());
	}

	@Override
	public boolean hasActiveKeyOrNoReceiverSigReq(
			final boolean isDelegateCall,
			@NotNull final Address target,
			@NotNull final Address activeContract,
			@NotNull final WorldLedgers worldLedgers
	) {
		final var accountId = EntityIdUtils.accountIdFromEvmAddress(target);
		if (txnCtx.activePayer().equals(accountId)) {
			return true;
		}
		final var requiredKey = receiverSigKeyIfAnyOf(accountId, worldLedgers);
		return requiredKey.map(key ->
				isActiveInFrame(key, isDelegateCall, activeContract, worldLedgers.aliases())).orElse(true);
	}

	private boolean isActiveInFrame(
			final JKey key,
			final boolean isDelegateCall,
			final Address activeContract,
			final ContractAliases aliases
	) {
		final var pkToCryptoSigsFn = txnCtx.accessor().getRationalizedPkToCryptoSigFn();
		return activationTest.test(
				key,
				pkToCryptoSigsFn,
				validityTestFor(isDelegateCall, activeContract, aliases));
	}

	BiPredicate<JKey, TransactionSignature> validityTestFor(
			final boolean isDelegateCall,
			final Address activeContract,
			final ContractAliases aliases
	) {
		/* Note that when this observer is used directly above in isActiveInFrame(), it will be
		 * called  with each primitive key in the top-level Hedera key of interest, along with
		 * that key's verified cryptographic signature (if any was available in the sigMap). */
		return (key, sig) -> {
			if (key.hasDelegatableContractId() || key.hasDelegatableContractAlias()) {
				final var controllingId = key.hasDelegatableContractId()
						? key.getDelegatableContractIdKey().getContractID()
						: key.getDelegatableContractAliasKey().getContractID();
				final var controllingContract =
						aliases.currentAddress(controllingId);
				return controllingContract.equals(activeContract);
			} else if (key.hasContractID() || key.hasContractAlias()) {
				final var controllingId = key.hasContractID()
						? key.getContractIDKey().getContractID()
						: key.getContractAliasKey().getContractID();
				final var controllingContract = aliases.currentAddress(controllingId);
				return !isDelegateCall && controllingContract.equals(activeContract);
			} else {
				/* Otherwise apply the standard cryptographic validity test */
				return cryptoValidity.test(key, sig);
			}
		};
	}

	private Optional<JKey> receiverSigKeyIfAnyOf(final AccountID id, final WorldLedgers worldLedgers) {
		final var merkleAccount = worldLedgers.accounts() != null ?
				Optional.ofNullable(worldLedgers.accounts().getImmutableRef(id)) :
				Optional.empty();

		return merkleAccount
				.filter(account -> !((MerkleAccount)account).isSmartContract())
				.filter(account -> ((MerkleAccount)account).isReceiverSigRequired())
				.map(account -> ((MerkleAccount)account).getAccountKey());
	}
}