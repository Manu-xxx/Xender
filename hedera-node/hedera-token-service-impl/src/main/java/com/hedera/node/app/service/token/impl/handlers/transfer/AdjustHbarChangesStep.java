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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.AMOUNT_EXCEEDS_ALLOWANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SPENDER_DOES_NOT_HAVE_ALLOWANCE;
import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdjustHbarChangesStep extends BaseTokenHandler implements TransferStep {
    final CryptoTransferTransactionBody op;
    private final AccountID topLevelPayer;

    public AdjustHbarChangesStep(
            @NonNull final CryptoTransferTransactionBody op, @NonNull final AccountID topLevelPayer) {
        requireNonNull(op);
        requireNonNull(topLevelPayer);
        this.op = op;
        this.topLevelPayer = topLevelPayer;
    }

    @Override
    public void doIn(@NonNull final TransferContext transferContext) {
        requireNonNull(transferContext);

        final var accountStore = transferContext.getHandleContext().writableStore(WritableAccountStore.class);
        // Aggregate all the hbar balances from the changes. It also includes allowance transfer amounts
        final Map<AccountID, Long> netHbarTransfers = new LinkedHashMap<>();
        // Allowance transfers is only for negative amounts, it is used to reduce allowance for the spender
        final Map<AccountID, Long> allowanceTransfers = new LinkedHashMap<>();
        for (final var aa : op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(Collections.emptyList())) {
            netHbarTransfers.merge(aa.accountID(), aa.amount(), Long::sum);
            if (aa.isApproval() && aa.amount() < 0) {
                allowanceTransfers.merge(aa.accountID(), aa.amount(), Long::sum);
            }
        }

        modifyAggregatedTransfers(netHbarTransfers, accountStore, transferContext);
        modifyAggregatedAllowances(allowanceTransfers, accountStore, transferContext);
    }

    /**
     * Puts all the aggregated token allowances changes into the accountStore.
     * For isApproval flag to work the spender account who was granted allowance
     * should be the payer for the transaction.
     * @param allowanceTransfers - map of aggregated token allowances to be put into state
     * @param accountStore  - account store
     * @param transferContext - transfer context
     */
    private void modifyAggregatedAllowances(
            @NonNull final Map<AccountID, Long> allowanceTransfers,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final TransferContext transferContext) {
        for (final var entry : allowanceTransfers.entrySet()) {
            final var accountId = entry.getKey();
            final var amount = entry.getValue();

            final var ownerAccount = getIfUsable(
                    accountId, accountStore, transferContext.getHandleContext().expiryValidator(), INVALID_ACCOUNT_ID);
            final var accountCopy = ownerAccount.copyBuilder();

            final var cryptoAllowances = new ArrayList<>(ownerAccount.cryptoAllowancesOrElse(Collections.emptyList()));
            var haveSpenderAllowance = false;

            for (int i = 0; i < cryptoAllowances.size(); i++) {
                final var allowance = cryptoAllowances.get(i);
                final var allowanceCopy = allowance.copyBuilder();
                // If isApproval flag is set then the spender account must have paid for the transaction.
                // The transfer list specifies the owner who granted allowance as sender
                // check if the allowances from the sender account has the payer account as spender
                if (topLevelPayer.equals(allowance.spenderId())) {
                    haveSpenderAllowance = true;
                    final var newAllowanceAmount = allowance.amount() + amount;
                    validateTrue(newAllowanceAmount >= 0, AMOUNT_EXCEEDS_ALLOWANCE);

                    allowanceCopy.amount(newAllowanceAmount);
                    if (newAllowanceAmount != 0) {
                        cryptoAllowances.set(i, allowanceCopy.build());
                    } else {
                        cryptoAllowances.remove(i);
                    }
                    break;
                }
            }
            validateTrue(haveSpenderAllowance, SPENDER_DOES_NOT_HAVE_ALLOWANCE);
            accountCopy.cryptoAllowances(cryptoAllowances);
            accountStore.put(accountCopy.build());
        }
    }

    /**
     * Puts all the aggregated hbar balances changes into the accountStore.
     * @param netHbarTransfers - map of aggregated hbar balances to be put into state
     * @param accountStore - account store
     * @param transferContext - transfer context
     */
    private void modifyAggregatedTransfers(
            @NonNull final Map<AccountID, Long> netHbarTransfers,
            @NonNull final WritableAccountStore accountStore,
            @NonNull final TransferContext transferContext) {
        for (final var entry : netHbarTransfers.entrySet()) {
            final var accountId = entry.getKey();
            final var amount = entry.getValue();
            final var account = getIfUsable(
                    accountId, accountStore, transferContext.getHandleContext().expiryValidator(), INVALID_ACCOUNT_ID);
            final var currentBalance = account.tinybarBalance();
            final var newBalance = currentBalance + amount;
            validateTrue(newBalance >= 0, INSUFFICIENT_ACCOUNT_BALANCE);
            final var copy = account.copyBuilder();
            accountStore.put(copy.tinybarBalance(newBalance).build());
        }
    }
}
