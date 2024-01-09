/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbiConstants;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.LogBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.priorityAddressOf;
import static java.util.Objects.requireNonNull;

public class GrantApprovalLoggingUtils {
    //todo add some docs here!
    public static void logSuccessfulApprove(
            @NonNull final TokenID tokenId ,
            @NonNull final AccountID sender,
            @NonNull final AccountID spender,
            final long amount,
            @NonNull final ReadableAccountStore accountStore,
            @NonNull final MessageFrame frame) {
        requireNonNull(tokenId);
        requireNonNull(frame);
        requireNonNull(sender);
        requireNonNull(spender);
        requireNonNull(accountStore);

        frame.addLog(builderFor(tokenId, sender, spender, accountStore)
                .forDataItem(amount)
                .build());
    }

    private static LogBuilder builderFor(
            @NonNull final TokenID tokenId,
            @NonNull final AccountID senderId,
            @NonNull final AccountID spenderId,
            @NonNull final ReadableAccountStore accountStore) {
        final var tokenAddress = asLongZeroAddress(tokenId.tokenNum());
        final var senderAddress = priorityAddressOf(requireNonNull(accountStore.getAccountById(senderId)));

        final var spenderAccount = accountStore.getAccountById(spenderId);
        final var spenderAddress = spenderAccount != null ? priorityAddressOf(spenderAccount) : Address.EMPTY; //todo - check how to handle nulls here... what if null?
        return LogBuilder.logBuilder()
                .forLogger(tokenAddress)
                .forEventSignature(AbiConstants.APPROVAL_EVENT)
                .forIndexedArgument(senderAddress)
                .forIndexedArgument(spenderAddress);
    }
}
