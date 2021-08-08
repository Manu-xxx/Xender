package com.hedera.services.state.submerkle;

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
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.FixedFee;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.FractionalFee;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.IOException;
import java.util.Objects;

import static com.hedera.services.state.submerkle.FcCustomFee.FeeType.FIXED_FEE;

/**
 * Represents a custom fee attached to an HTS token type. Custom fees are
 * charged during a CryptoTransfer that moves units of the token type. They
 * are always paid by the same account that pays the ordinary Hedera fees
 * to account 0.0.98 and the submitting node's account.
 *
 * A custom fee must give a fee collection account to receive the charged
 * fees. The amount to be charged is specified by either a fixed or
 * fractional term.
 *
 * A <i>fixed fee</i> may have units of either ℏ or an arbitrary HTS token.
 *
 * A <i>fractional fee</i> always has the same units as the token type
 * defining the custom fee. It specifies the fraction of the units
 * moved that should go to the fee collection account, along with an
 * optional minimum and maximum number of units to be charged.
 *
 * A <i>royalty fee</i> is used with a non-fungible unique token type and
 * sets a fraction of the fungible value received for a NFT that should
 * be collected as royalty. (If no fungible value is received, a fixed fee
 * can be charged to the NFT's new owner, if desired.)
 */
public class FcCustomFee implements SelfSerializable {
	static final byte FIXED_CODE = (byte) (1 << 0);
	static final byte FRACTIONAL_CODE = (byte) (1 << 1);

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0xf65baa433940f137L;

	private FeeType feeType;
	private EntityId feeCollector;
	private FixedFeeSpec fixedFeeSpec;
	private FractionalFeeSpec fractionalFeeSpec;

	public enum FeeType {
		FRACTIONAL_FEE, FIXED_FEE, ROYALTY_FEE
	}

	public FcCustomFee() {
		/* For RuntimeConstructable */
	}

	private FcCustomFee(
			FeeType feeType,
			EntityId feeCollector,
			FixedFeeSpec fixedFeeSpec,
			FractionalFeeSpec fractionalFeeSpec
	) {
		this.feeType = feeType;
		this.feeCollector = feeCollector;
		this.fixedFeeSpec = fixedFeeSpec;
		this.fractionalFeeSpec = fractionalFeeSpec;
	}

	public static FcCustomFee fractionalFee(
			long numerator,
			long denominator,
			long minimumUnitsToCollect,
			long maximumUnitsToCollect,
			EntityId feeCollector
	) {
		Objects.requireNonNull(feeCollector);
		final var spec = new FractionalFeeSpec(numerator, denominator, minimumUnitsToCollect, maximumUnitsToCollect);
		return new FcCustomFee(FeeType.FRACTIONAL_FEE, feeCollector, null, spec);
	}

	public static FcCustomFee fixedFee(
			long unitsToCollect,
			EntityId tokenDenomination,
			EntityId feeCollector
	) {
		Objects.requireNonNull(feeCollector);
		final var spec = new FixedFeeSpec(unitsToCollect, tokenDenomination);
		return new FcCustomFee(FIXED_FEE, feeCollector, spec, null);
	}

	public static FcCustomFee fromGrpc(CustomFee source, EntityId targetId) {
		final var feeCollector = EntityId.fromGrpcAccountId(source.getFeeCollectorAccountId());
		if (source.hasFixedFee()) {
			EntityId denom = null;
			final var fixedSource = source.getFixedFee();
			if (fixedSource.hasDenominatingTokenId()) {
				denom = EntityId.fromGrpcTokenId(fixedSource.getDenominatingTokenId());
				if (0 == denom.num()) {
					denom = targetId;
				}
			}
			return fixedFee(fixedSource.getAmount(), denom, feeCollector);
		} else {
			final var fractionalSource = source.getFractionalFee();
			final var fraction = fractionalSource.getFractionalAmount();
			final var nominalMax = fractionalSource.getMaximumAmount();
			final var effectiveMax = nominalMax == 0 ? Long.MAX_VALUE : nominalMax;
			return fractionalFee(
					fraction.getNumerator(),
					fraction.getDenominator(),
					fractionalSource.getMinimumAmount(),
					effectiveMax,
					feeCollector);
		}
	}

	public CustomFee asGrpc() {
		final var builder = CustomFee.newBuilder()
				.setFeeCollectorAccountId(feeCollector.toGrpcAccountId());
		if (feeType == FIXED_FEE) {
			final var spec = fixedFeeSpec;
			final var fixedBuilder = FixedFee.newBuilder()
					.setAmount(spec.getUnitsToCollect());
			if (spec.getTokenDenomination() != null) {
				fixedBuilder.setDenominatingTokenId(spec.getTokenDenomination().toGrpcTokenId());
			}
			builder.setFixedFee(fixedBuilder);
		} else {
			final var spec = fractionalFeeSpec;
			final var fracBuilder = FractionalFee.newBuilder()
					.setFractionalAmount(Fraction.newBuilder()
							.setNumerator(spec.getNumerator())
							.setDenominator(spec.getDenominator()))
					.setMinimumAmount(spec.getMinimumAmount());
			if (spec.getMaximumUnitsToCollect() != Long.MAX_VALUE) {
				fracBuilder.setMaximumAmount(spec.getMaximumUnitsToCollect());
			}
			builder.setFractionalFee(fracBuilder);
		}
		return builder.build();
	}

	public EntityId getFeeCollector() {
		return feeCollector;
	}

	public Id getFeeCollectorAsId() {
		return feeCollector.asId();
	}

	public FeeType getFeeType() {
		return feeType;
	}

	public FixedFeeSpec getFixedFeeSpec() {
		return fixedFeeSpec;
	}

	public FractionalFeeSpec getFractionalFeeSpec() {
		return fractionalFeeSpec;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || !obj.getClass().equals(FcCustomFee.class)) {
			return false;
		}

		final var that = (FcCustomFee) obj;
		return this.feeType == that.feeType &&
				Objects.equals(this.feeCollector, that.feeCollector) &&
				Objects.equals(this.fixedFeeSpec, that.fixedFeeSpec) &&
				Objects.equals(this.fractionalFeeSpec, that.fractionalFeeSpec);
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(FcCustomFee.class)
				.omitNullValues()
				.add("feeType", feeType)
				.add("fixedFee", fixedFeeSpec)
				.add("fractionalFee", fractionalFeeSpec)
				.add("feeCollector", feeCollector)
				.toString();
	}

	@Override
	public void deserialize(SerializableDataInputStream din, int version) throws IOException {
		var byteCode = din.readByte();
		if (byteCode == FIXED_CODE) {
			feeType = FIXED_FEE;
			var unitsToCollect = din.readLong();
			EntityId denom = din.readSerializable(true, EntityId::new);
			fixedFeeSpec = new FixedFeeSpec(unitsToCollect, denom);
		} else {
			feeType = FeeType.FRACTIONAL_FEE;
			var numerator = din.readLong();
			var denominator = din.readLong();
			var minimumUnitsToCollect = din.readLong();
			var maximumUnitsToCollect = din.readLong();
			fractionalFeeSpec = new FractionalFeeSpec(
					numerator, denominator, minimumUnitsToCollect, maximumUnitsToCollect);
		}

		feeCollector = din.readSerializable(true, EntityId::new);
	}

	@Override
	public void serialize(SerializableDataOutputStream dos) throws IOException {
		if (feeType == FIXED_FEE) {
			dos.writeByte(FIXED_CODE);
			dos.writeLong(fixedFeeSpec.getUnitsToCollect());
			dos.writeSerializable(fixedFeeSpec.getTokenDenomination(), true);
		} else {
			dos.writeByte(FRACTIONAL_CODE);
			dos.writeLong(fractionalFeeSpec.getNumerator());
			dos.writeLong(fractionalFeeSpec.getDenominator());
			dos.writeLong(fractionalFeeSpec.getMinimumAmount());
			dos.writeLong(fractionalFeeSpec.getMaximumUnitsToCollect());
		}
		dos.writeSerializable(feeCollector, true);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}
}
