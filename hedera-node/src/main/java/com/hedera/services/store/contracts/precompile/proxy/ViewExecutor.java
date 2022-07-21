package com.hedera.services.store.contracts.precompile.proxy;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO;
import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_ID_GET_TOKEN_INFO;
import static com.hedera.services.utils.MiscUtils.asSecondsTimestamp;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;

import com.hedera.services.config.NetworkInfo;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.store.contracts.WorldLedgers;
import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;
import com.hedera.services.store.contracts.precompile.utils.TokenInfoRetrievalUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class ViewExecutor {
  public static final long MINIMUM_TINYBARS_COST = 100;

  private final Bytes input;
  private final MessageFrame frame;
  private final WorldLedgers ledgers;
  private final EncodingFacade encoder;
  private final DecodingFacade decoder;
  private final ViewGasCalculator gasCalculator;
  private final HederaStackedWorldStateUpdater updater;
  private final NetworkInfo networkInfo;

  public ViewExecutor(
      final Bytes input,
      final MessageFrame frame,
      final EncodingFacade encoder,
      final DecodingFacade decoder,
      final ViewGasCalculator gasCalculator,
      final NetworkInfo networkInfo) {
    this.input = input;
    this.frame = frame;
    this.encoder = encoder;
    this.decoder = decoder;
    this.gasCalculator = gasCalculator;

    this.updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
    this.ledgers = updater.trackingLedgers();
    this.networkInfo = networkInfo;
  }

  public Pair<Long, Bytes> computeCosted() {
    final var now = asSecondsTimestamp(frame.getBlockValues().getTimestamp());
    final var costInGas = gasCalculator.compute(now, MINIMUM_TINYBARS_COST);

    final var selector = input.getInt(0);
    try {
      final var answer = answerGiven(selector);
      return Pair.of(costInGas, answer);
    } catch (final InvalidTransactionException e) {
      if (e.isReverting()) {
        frame.setRevertReason(e.getRevertReason());
        frame.setState(MessageFrame.State.REVERT);
      }
      return Pair.of(costInGas, null);
    }
  }

  private Bytes answerGiven(
      final int selector) {
    if (selector == ABI_ID_GET_TOKEN_INFO) {
      final var wrapper = decoder.decodeGetTokenInfo(input);
      final var tokenInfo = TokenInfoRetrievalUtils.getTokenInfo(wrapper.tokenID(), ledgers, networkInfo);
      return encoder.encodeGetTokenInfo(tokenInfo);
    } else if (selector == ABI_ID_GET_FUNGIBLE_TOKEN_INFO) {
      final var wrapper = decoder.decodeGetFungibleTokenInfo(input);
      final var fungibleTokenInfo = TokenInfoRetrievalUtils.getFungibleTokenInfo(wrapper.tokenID(), ledgers, networkInfo);
      return encoder.encodeGetFungibleTokenInfo(fungibleTokenInfo);
    } else if (selector == ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO) {
      final var wrapper = decoder.decodeGetNonFungibleTokenInfo(input);
      final var nonFungibleTokenInfo = TokenInfoRetrievalUtils.getNonFungibleTokenInfo(wrapper.tokenID(), wrapper.serialNumber(), ledgers, networkInfo);
      return encoder.encodeGetNonFungibleTokenInfo(nonFungibleTokenInfo);
    } else {
      // Only view functions can be used inside a ContractCallLocal
      throw new InvalidTransactionException(NOT_SUPPORTED);
    }
  }
}
