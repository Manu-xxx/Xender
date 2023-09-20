package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Arrays;

import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT;

@Singleton
public class GetApprovedTranslator extends AbstractHtsCallTranslator {

    public static final Function HAPI_GET_APPROVED =
            new Function("getApproved(address,uint256)", "(int,int)");
    public static final Function ERC_GET_APPROVED = new Function("getApproved(uint256)", INT);

    @Inject
    public GetApprovedTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull HtsCallAttempt attempt) {
        return (attempt.isTokenRedirect() && matchesErcSelector(attempt.selector()))
                || (!attempt.isTokenRedirect() && matchesClassicSelector(attempt.selector()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GetApprovedCall callFrom(@NonNull HtsCallAttempt attempt) {
        if (matchesErcSelector(attempt.selector())) {
            final var args = ERC_GET_APPROVED.decodeCall(attempt.input().toArrayUnsafe());
            return new GetApprovedCall(attempt.enhancement(), attempt.redirectToken(), args.get(0), true);
        } else {
            final var args = HAPI_GET_APPROVED.decodeCall(attempt.input().toArrayUnsafe());
            return new GetApprovedCall(attempt.enhancement(), args.get(0), args.get(1),  false);
        }
    }

    private static boolean matchesErcSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ERC_GET_APPROVED.selector());
    }

    private static boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, HAPI_GET_APPROVED.selector());
    }
}
