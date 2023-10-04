package com.hedera.services.bdd.suites.contract.classiccalls.views;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.contract.classiccalls.AbstractFailableNonStaticCall;
import com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.EnumSet;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_ACCOUNT_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_TOKEN_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.ALICE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_ACCOUNT_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_TOKEN_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.VALID_NON_FUNGIBLE_TOKEN_IDS;

public class RevokeKycFailableCall extends AbstractFailableNonStaticCall {
    private static final Function SIGNATURE = new Function("revokeTokenKyc(address,address)", "(int64)");

    public RevokeKycFailableCall() {
        super(EnumSet.of(INVALID_TOKEN_ID_FAILURE, INVALID_ACCOUNT_ID_FAILURE));
    }

    @Override
    public String name() {
        return "revokeTokenKyc";
    }

    @Override
    public byte[] encodedCall(@NonNull final ClassicFailureMode mode, @NonNull final HapiSpec spec) {
        throwIfUnsupported(mode);
        final var validAccountAddress = idAsHeadlongAddress(spec.registry().getAccountID(ALICE));
        if (mode == INVALID_TOKEN_ID_FAILURE) {
            return SIGNATURE.encodeCallWithArgs(
                    INVALID_TOKEN_ADDRESS,
                    validAccountAddress).array();
        } else {
            // Must be INVALID_ACCOUNT_ID_FAILURE
            return SIGNATURE.encodeCallWithArgs(
                    idAsHeadlongAddress(spec.registry().getTokenID(VALID_NON_FUNGIBLE_TOKEN_IDS[0])),
                    INVALID_ACCOUNT_ADDRESS).array();
        }
    }
}
