package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.Arrays;

import static java.util.Objects.requireNonNull;

/**
 * Translates associate and dissociate calls to the HTS system contract. There are no special cases for
 * these calls, so the returned {@link HtsCall} is simply an instance of {@link DispatchForResponseCodeHtsCall}.
 */
@Singleton
public class AssociationsTranslator implements HtsCallTranslator<DispatchForResponseCodeHtsCall<SingleTransactionRecordBuilder>> {
    public static final Function HRC_ASSOCIATE = new Function("associate()", ReturnTypes.INT);
    public static final Function ASSOCIATE_ONE = new Function("associateToken(address,address)", ReturnTypes.INT_64);
    public static final Function DISSOCIATE_ONE = new Function("dissociateToken(address,address)", ReturnTypes.INT_64);
    public static final Function HRC_DISSOCIATE = new Function("dissociate()", ReturnTypes.INT);
    public static final Function ASSOCIATE_MANY =
            new Function("associateTokens(address,address[])", ReturnTypes.INT_64);
    public static final Function DISSOCIATE_MANY =
            new Function("dissociateTokens(address,address[])", ReturnTypes.INT_64);

    private final AssociationsDecoder decoder;

    @Inject
    public AssociationsTranslator(@NonNull final AssociationsDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public @Nullable DispatchForResponseCodeHtsCall<SingleTransactionRecordBuilder> translate(@NonNull final HtsCallAttempt attempt) {
        requireNonNull(attempt);
        if (matches(attempt)) {
            return new DispatchForResponseCodeHtsCall<>(
                    attempt,
                    matchesHrcSelector(attempt.selector()) ? bodyForHrc(attempt) : bodyForClassic(attempt),
                    SingleTransactionRecordBuilder.class);
        }
        return null;
    }

    private TransactionBody bodyForHrc(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), HRC_ASSOCIATE.selector())) {
            return decoder.decodeHrcAssociate(attempt);
        } else {
            return decoder.decodeHrcDissociate(attempt);
        }
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), ASSOCIATE_ONE.selector())) {
            return decoder.decodeAssociateOne(attempt);
        } else if (Arrays.equals(attempt.selector(), ASSOCIATE_MANY.selector())) {
            return decoder.decodeAssociateMany(attempt);
        } else if (Arrays.equals(attempt.selector(), DISSOCIATE_ONE.selector())) {
            return decoder.decodeDissociateOne(attempt);
        } else {
            return decoder.decodeDissociateMany(attempt);
        }
    }

    private boolean matches(@NonNull final HtsCallAttempt attempt) {
        return (attempt.isTokenRedirect() && matchesHrcSelector(attempt.selector()))
                || (!attempt.isTokenRedirect() && matchesClassicSelector(attempt.selector()));
    }

    private static boolean matchesHrcSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, HRC_ASSOCIATE.selector()) || Arrays.equals(selector, HRC_DISSOCIATE.selector());
    }

    private static boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ASSOCIATE_ONE.selector())
                || Arrays.equals(selector, DISSOCIATE_ONE.selector())
                || Arrays.equals(selector, ASSOCIATE_MANY.selector())
                || Arrays.equals(selector, DISSOCIATE_MANY.selector());
    }
}
