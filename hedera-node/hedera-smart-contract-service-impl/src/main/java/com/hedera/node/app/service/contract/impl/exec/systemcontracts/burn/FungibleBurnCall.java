package com.hedera.node.app.service.contract.impl.exec.systemcontracts.burn;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.TokenBurnRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;

public class FungibleBurnCall extends AbstractHtsCall {

    private final long amount;

    @Nullable
    private final TokenID tokenId;
    private final AddressIdConverter addressIdConverter;
    private final VerificationStrategy verificationStrategy;
    private final org.hyperledger.besu.datatypes.Address spender;

    public FungibleBurnCall(@NonNull final HederaWorldUpdater.Enhancement enhancement,
            @Nullable final TokenID tokenId,
            final long amount,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(enhancement);
        this.tokenId = requireNonNull(tokenId);
        this.amount = amount;
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.spender = requireNonNull(spender);
        this.addressIdConverter = requireNonNull(addressIdConverter);
    }

    @Override
    public @NonNull PricedResult execute() {
        if (tokenId == null) {
            return reversionWith(INVALID_TOKEN_ID, 0L);
        }
        final var spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        final var recordBuilder = systemContractOperations()
                .dispatch(
                        syntheticBurnUnits(tokenId, amount),
                        verificationStrategy,
                        spenderId,
                        TokenBurnRecordBuilder.class);
        final var newTotalSupply = nativeOperations().getToken(tokenId.tokenNum()).totalSupply();
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), 0L));
        } else {
            //@TODO implementation for V1 and V2 versions
            final var encodedOutput = BurnTranslator.BURN_TOKEN_V1.getOutputs()
                    .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()));
            return gasOnly(successResult(encodedOutput, 0L));
        }
    }

    private TransactionBody syntheticBurnUnits(@NonNull final TokenID tokenId, final long amount) {
        return TransactionBody.newBuilder()
                .tokenBurn(TokenBurnTransactionBody.newBuilder()
                        .token(tokenId)
                        .amount(amount)
                        .build()
                ).build();
    }
}
