package com.hedera.node.app.service.contract.impl.exec.systemcontracts.burn;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Address;

public class BurnTranslator extends AbstractHtsCallTranslator {

    public static final Function BURN_TOKEN_V1 = new Function("burnToken(address,uint64,int64[])", "(int,int)");
    public static final Function BURN_TOKEN_V2 = new Function("burnToken(address,int64,int64[])", ReturnTypes.INT);

    @Inject
    public BurnTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), BurnTranslator.BURN_TOKEN_V1.selector())
                || Arrays.equals(attempt.selector(), BurnTranslator.BURN_TOKEN_V2.selector());
    }

    @Override
    public FungibleBurnCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var selector = attempt.selector();
        final Tuple call;
        if (Arrays.equals(selector, BurnTranslator.BURN_TOKEN_V1.selector())) {
            call = BurnTranslator.BURN_TOKEN_V1.decodeCall(attempt.input().toArrayUnsafe());
        } else {
            call = BurnTranslator.BURN_TOKEN_V2.decodeCall(attempt.input().toArrayUnsafe());
        }
        final var token = attempt.linkedToken(Address.fromHexString(call.get(0).toString()));
        if (token == null) {
            return null;
        } else {
            return token.tokenType() == TokenType.FUNGIBLE_COMMON
                    ? new FungibleBurnCall(
                            attempt.enhancement(),
                            call.get(0),
                            call.get(1),
                            attempt.defaultVerificationStrategy(),
                            attempt.senderAddress(),
                            attempt.addressIdConverter())
                    :  //@TODO NonFungibleBurnCall
                            new FungibleBurnCall(attempt.enhancement(), call.get(0), call.get(1),
                            attempt.defaultVerificationStrategy(), attempt.senderAddress(),
                            attempt.addressIdConverter());
        }
    }
}
