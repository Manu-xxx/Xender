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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval;

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
import javax.inject.Singleton;

@Singleton
public class GrantApprovalTranslator extends AbstractHtsCallTranslator {
    public static final Function GRANT_APPROVAL = new Function("approve(address,address,uint256)", ReturnTypes.INT_64);
    public static final Function GRANT_APPROVAL_NFT =
            new Function("approveNFT(address,address,uint256)", ReturnTypes.INT_64);
    private final GrantApprovalDecoder decoder;

    @Inject
    public GrantApprovalTranslator(@NonNull final GrantApprovalDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return matchesClassicSelector(attempt.selector());
    }

    @Override
    public HtsCall callFrom(@NonNull final HtsCallAttempt attempt) {
        return new DispatchForResponseCodeHtsCall<>(
                attempt, bodyForClassic(attempt), SingleTransactionRecordBuilder.class);
    }

    private boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, GRANT_APPROVAL.selector())
                || Arrays.equals(selector, GRANT_APPROVAL_NFT.selector());
    }

    private TransactionBody bodyForClassic(final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), GRANT_APPROVAL.selector())) {
            return decoder.decodeGrantApproval(attempt);
        } else {
            return decoder.decodeGrantApprovalNFT(attempt);
        }
    }
}
