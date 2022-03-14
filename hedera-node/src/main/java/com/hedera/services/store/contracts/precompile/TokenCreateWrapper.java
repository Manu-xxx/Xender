package com.hedera.services.store.contracts.precompile;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.codec.DecoderException;

import java.math.BigInteger;
import java.util.List;

final class TokenCreateWrapper {
	private final boolean isFungible;
	private final String name;
	private final String symbol;
	private final AccountID treasury;
	private final boolean isSupplyTypeFinite;
	private final BigInteger initSupply;
	private final BigInteger decimals;
	private final long maxSupply;
	private final String memo;
	private final boolean isFreezeDefault;
	private final List<TokenKeyWrapper> tokenKeys;
	private final TokenExpiryWrapper expiry;
	private List<FixedFeeWrapper> fixedFees;
	private List<FractionalFeeWrapper> fractionalFees;
	private List<RoyaltyFeeWrapper> royaltyFees;

	TokenCreateWrapper(final boolean isFungible, final String tokenName, final String tokenSymbol,
					   final AccountID tokenTreasury, final String memo, final Boolean isSupplyTypeFinite,
					   final BigInteger initSupply, final BigInteger decimals, final long maxSupply,
					   final Boolean isFreezeDefault, final List<TokenKeyWrapper> tokenKeys,
					   final TokenExpiryWrapper tokenExpiry) {
		this.isFungible = isFungible;
		this.name = tokenName;
		this.symbol = tokenSymbol;
		this.treasury = tokenTreasury;
		this.memo = memo;
		this.isSupplyTypeFinite = isSupplyTypeFinite;
		this.initSupply = initSupply;
		this.decimals = decimals;
		this.maxSupply = maxSupply;
		this.isFreezeDefault = isFreezeDefault;
		this.tokenKeys = tokenKeys;
		this.expiry = tokenExpiry;
		this.fixedFees = List.of();
		this.fractionalFees = List.of();
		this.royaltyFees = List.of();
	}

	boolean isFungible() {
		return isFungible;
	}

	String getName() {
		return name;
	}

	String getSymbol() {
		return symbol;
	}

	AccountID getTreasury() {
		return treasury;
	}

	boolean isSupplyTypeFinite() {
		return isSupplyTypeFinite;
	}

	BigInteger getInitSupply() {
		return initSupply;
	}

	BigInteger getDecimals() {
		return decimals;
	}

	long getMaxSupply() {
		return maxSupply;
	}

	String getMemo() {
		return memo;
	}

	boolean isFreezeDefault() {
		return isFreezeDefault;
	}

	List<TokenKeyWrapper> getTokenKeys() {
		return tokenKeys;
	}

	TokenExpiryWrapper getExpiry() {
		return expiry;
	}

	List<FixedFeeWrapper> getFixedFees() {
		return fixedFees;
	}

	List<FractionalFeeWrapper> getFractionalFees() {
		return fractionalFees;
	}

	List<RoyaltyFeeWrapper> getRoyaltyFees() {
		return royaltyFees;
	}

	void setFixedFees(final List<FixedFeeWrapper> fixedFees) {
		this.fixedFees = fixedFees;
	}

	void setFractionalFees(final List<FractionalFeeWrapper> fractionalFees) {
		this.fractionalFees = fractionalFees;
	}

	void setRoyaltyFees(final List<RoyaltyFeeWrapper> royaltyFees) {
		this.royaltyFees = royaltyFees;
	}

	void setAllInheritedKeysTo(final JKey senderKey) throws DecoderException {
		for (final var tokenKey: tokenKeys) {
			if (tokenKey.key.isShouldInheritAccountKeySet()) {
				tokenKey.key.setInheritedKey(JKey.mapJKey(senderKey));
			}
		}
	}

	record TokenKeyWrapper(BigInteger keyType, KeyValueWrapper key) {
		boolean isUsedForAdminKey() {
			return (keyType().intValue() & 1) != 0;
		}

		boolean isUsedForKycKey() {
			return (keyType().intValue() & 2) != 0;
		}

		boolean isUsedForFreezeKey() {
			return (keyType().intValue() & 4) != 0;
		}

		boolean isUsedForWipeKey() {
			return (keyType().intValue() & 8) != 0;
		}

		boolean isUsedForSupplyKey() {
			return (keyType().intValue() & 16) != 0;
		}

		boolean isUsedForFeeScheduleKey() {
			return (keyType().intValue() & 32) != 0;
		}

		boolean isUsedForPauseKey() {
			return (keyType().intValue() & 64) != 0;
		}
	}

	static final class KeyValueWrapper {
		/* ---  Only 1 of these values should be set when the input is valid. --- */
		private final boolean shouldInheritAccountKey;
		private final ContractID contractID;
		private final byte[] ed25519;
		private final byte[] ecdsSecp256k1;
		private final ContractID delegatableContractID;

		/* --- This field is populated only when `shouldInheritAccountKey` is true --- */
		private Key inheritedKey;

		public KeyValueWrapper(final boolean shouldInheritAccountKey, final ContractID contractID, final byte[] ed25519,
							   final byte[] ecdsSecp256k1, final ContractID delegatableContractID) {
			this.shouldInheritAccountKey = shouldInheritAccountKey;
			this.contractID = contractID;
			this.ed25519 = ed25519;
			this.ecdsSecp256k1 = ecdsSecp256k1;
			this.delegatableContractID = delegatableContractID;
		}

		boolean isContractIDSet() {
			return contractID != null;
		}

		boolean isDelegatableContractIdSet() {
			return delegatableContractID != null;
		}

		boolean isShouldInheritAccountKeySet() {
			return shouldInheritAccountKey;
		}

		boolean isEd25519KeySet() {
			return ed25519.length == 32;
		}

		boolean isEcdsSecp256k1KeySet() {
			return ecdsSecp256k1.length == 33;
		}

		private void setInheritedKey(final Key key) {
			this.inheritedKey = key;
		}

		Key asGrpc() {
			if (shouldInheritAccountKey) {
				return this.inheritedKey;
			} else if (contractID != null) {
				return Key.newBuilder().setContractID(contractID).build();
			} else if (ed25519.length == 32) {
				return Key.newBuilder().setEd25519(ByteString.copyFrom(ed25519)).build();
			} else if (ecdsSecp256k1.length == 33) {
				return Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(ecdsSecp256k1)).build();
			} else if (delegatableContractID != null) {
				return Key.newBuilder().setContractID((delegatableContractID)).build();
			} else {
				return Key.newBuilder().build();
			}
		}
	}

	record TokenExpiryWrapper(long second, AccountID autoRenewAccount, long autoRenewPeriod) { }

	record FixedFeeWrapper(long amount, TokenID tokenID, AccountID feeCollector) {
		CustomFee asGrpc() {
			final var fixedFeeBuilder = FixedFee.newBuilder().setAmount(amount);
			if (tokenID != null)
				fixedFeeBuilder.setDenominatingTokenId(tokenID);
			return CustomFee.newBuilder()
				.setFixedFee(fixedFeeBuilder.build())
				.setFeeCollectorAccountId(feeCollector)
				.build();
		}
	}

	record FractionalFeeWrapper(long numerator, long denominator, long minimumAmount, long maximumAmount,
								boolean netOfTransfers, AccountID feeCollector) {
		CustomFee asGrpc() {
			return CustomFee.newBuilder()
				.setFractionalFee(com.hederahashgraph.api.proto.java.FractionalFee.newBuilder()
					.setFractionalAmount(Fraction.newBuilder()
						.setNumerator(numerator)
						.setDenominator(denominator)
						.build()
					)
					.setMinimumAmount(minimumAmount)
					.setMaximumAmount(maximumAmount)
					.setNetOfTransfers(netOfTransfers)
					.build()
				)
				.setFeeCollectorAccountId(feeCollector)
				.build();
		}
	}

	record RoyaltyFeeWrapper(long numerator, long denominator, FixedFeeWrapper fallbackFixedFee,
							 AccountID feeCollector) {
		CustomFee asGrpc() {
			final var fallbackFeeBuilder = FixedFee.newBuilder().setAmount(fallbackFixedFee.amount);
			if (fallbackFixedFee.tokenID != null)
				fallbackFeeBuilder.setDenominatingTokenId(fallbackFixedFee.tokenID);
			return CustomFee.newBuilder()
				.setRoyaltyFee(com.hederahashgraph.api.proto.java.RoyaltyFee.newBuilder()
					.setExchangeValueFraction(Fraction.newBuilder()
						.setNumerator(numerator)
						.setDenominator(denominator)
						.build()
					)
					.setFallbackFee(fallbackFeeBuilder.build())
					.build()
				)
				.setFeeCollectorAccountId(feeCollector)
				.build();
		}
	}
}