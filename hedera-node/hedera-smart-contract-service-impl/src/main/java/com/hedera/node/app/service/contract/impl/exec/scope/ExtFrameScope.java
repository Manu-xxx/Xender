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

package com.hedera.node.app.service.contract.impl.exec.scope;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.annotations.TransactionScope;
import com.hedera.node.app.service.contract.impl.state.ScopedEvmFrameState;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import javax.inject.Inject;

/**
 * The "extended" frame scope that a {@link ScopedEvmFrameState} needs to perform all required operations.
 */
@TransactionScope
public class ExtFrameScope {
    private final HandleContext context;

    @Inject
    public ExtFrameScope(@NonNull final HandleContext context) {
        this.context = Objects.requireNonNull(context);
    }

    /**
     * Returns the {@link Account} with the given number.
     *
     * @param number the account number
     * @return the account, or {@code null} if no such account exists
     */
    @Nullable
    public Account getAccount(final long number) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Returns the {@link Token} with the given number.
     *
     * @param number the token number
     * @return the token, or {@code null} if no such token exists
     */
    @Nullable
    public Token getToken(final long number) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Given an EVM address, resolves to the account or contract number (if any) that this address
     * is an alias for.
     *
     * @param evmAddress the EVM address
     * @return the account or contract number, or {@code null} if the address is not an alias
     */
    @Nullable
    public EntityNumber resolveAlias(@NonNull final Bytes evmAddress) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Assigns the given {@code nonce} to the given {@code contractNumber}.
     *
     * @param contractNumber the contract number
     * @param nonce          the new nonce
     * @throws IllegalArgumentException if there is no valid contract with the given {@code contractNumber}
     */
    public void setNonce(final long contractNumber, final long nonce) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Creates a new hollow account with the given EVM address. The implementation of this call should
     * consume a new entity number for the created new account.
     * <p>
     * If this fails due to some non-EVM resource constraint (for example, insufficient preceding child
     * records), returns the corresponding failure code, and {@link ResponseCodeEnum#OK} otherwise.
     *
     * @param evmAddress the EVM address of the new hollow account
     * @return the result of the creation
     */
    public ResponseCodeEnum createHollowAccount(@NonNull final Bytes evmAddress) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Finalizes an existing hollow account with the given address as a contract by setting
     * {@code isContract=true}, {@code key=Key{contractID=...}}, and {@code nonce=1}. As with
     * a "normal" internal {@code CONTRACT_CREATION}, the record of this finalization should
     * only be externalized if the top-level HAPI transaction succeeds.
     *
     * @param evmAddress the EVM address of the hollow account to finalize as a contract
     */
    public void finalizeHollowAccountAsContract(@NonNull final Bytes evmAddress) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Collects the given {@code amount} of fees from the given {@code fromEntityNumber}.
     *
     * @param fromEntityNumber the number of the entity to collect fees from
     * @param amount          the amount of fees to collect
     */
    public void collectFee(final long fromEntityNumber, final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Refunds the given {@code amount} of fees from the given {@code fromEntityNumber}.
     *
     * @param fromEntityNumber the number of the entity to refund fees to
     * @param amount          the amount of fees to collect
     */
    public void refundFee(final long fromEntityNumber, final long amount) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Transfers value from one account or contract to another without creating a record in this {@link ExtWorldScope},
     * performing signature verification for a receiver with {@code receiverSigRequired=true} by giving priority
     * to the included {@code VerificationStrategy}.
     *
     * @param amount           the amount to transfer
     * @param fromEntityNumber the number of the entity to transfer from
     * @param toEntityNumber   the number of the entity to transfer to
     * @param strategy         the {@link VerificationStrategy} to use
     * @return the result of the transfer attempt
     */
    public ResponseCodeEnum transferWithReceiverSigCheck(
            final long amount,
            final long fromEntityNumber,
            final long toEntityNumber,
            @NonNull final VerificationStrategy strategy) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Tracks the deletion of a contract and the beneficiary that should receive any staking awards otherwise
     * earned by the deleted contract.
     *
     * @param deletedNumber the number of the deleted contract
     * @param beneficiaryNumber the number of the beneficiary
     */
    public void trackDeletion(final long deletedNumber, final long beneficiaryNumber) {
        throw new AssertionError("Not implemented");
    }
}
