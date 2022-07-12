package com.hedera.services.store.contracts.precompile.impl;

import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.ADMIN_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.FEE_SCHEDULE_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.FREEZE_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.KYC_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.PAUSE_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.SUPPLY_KEY;
import static com.hedera.services.store.contracts.precompile.KeyTypeConstants.WIPE_KEY;

import com.hedera.services.config.NetworkInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.codec.Expiry;
import com.hedera.services.store.contracts.precompile.codec.FixedFee;
import com.hedera.services.store.contracts.precompile.codec.FractionalFee;
import com.hedera.services.store.contracts.precompile.codec.HederaToken;
import com.hedera.services.store.contracts.precompile.codec.KeyValue;
import com.hedera.services.store.contracts.precompile.codec.RoyaltyFee;
import com.hedera.services.store.contracts.precompile.codec.TokenInfo;
import com.hedera.services.store.contracts.precompile.codec.TokenKey;
import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.ArrayList;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;

public abstract class AbstractTokenInfoPrecompile extends AbstractReadOnlyPrecompile {

  private final NetworkInfo networkInfo;

  protected AbstractTokenInfoPrecompile(
      final TokenID tokenId,
      final SyntheticTxnFactory syntheticTxnFactory,
      final WorldLedgers ledgers,
      final EncodingFacade encoder,
      final DecodingFacade decoder,
      final PrecompilePricingUtils pricingUtils,
      final NetworkInfo networkInfo) {
    super(tokenId, syntheticTxnFactory, ledgers, encoder, decoder, pricingUtils);
    this.networkInfo = networkInfo;
  }

  protected TokenInfo getTokenInfo() {
    final var token = ledgers.tokens().getImmutableRef(tokenId);

    final var name = token.name();
    final var symbol = token.symbol();
    final var treasury = EntityIdUtils.asTypedEvmAddress(token.treasury());
    final var memo = token.memo();
    final var tokenSupplyType = token.supplyType() == TokenSupplyType.FINITE;
    final var maxSupply = token.maxSupply();
    final var freezeDefault = token.hasFreezeKey();
    final var tokenKeys = getTokenKeys(token);
    final var expiry =
        new Expiry(
            token.expiry(),
            EntityIdUtils.asTypedEvmAddress(token.autoRenewAccount()),
            token.autoRenewPeriod());
    final var hederaToken =
        new HederaToken(
            name,
            symbol,
            treasury,
            memo,
            tokenSupplyType,
            maxSupply,
            freezeDefault,
            tokenKeys,
            expiry);

    final var totalSupply = token.totalSupply();
    final var deleted = token.isDeleted();
    final var defaultKycStatus = token.accountsKycGrantedByDefault();
    final var pauseStatus = token.isPaused();
    final var ledgerId = networkInfo.ledgerId().toString();

    final var fixedFees = new ArrayList<FixedFee>();
    final var fractionalFees = new ArrayList<FractionalFee>();
    final var royaltyFees = new ArrayList<RoyaltyFee>();

    for(final var fee : token.customFeeSchedule()) {
      final var feeCollector = EntityIdUtils.asTypedEvmAddress(fee.getFeeCollector());
      switch(fee.getFeeType()) {
        case FIXED_FEE -> fixedFees.add(getFixedFee(fee, feeCollector));
        case FRACTIONAL_FEE -> fractionalFees.add(getFractionalFee(fee, feeCollector));
        case ROYALTY_FEE -> royaltyFees.add(getRoyaltyFee(fee, feeCollector));
      }
    }

    return
        new TokenInfo(
            hederaToken,
            totalSupply,
            deleted,
            defaultKycStatus,
            pauseStatus,
            fixedFees,
            fractionalFees,
            royaltyFees,
            ledgerId);
  }

  private FixedFee getFixedFee(final FcCustomFee fee, final Address feeCollector) {
    final var fixedFeeSpec = fee.getFixedFeeSpec();
    return new FixedFee(fixedFeeSpec.getUnitsToCollect(), EntityIdUtils.asTypedEvmAddress(fixedFeeSpec.getTokenDenomination()),
        fixedFeeSpec.getTokenDenomination()==null, false, feeCollector);
  }

  private FractionalFee getFractionalFee(final FcCustomFee fee, final Address feeCollector) {
    final var fractionalFeeSpec = fee.getFractionalFeeSpec();
    return new FractionalFee(fractionalFeeSpec.getNumerator(), fractionalFeeSpec.getDenominator(),
        fractionalFeeSpec.getMinimumAmount(), fractionalFeeSpec.getMaximumUnitsToCollect(), fractionalFeeSpec.isNetOfTransfers(), feeCollector);
  }

  private RoyaltyFee getRoyaltyFee(final FcCustomFee fee, final Address feeCollector) {
    final var royaltyFeeSpec = fee.getRoyaltyFeeSpec();
    RoyaltyFee royaltyFee;
    if(royaltyFeeSpec.hasFallbackFee()) {
      final var fallbackFee = royaltyFeeSpec.fallbackFee();
      royaltyFee = new RoyaltyFee(royaltyFeeSpec.numerator(), royaltyFeeSpec.denominator(), fallbackFee.getUnitsToCollect(),
          EntityIdUtils.asTypedEvmAddress(fallbackFee.getTokenDenomination()), fallbackFee.getTokenDenomination() == null, feeCollector);
    } else {
      royaltyFee = new RoyaltyFee(royaltyFeeSpec.numerator(), royaltyFeeSpec.denominator(), 0L, null, false, feeCollector);
    }
    return royaltyFee;
  }

  private List<TokenKey> getTokenKeys(final MerkleToken token) {
    final List<TokenKey> tokenKeys = new ArrayList<>(7);

    if (token.getAdminKey() != null) {
      tokenKeys.add(getTokenKey(token.getAdminKey(), ADMIN_KEY));
    }
    if (token.getKycKey() != null) {
      tokenKeys.add(getTokenKey(token.getKycKey(), KYC_KEY));
    }
    if (token.getFreezeKey() != null) {
      tokenKeys.add(getTokenKey(token.getFreezeKey(), FREEZE_KEY));
    }
    if (token.getWipeKey() != null) {
      tokenKeys.add(getTokenKey(token.getWipeKey(), WIPE_KEY));
    }
    if (token.getSupplyKey() != null) {
      tokenKeys.add(getTokenKey(token.getSupplyKey(), SUPPLY_KEY));
    }
    if (token.getFeeScheduleKey() != null) {
      tokenKeys.add(getTokenKey(token.getFeeScheduleKey(), FEE_SCHEDULE_KEY));
    }
    if (token.getPauseKey() != null) {
      tokenKeys.add(getTokenKey(token.getPauseKey(), PAUSE_KEY));
    }

    return tokenKeys;
  }

  private TokenKey getTokenKey(final JKey key, final int keyType) {
    final var keyValue = getKeyValue(key);
    return new TokenKey(keyType, keyValue);
  }

  private KeyValue getKeyValue(final JKey key) {
    return new KeyValue(
        false,
        EntityIdUtils.asTypedEvmAddress(key.getContractIDKey().getContractID()),
        key.getEd25519(),
        key.getECDSASecp256k1Key(),
        EntityIdUtils.asTypedEvmAddress(key.getDelegatableContractIdKey().getContractID()));
  }
}
