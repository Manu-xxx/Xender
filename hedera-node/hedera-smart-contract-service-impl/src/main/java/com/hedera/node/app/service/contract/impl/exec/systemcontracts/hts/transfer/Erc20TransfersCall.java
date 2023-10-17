/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Implements the ERC-20 {@code transfer()} and {@code transferFrom()} calls of the HTS contract.
 */
public class Erc20TransfersCall extends AbstractHtsCall {

    private final long amount;

    @Nullable
    private final Address from;

    private final Address to;

    @Nullable
    private final TokenID tokenId;

    private final VerificationStrategy verificationStrategy;
    private final org.hyperledger.besu.datatypes.Address spender;
    private final AddressIdConverter addressIdConverter;

    // too many parameters
    @SuppressWarnings("java:S107")
    public Erc20TransfersCall(
            @NonNull final SystemContractGasCalculator gasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            final long amount,
            @Nullable final Address from,
            @NonNull final Address to,
            @Nullable final TokenID tokenId,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(gasCalculator, enhancement);
        this.amount = amount;
        this.from = from;
        this.to = requireNonNull(to);
        this.tokenId = tokenId;
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.spender = requireNonNull(spender);
        this.addressIdConverter = requireNonNull(addressIdConverter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        // https://eips.ethereum.org/EIPS/eip-20
        // TODO - gas calculation
        if (tokenId == null) {
            return reversionWith(INVALID_TOKEN_ID, 0L);
        }
        final var spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        final var recordBuilder = systemContractOperations()
                .dispatch(
                        syntheticTransferOrTransferFrom(spenderId),
                        verificationStrategy,
                        spenderId,
                        CryptoTransferRecordBuilder.class);
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), 0L));
        } else {
            final var encodedOutput = (from == null)
                    ? Erc20TransfersTranslator.ERC_20_TRANSFER.getOutputs().encodeElements(true)
                    : Erc20TransfersTranslator.ERC_20_TRANSFER_FROM.getOutputs().encodeElements(true);
            return gasOnly(successResult(encodedOutput, 0L));
        }
    }

    private TransactionBody syntheticTransferOrTransferFrom(@NonNull final AccountID spenderId) {
        final var receiverId = addressIdConverter.convertCredit(to);
        final var ownerId = (from == null) ? spenderId : addressIdConverter.convert(from);
        return TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(TokenTransferList.newBuilder()
                                .token(tokenId)
                                .transfers(
                                        AccountAmount.newBuilder()
                                                .accountID(receiverId)
                                                .amount(amount)
                                                .build(),
                                        AccountAmount.newBuilder()
                                                .accountID(ownerId)
                                                .amount(-amount)
                                                .isApproval(!spenderId.equals(ownerId))
                                                .build())
                                .build()))
                .build();
    }
}
