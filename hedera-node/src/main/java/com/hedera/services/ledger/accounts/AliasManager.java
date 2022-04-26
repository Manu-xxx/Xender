package com.hedera.services.ledger.accounts;

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

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static com.hedera.services.utils.MiscUtils.forEach;

/**
 * Handles a map with all the accounts that are auto-created. The map will be re-built on restart, reconnect.
 * Entries from the map are removed when the entity expires
 */
@Singleton
public class AliasManager extends AbstractContractAliases implements ContractAliases {
	private static final Logger log = LogManager.getLogger(AliasManager.class);

	private static final String NON_TRANSACTIONAL_MSG = "Base alias manager does not buffer changes";

	private static final ECCurve SECP256K1_CURVE = new SecP256K1Curve();

	private final Supplier<Map<ByteString, EntityNum>> aliases;

	@Inject
	public AliasManager(final Supplier<Map<ByteString, EntityNum>> aliases) {
		this.aliases = aliases;
	}

	@Override
	public void revert() {
		throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
	}

	@Override
	public void filterPendingChanges(Predicate<Address> filter) {
		throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
	}

	@Override
	public void commit(final @Nullable SigImpactHistorian observer) {
		throw new UnsupportedOperationException(NON_TRANSACTIONAL_MSG);
	}

	@Override
	public void link(final Address alias, final Address address) {
		link(ByteString.copyFrom(alias.toArrayUnsafe()), EntityNum.fromEvmAddress(address));
	}

	@Override
	public void unlink(final Address alias) {
		unlink(ByteString.copyFrom(alias.toArrayUnsafe()));
	}

	@Override
	public Address resolveForEvm(final Address addressOrAlias) {
		if (isMirror(addressOrAlias)) {
			return addressOrAlias;
		}
		final var aliasKey = ByteString.copyFrom(addressOrAlias.toArrayUnsafe());
		final var contractNum = curAliases().get(aliasKey);
		// If we cannot resolve to a mirror address, we return the missing alias and let a
		// downstream component fail the transaction by returning null from its get() method.
		// Cf. the address validator provided by ContractsModule#provideAddressValidator().
		return (contractNum == null) ? addressOrAlias : contractNum.toEvmAddress();
	}

	@Override
	public boolean isInUse(final Address address) {
		return curAliases().containsKey(ByteString.copyFrom(address.toArrayUnsafe()));
	}

	public void link(final ByteString alias, final EntityNum num) {
		curAliases().put(alias, num);
	}

	public boolean maybeLinkEvmAddress(final JKey key, final EntityNum num) {
		if (key != null && key.hasECDSAsecp256k1Key()) {
			// Only complressed keys are stored at the moment
			byte[] rawCompressedKey = key.getECDSASecp256k1Key();
			if (rawCompressedKey.length == JECDSASecp256k1Key.ECDSASECP256_COMPRESSED_BYTE_LENGTH) {
//				var evmAddress = calculateEthAddress(rawCompressedKey);
				var evmAddress = EthTxSigs.recoverAddressFromPubKey(rawCompressedKey);
				if (evmAddress != null) {
					link(ByteString.copyFrom(evmAddress), num);
				}
				return true;
			}
		}
		return false;
	}

	public void unlink(final ByteString alias) {
		curAliases().remove(alias);
	}

	/**
	 * From given MerkleMap of accounts, populate the auto accounts creations map. Iterate through
	 * each account in accountsMap and add an entry to autoAccountsMap if {@code alias} exists on the account.
	 *
	 * @param accounts
	 * 		the current accounts
	 */
	public void rebuildAliasesMap(final MerkleMap<EntityNum, MerkleAccount> accounts) {
		final var numCreate2Aliases = new AtomicInteger();
		final var numEOAliases = new AtomicInteger();
		final var workingAliases = curAliases();
		workingAliases.clear();
		forEach(accounts, (k, v) -> {
			if (!v.getAlias().isEmpty()) {
				workingAliases.put(v.getAlias(), k);
				if (v.isSmartContract()) {
					numCreate2Aliases.getAndIncrement();
				}
				try {
					Key key = Key.parseFrom(v.getAlias());
					JKey jKey = JKey.mapKey(key);
					if (maybeLinkEvmAddress(jKey, v.getKey())) {
						numEOAliases.incrementAndGet();
					}
				} catch (InvalidProtocolBufferException | DecoderException | IllegalArgumentException e) {
					// any expected exception means no eth mapping
				}
			}
		});
		log.info("Rebuild complete, re-mapped {} aliases ({} from CREATE2, {} externally owned accounts)",
				workingAliases::size, numCreate2Aliases::get, numEOAliases::get);
	}

	/**
	 * Ensures an alias is no longer in use, returning whether it previously was.
	 *
	 * @param alias the alias to forget
	 * @return whether it was present
	 */
	public boolean forgetAlias(final ByteString alias) {
		if (alias.isEmpty()) {
			return false;
		}
		return curAliases().remove(alias) != null;
	}

	public void forgetEvmAddress(final ByteString alias) {
		try {
			var key = Key.parseFrom(alias);
			var jKey = JKey.mapKey(key);
			if (jKey.hasECDSAsecp256k1Key()) {
				// ecdsa keys from alias are currently only stored in compressed form.
				byte[] rawCompressedKey = jKey.getECDSASecp256k1Key();
				// trust, but verify
				if (rawCompressedKey.length == JECDSASecp256k1Key.ECDSASECP256_COMPRESSED_BYTE_LENGTH) {
//					var evmAddress = calculateEthAddress(rawCompressedKey);
					var evmAddress = EthTxSigs.recoverAddressFromPubKey(rawCompressedKey);
					if (evmAddress != null) {
						curAliases().remove(ByteString.copyFrom(evmAddress));
					}
				}
			}
		} catch (InvalidProtocolBufferException | DecoderException internal) {
			// any parse error means it's not a evm address
		}
	}

	/**
	 * Returns the entityNum for the given alias
	 *
	 * @param alias
	 * 		alias of the accountId
	 * @return EntityNum mapped to the given alias.
	 */
	public EntityNum lookupIdBy(final ByteString alias) {
		return curAliases().getOrDefault(alias, MISSING_NUM);
	}

	private Map<ByteString, EntityNum> curAliases() {
		return aliases.get();
	}

	@VisibleForTesting
	Map<ByteString, EntityNum> getAliases() {
		return curAliases();
	}
}
