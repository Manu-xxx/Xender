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
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implements the ERC-721 {@code transferFrom()} call of the HTS contract.
 */
public class Erc721TransferFromCall extends AbstractHtsCall {

    private final long serialNo;
    private final Address from;
    private final Address to;
    private final TokenID tokenId;
    private final VerificationStrategy verificationStrategy;
    private final org.hyperledger.besu.datatypes.Address spender;
    private final AddressIdConverter addressIdConverter;

    // too many parameters
    @SuppressWarnings("java:S107")
    public Erc721TransferFromCall(
            final long serialNo,
            @NonNull final Address from,
            @NonNull final Address to,
            @NonNull final TokenID tokenId,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(gasCalculator, enhancement);
        this.from = requireNonNull(from);
        this.to = requireNonNull(to);
        this.tokenId = tokenId;
        this.spender = requireNonNull(spender);
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.addressIdConverter = requireNonNull(addressIdConverter);
        this.serialNo = serialNo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        // https://eips.ethereum.org/EIPS/eip-721
        // TODO - gas calculation
        if (tokenId == null) {
            return reversionWith(INVALID_TOKEN_ID, 0L);
        }
        final var spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        final var recordBuilder = systemContractOperations()
                .dispatch(
                        syntheticTransfer(spenderId),
                        verificationStrategy,
                        spenderId,
                        CryptoTransferRecordBuilder.class);
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), 0L));
        } else {
            return gasOnly(successResult(
                    Erc721TransferFromTranslator.ERC_721_TRANSFER_FROM
                            .getOutputs()
                            .encodeElements(),
                    0L));
        }
    }

    private TransactionBody syntheticTransfer(@NonNull final AccountID spenderId) {
        final var ownerId = addressIdConverter.convert(from);
        final var receiverId = addressIdConverter.convertCredit(to);
        return TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(TokenTransferList.newBuilder()
                                .token(tokenId)
                                .nftTransfers(NftTransfer.newBuilder()
                                        .serialNumber(serialNo)
                                        .senderAccountID(ownerId)
                                        .receiverAccountID(receiverId)
                                        .isApproval(!spenderId.equals(ownerId))
                                        .build())
                                .build()))
                .build();
    }
}
