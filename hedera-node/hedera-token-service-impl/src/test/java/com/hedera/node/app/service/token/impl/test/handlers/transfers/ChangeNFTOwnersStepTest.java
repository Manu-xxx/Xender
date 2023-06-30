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

package com.hedera.node.app.service.token.impl.test.handlers.transfers;

import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.NFTOwnersChangeStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.ReplaceAliasesWithIDsInOp;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ChangeNFTOwnersStepTest extends StepsBase {
    @BeforeEach
    public void setUp() {
        super.setUp();
        recordBuilder = new SingleTransactionRecordBuilder(consensusInstant);
        givenTxn();
        refreshWritableStores();
        givenStoresAndConfig(handleContext);
        ensureAliasesStep = new EnsureAliasesStep(body);
        replaceAliasesWithIDsInOp = new ReplaceAliasesWithIDsInOp();
        transferContext = new TransferContextImpl(handleContext);
    }

    @Test
    void replacesAliasesInOp() {
        final var replacedOp = getReplacedOp();
        changeNFTOwnersStep = new NFTOwnersChangeStep(replacedOp, payerId);
        changeNFTOwnersStep.doIn(transferContext);
    }

    CryptoTransferTransactionBody getReplacedOp() {
        givenConditions();
        ensureAliasesStep.doIn(transferContext);
        return replaceAliasesWithIDsInOp.replaceAliasesWithIds(body, transferContext);
    }
}
