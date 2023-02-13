/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.token.impl.handlers;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.meta.PrehandleHandlerContext;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoDeleteAllowance}.
 */
@Singleton
public class CryptoDeleteAllowanceHandler implements TransactionHandler {
    @Inject
    public CryptoDeleteAllowanceHandler() {}

    /**
     * Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#CryptoDeleteAllowance} transaction,
     * returning the metadata required to, at minimum, validate the signatures of all required
     * signing keys.
     *
     * @param context the {@link PrehandleHandlerContext} which collects all information that will
     *     be passed to {@link #handle(TransactionMetadata)}
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PrehandleHandlerContext context) {
        requireNonNull(context);
        final var op = context.getTxn().getCryptoDeleteAllowance();
        // Every owner whose allowances are being removed should sign, if the owner is not payer
        for (final var allowance : op.getNftAllowancesList()) {
            context.addNonPayerKey(allowance.getOwner(), INVALID_ALLOWANCE_OWNER_ID);
        }
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        requireNonNull(metadata);
        throw new UnsupportedOperationException("Not implemented");
    }
}
