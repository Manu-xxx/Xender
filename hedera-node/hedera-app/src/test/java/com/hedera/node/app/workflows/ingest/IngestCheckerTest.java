/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows.ingest;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_ID_FIELD_NOT_ALLOWED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.txns.TransitionLogic;
import com.hedera.node.app.service.mono.txns.TransitionLogicLookup;
import com.hedera.node.app.workflows.common.PreCheckException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Optional;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestCheckerTest {

    @Mock private TransitionLogicLookup transitionLogicLookup;
    @Mock private TransitionLogic transitionLogic;
    @Mock private GlobalDynamicProperties dynamicProperties;

    @Test
    void testDefaultCase() {
        // given
        final var nodeAccountID = AccountID.newBuilder().build();
        final var txBody = TransactionBody.getDefaultInstance();
        when(transitionLogicLookup.lookupFor(HederaFunctionality.NONE, txBody))
                .thenReturn(Optional.of(transitionLogic));
        when(transitionLogic.semanticCheck()).thenReturn(it -> OK);
        final IngestChecker checker =
                new IngestChecker(nodeAccountID, transitionLogicLookup, dynamicProperties);

        // then
        assertDoesNotThrow(
                () -> checker.checkTransactionSemantics(txBody, HederaFunctionality.NONE));
    }

    @Test
    void testCheckTransactionBodyWithDifferentNodeAccountFails() {
        // given
        final var selfAccountID = AccountID.newBuilder().build();
        final var nodeAccountID = AccountID.newBuilder().setAccountNum(42L).build();
        final var txBody = TransactionBody.newBuilder().setNodeAccountID(nodeAccountID).build();
        final IngestChecker checker =
                new IngestChecker(selfAccountID, transitionLogicLookup, dynamicProperties);

        // then
        assertThatThrownBy(
                        () -> checker.checkTransactionSemantics(txBody, HederaFunctionality.NONE))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_NODE_ACCOUNT);
    }

    @Test
    void testCheckTransactionBodyWithTransactionIDScheduledFails() {
        // given
        final var nodeAccountID = AccountID.newBuilder().build();
        final var transactionID = TransactionID.newBuilder().setScheduled(true).build();
        final var txBody = TransactionBody.newBuilder().setTransactionID(transactionID).build();
        final IngestChecker checker =
                new IngestChecker(nodeAccountID, transitionLogicLookup, dynamicProperties);

        // then
        assertThatThrownBy(
                        () -> checker.checkTransactionSemantics(txBody, HederaFunctionality.NONE))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_ID_FIELD_NOT_ALLOWED);
    }

    @Test
    void testCheckTransactionBodyWithTransactionIDIllegalNonceFails() {
        // given
        final var nodeAccountID = AccountID.newBuilder().build();
        final var transactionID = TransactionID.newBuilder().setNonce(42).build();
        final var txBody = TransactionBody.newBuilder().setTransactionID(transactionID).build();
        final IngestChecker checker =
                new IngestChecker(nodeAccountID, transitionLogicLookup, dynamicProperties);

        // then
        assertThatThrownBy(
                        () -> checker.checkTransactionSemantics(txBody, HederaFunctionality.NONE))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_ID_FIELD_NOT_ALLOWED);
    }

    @Test
    void testUnsupportedFunctionality() {
        // given
        final var nodeAccountID = AccountID.newBuilder().build();
        final var txBody = TransactionBody.getDefaultInstance();
        when(transitionLogicLookup.lookupFor(HederaFunctionality.NONE, txBody))
                .thenReturn(Optional.empty());
        final IngestChecker checker =
                new IngestChecker(nodeAccountID, transitionLogicLookup, dynamicProperties);

        // then
        assertThatThrownBy(
                        () -> checker.checkTransactionSemantics(txBody, HederaFunctionality.NONE))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", NOT_SUPPORTED);
    }

    @Test
    void testFailedSemanticCheck() {
        // given
        final var nodeAccountID = AccountID.newBuilder().build();
        final var txBody = TransactionBody.getDefaultInstance();
        when(transitionLogicLookup.lookupFor(HederaFunctionality.NONE, txBody))
                .thenReturn(Optional.of(transitionLogic));
        when(transitionLogic.semanticCheck()).thenReturn(it -> BATCH_SIZE_LIMIT_EXCEEDED);
        final IngestChecker checker =
                new IngestChecker(nodeAccountID, transitionLogicLookup, dynamicProperties);

        // then
        assertThatThrownBy(
                        () -> checker.checkTransactionSemantics(txBody, HederaFunctionality.NONE))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", BATCH_SIZE_LIMIT_EXCEEDED);
    }

    @Disabled("Needs to be implemented")
    @Test
    void testTokenAccountWipe() {
        // TODO: Implement once this code path does not relay on static code
        fail("Test not implemented");
    }
}
