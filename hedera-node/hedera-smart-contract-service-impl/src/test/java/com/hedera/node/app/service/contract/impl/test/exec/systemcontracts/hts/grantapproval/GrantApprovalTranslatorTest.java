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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.grantapproval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrantApprovalTranslatorTest {

    @Mock
    private HtsCallAttempt attempt;

    private final GrantApprovalDecoder decoder = new GrantApprovalDecoder();
    private GrantApprovalTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new GrantApprovalTranslator(decoder);
    }

    @Test
    void grantApprovalMatches() {
        given(attempt.selector()).willReturn(GrantApprovalTranslator.GRANT_APPROVAL.selector());
        final var matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void grantApprovalNFTMatches() {
        given(attempt.selector()).willReturn(GrantApprovalTranslator.GRANT_APPROVAL_NFT.selector());
        final var matches = subject.matches(attempt);
        assertTrue(matches);
    }

    @Test
    void falseOnInvalidSelector() {
        given(attempt.selector()).willReturn(ClassicTransfersTranslator.CRYPTO_TRANSFER.selector());
        final var matches = subject.matches(attempt);
        assertFalse(matches);
    }
}
