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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;

/**
 * Translates setApprovalForAll call to the HTS system contract. There are no special cases for
 * these calls, so the returned {@link HtsCall} is simply an instance of {@link DispatchForResponseCodeHtsCall}.
 */
public class SetApprovalForAllTranslator extends AbstractHtsCallTranslator {

    public static final Function SET_APPROVAL_FOR_ALL =
            new Function("setApprovalForAll(address,address,bool)", ReturnTypes.INT);

    private final SetApprovalForAllDecoder decoder;

    @Inject
    public SetApprovalForAllTranslator(final SetApprovalForAllDecoder decoder) {
        this.decoder = decoder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), SET_APPROVAL_FOR_ALL.selector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall<>(
                attempt, bodyForClassic(attempt), SingleTransactionRecordBuilder.class);
    }

    private TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        return decoder.decodeSetApprovalForAll(attempt);
    }
}
