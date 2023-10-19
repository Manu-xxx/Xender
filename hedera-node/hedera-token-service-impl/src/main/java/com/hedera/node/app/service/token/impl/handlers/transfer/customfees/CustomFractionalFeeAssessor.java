/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_MUST_BE_POSITIVE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.ADJUSTMENTS_MAP_FACTORY;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.asFixedFee;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.getFungibleTokenCredits;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.AdjustmentUtils.safeFractionMultiply;
import static com.hedera.node.app.service.token.impl.handlers.transfer.customfees.CustomFeeExemptions.isPayerExempt;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.FractionalFee;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Assesses fractional custom fees for given token transfer.
 * All fractional fees, that are not netOfTransfers will manipulate the given transaction body.
 * If netOfTransfers flag is set to false, the custom fee si reclaimed from the credits in
 * given transaction body.
 * If netOfTransfers flag is set to true the sender will pay the custom fees.Else the receivers will pay custom fees This means that the fee will be charged from the sender account. This is done to avoid
 * manipulate the given transaction
 */
@Singleton
public class CustomFractionalFeeAssessor {
    private final CustomFixedFeeAssessor fixedFeeAssessor;

    @Inject
    public CustomFractionalFeeAssessor(CustomFixedFeeAssessor fixedFeeAssessor) {
        this.fixedFeeAssessor = fixedFeeAssessor;
    }

    /**
     * Calculates the custom fee amount that should be paid for given fraction fee.
     * If the fee is netOfTransfers the sender will pay the fee, otherwise the receiver(s) will effectively pay the fee,
     * as they are the accounts whose balances will be lower than if the fee had not existed.
     * If netOfTransfers is true the assessed fee will be accumulated for next level transaction body.
     * If netOfTransfers is false the assessed fee will be reclaimed from the credits in given transaction body.
     * @param feeMeta the fee meta
     * @param sender the sender, who might be payer for the fee if netOfTransfers is true
     * @param result the result
     */
    // Suppressing the warning about using two "continue" statements
    @SuppressWarnings("java:S135")
    public void assessFractionalFees(
            @NonNull final CustomFeeMeta feeMeta,
            @NonNull final AccountID sender,
            @NonNull final AssessmentResult result) {
        final var denom = feeMeta.tokenId();

        final var nonMutableInputTokenTransfers = result.getImmutableInputTokenAdjustments();
        final var mutableInputTokenTransfers = result.getMutableInputTokenAdjustments();

        // get the initial units for this token change from given input.
        // This is needed to see the fraction of the adjustment to be charged as custom fee
        final var initialAdjustment = nonMutableInputTokenTransfers.get(denom).get(sender);
        // custom fees can't be assessed for credits
        validateTrue(initialAdjustment < 0, CUSTOM_FEE_MUST_BE_POSITIVE);

        var unitsLeft = -initialAdjustment;
        final var creditsForToken = getFungibleTokenCredits(nonMutableInputTokenTransfers.get(denom));
        final var effectivePayerAccounts = creditsForToken.keySet();

        for (final var fee : feeMeta.customFees()) {
            final var collector = fee.feeCollectorAccountId();
            // If the collector 0.0.C for a fractional fee is trying to send X units to
            // a receiver 0.0.R, then we want to let all X units go to 0.0.R, instead of
            // reclaiming some fraction of them
            if (!fee.fee().kind().equals(CustomFee.FeeOneOfType.FRACTIONAL_FEE) || sender.equals(collector)) {
                continue;
            }
            final var fractionalFee = fee.fractionalFeeOrThrow();
            final var filteredCredits = filteredByExemptions(creditsForToken, feeMeta, fee);
            if (filteredCredits.isEmpty()) {
                continue;
            }

            boolean cont = false;
            for (final var acc : effectivePayerAccounts) {
                if (isPayerExempt(feeMeta, fee, acc)) cont = true;
            }
            if (cont) continue;

            // calculate amount that should be paid for fractional custom fee
            var assessedAmount = amountOwed(unitsLeft, fractionalFee);

            // If it is netOfTransfers the sender will pay the fee, otherwise the receiver will pay the fee
            if (fractionalFee.netOfTransfers()) {
                final var addedFee =
                        asFixedFee(assessedAmount, denom, fee.feeCollectorAccountId(), fee.allCollectorsAreExempt());
                fixedFeeAssessor.assessFixedFee(feeMeta, sender, addedFee, result);
            } else {
                // amount that should be deducted from the credits to token
                // Inside this reclaim there will be debits to the input transaction
                final long exemptAmount = reclaim(assessedAmount, filteredCredits);
                // debits from the input transaction should be adjusted
                adjustInputTokenTransfersWithReclaimAmounts(mutableInputTokenTransfers, denom, filteredCredits);

                assessedAmount -= exemptAmount;
                unitsLeft -= assessedAmount;
                validateTrue(unitsLeft >= 0, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

                // make credit to the collector
                final var map =
                        result.getMutableInputTokenAdjustments().computeIfAbsent(denom, ADJUSTMENTS_MAP_FACTORY);
                map.merge(collector, assessedAmount, Long::sum);
                result.getMutableInputTokenAdjustments().put(denom, map);

                final var finalEffPayerNums =
                        (filteredCredits == creditsForToken) ? effectivePayerAccounts : filteredCredits.keySet();
                final var finalEffPayerNumsArray = new AccountID[finalEffPayerNums.size()];

                // Add assessed custom fees to the result. This is needed to build transaction record
                result.addAssessedCustomFee(AssessedCustomFee.newBuilder()
                        .effectivePayerAccountId(finalEffPayerNums.toArray(finalEffPayerNumsArray))
                        .feeCollectorAccountId(collector)
                        .tokenId(denom)
                        .amount(assessedAmount)
                        .build());
            }
        }
    }

    /**
     * For a given input token transfers from transaction body, if the fractional fee has to be
     * adjusted from credits, adjusts the given transaction body with the adjustments
     * @param mutableInputTokenAdjustments the input token adjustments from given transaction body
     * @param denom the token id
     * @param filteredCredits the credits that should be adjusted
     */
    private void adjustInputTokenTransfersWithReclaimAmounts(
            @NonNull final Map<TokenID, Map<AccountID, Long>> mutableInputTokenAdjustments,
            @NonNull final TokenID denom,
            @NonNull final Map<AccountID, Long> filteredCredits) {
        // if we reached here it means there are credits for the token
        final var map = mutableInputTokenAdjustments.get(denom);
        for (final var entry : filteredCredits.entrySet()) {
            final var account = entry.getKey();
            final var amount = entry.getValue();
            map.put(account, amount);
        }
        mutableInputTokenAdjustments.put(denom, map);
    }

    /**
     * From the given credits, filters all credits whose payer is not exempt from custom fee.
     * Returns credits back if there are no credits whose payer is not exempt from custom fee.
     * If all credits are exempt from custom fee, returns empty map
     * @param creditsForToken the credits for a token
     * @param feeMeta the fee meta
     * @param fee the custom fee
     * @return the filtered credits whose payer is not exempt from custom fee
     */
    private Map<AccountID, Long> filteredByExemptions(
            @NonNull final Map<AccountID, Long> creditsForToken,
            @NonNull final CustomFeeMeta feeMeta,
            @NonNull final CustomFee fee) {
        final var filteredCredits = new HashMap<AccountID, Long>();
        for (final var entry : creditsForToken.entrySet()) {
            final var account = entry.getKey();
            final var amount = entry.getValue();
            if (!isPayerExempt(feeMeta, fee, account)) {
                filteredCredits.put(account, amount);
            }
        }
        return !filteredCredits.isEmpty() ? filteredCredits : creditsForToken;
    }

    /**
     * Calculates the amount owned to be paid as fractional custom fee
     * @param givenUnits  units transferred in the transaction
     * @param fractionalFee the fractional fee
     * @return the amount owned to be paid as fractional custom fee
     */
    private long amountOwed(final long givenUnits, @NonNull final FractionalFee fractionalFee) {
        final var numerator = fractionalFee.fractionalAmount().numerator();
        final var denominator = fractionalFee.fractionalAmount().denominator();
        var nominalFee = 0L;
        try {
            nominalFee = safeFractionMultiply(numerator, denominator, givenUnits);
        } catch (final ArithmeticException e) {
            throw new HandleException(CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE);
        }
        long effectiveFee = Math.max(nominalFee, fractionalFee.minimumAmount());
        if (fractionalFee.maximumAmount() > 0) {
            effectiveFee = Math.min(effectiveFee, fractionalFee.maximumAmount());
        }
        return effectiveFee;
    }

    /**
     * Deducts the given amount from the given credits. If there are multiple credits to same account,
     * reclaims proportionally from each credit.
     * @param amount the amount to be reclaimed
     * @param credits the credits to be reclaimed from
     * @return the amount reclaimed
     */
    private long reclaim(final long amount, @NonNull final Map<AccountID, Long> credits) {
        var availableToReclaim = 0L;
        for (final var entry : credits.entrySet()) {
            availableToReclaim += entry.getValue();
            validateTrue(availableToReclaim >= 0, CUSTOM_FEE_OUTSIDE_NUMERIC_RANGE);
        }

        var amountReclaimed = 0L;
        for (final var entry : credits.entrySet()) {
            final var account = entry.getKey();
            final var creditAmount = entry.getValue();
            final var toReclaimHere = safeFractionMultiply(creditAmount, availableToReclaim, amount);
            credits.put(account, creditAmount - toReclaimHere);
            amountReclaimed += toReclaimHere;
        }

        if (amountReclaimed < amount) {
            var leftToReclaim = amount - amountReclaimed;
            for (final var entry : credits.entrySet()) {
                final var account = entry.getKey();
                final var creditAmount = entry.getValue();
                final var toReclaimHere = Math.min(creditAmount, leftToReclaim);
                credits.put(account, creditAmount - toReclaimHere);
                amountReclaimed += toReclaimHere;
                leftToReclaim -= toReclaimHere;
                if (leftToReclaim == 0) {
                    break;
                }
            }
        }
        return amount - amountReclaimed;
    }
}
