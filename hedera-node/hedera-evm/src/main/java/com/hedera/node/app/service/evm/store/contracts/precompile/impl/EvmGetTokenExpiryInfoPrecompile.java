package com.hedera.node.app.service.evm.store.contracts.precompile.impl;

import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.convertAddressBytesToTokenID;
import static com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmDecodingFacade.decodeFunctionCall;
import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.BYTES32;

import com.esaulpaugh.headlong.abi.ABIType;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.esaulpaugh.headlong.abi.TypeFactory;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.GetTokenExpiryInfoWrapper;
import org.apache.tuweni.bytes.Bytes;

public interface EvmGetTokenExpiryInfoPrecompile {
  Function GET_TOKEN_EXPIRY_INFO_FUNCTION =
      new Function("getTokenExpiryInfo(address)");
   Bytes GET_TOKEN_EXPIRY_INFO_SELECTOR =
      Bytes.wrap(GET_TOKEN_EXPIRY_INFO_FUNCTION.selector());
  ABIType<Tuple> GET_TOKEN_EXPIRY_INFO_DECODER = TypeFactory.create(BYTES32);

  public static GetTokenExpiryInfoWrapper decodeGetTokenExpiryInfo(final Bytes input) {
    final Tuple decodedArguments =
        decodeFunctionCall(
            input, GET_TOKEN_EXPIRY_INFO_SELECTOR, GET_TOKEN_EXPIRY_INFO_DECODER);

    final var tokenID = convertAddressBytesToTokenID(decodedArguments.get(0));
    return new GetTokenExpiryInfoWrapper(tokenID);
  }

}
