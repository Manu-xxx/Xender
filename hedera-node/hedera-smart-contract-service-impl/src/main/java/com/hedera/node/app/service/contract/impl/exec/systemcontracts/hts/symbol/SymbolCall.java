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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.symbol;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractTokenViewCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;

/**
 * Implements the token redirect {@code symbol()} call of the HTS system contract.
 */
public class SymbolCall extends AbstractTokenViewCall {

    public SymbolCall(@NonNull final HederaWorldUpdater.Enhancement enhancement, @Nullable final Token token) {
        super(enhancement, token);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected @NonNull HederaSystemContract.FullResult resultOfViewingToken(@NonNull Token token) {
        final var output = SymbolTranslator.SYMBOL.getOutputs().encodeElements(token.symbol());
        return successResult(output, 0L);
    }
}
