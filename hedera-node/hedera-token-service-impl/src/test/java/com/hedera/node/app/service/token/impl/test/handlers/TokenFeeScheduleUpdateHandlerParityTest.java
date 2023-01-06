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
package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils.txnFrom;
import static com.hedera.test.factories.scenarios.TokenFeeScheduleUpdateScenarios.*;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenFeeScheduleUpdateHandler;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.test.utils.AdapterUtils;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TokenFeeScheduleUpdateHandlerParityTest {

    private final TokenFeeScheduleUpdateHandler subject = new TokenFeeScheduleUpdateHandler();

    private AccountKeyLookup keyLookup;
    private ReadableTokenStore readableTokenStore;

    @BeforeEach
    void setUp() {
        final var now = Instant.now();
        keyLookup = AdapterUtils.wellKnownKeyLookupAt(now);
        readableTokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt(now);
    }

    @Test
    void tokenFeeScheduleUpdateNonExistingToken() {
        final var txn = txnFrom(UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST);
        final var meta =
                subject.preHandle(
                        txn, txn.getTransactionID().getAccountID(), keyLookup, readableTokenStore);

        assertTrue(meta.failed());
        assertEquals(INVALID_TOKEN_ID, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(Collections.emptyList(), meta.requiredNonPayerKeys());
    }

    @Test
    void tokenFeeScheduleUpdateTokenWithoutFeeScheduleKey() {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY);
        final var meta =
                subject.preHandle(
                        txn, txn.getTransactionID().getAccountID(), keyLookup, readableTokenStore);

        // may look odd, but is intentional --- we fail in the handle(), not in preHandle()
        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(Collections.emptyList(), meta.requiredNonPayerKeys());
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeySigReqFeeCollector() {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ);
        final var meta =
                subject.preHandle(
                        txn, txn.getTransactionID().getAccountID(), keyLookup, readableTokenStore);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(2, meta.requiredNonPayerKeys().size());
        assertThat(
                sanityRestored(meta.requiredNonPayerKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeySigNotReqFeeCollector() {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_NO_SIG_REQ);
        final var meta =
                subject.preHandle(
                        txn, txn.getTransactionID().getAccountID(), keyLookup, readableTokenStore);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(1, meta.requiredNonPayerKeys().size());
        assertThat(
                sanityRestored(meta.requiredNonPayerKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void
            tokenFeeScheduleUpdateWithFeeScheduleKeyAndOneSigReqFeeCollectorAndAnotherSigNonReqFeeCollector() {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ);
        final var meta =
                subject.preHandle(
                        txn, txn.getTransactionID().getAccountID(), keyLookup, readableTokenStore);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertEquals(2, meta.requiredNonPayerKeys().size());
        assertThat(
                sanityRestored(meta.requiredNonPayerKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorAsPayerAndSigReq() {
        final var txn =
                txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER);
        final var meta =
                subject.preHandle(
                        txn, txn.getTransactionID().getAccountID(), keyLookup, readableTokenStore);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), RECEIVER_SIG_KT.asKey());
        assertEquals(1, meta.requiredNonPayerKeys().size());
        assertThat(
                sanityRestored(meta.requiredNonPayerKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeyAndFeeCollectorAsPayer() {
        final var txn =
                txnFrom(
                        UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_NO_SIG_REQ_AND_AS_PAYER);
        final var meta =
                subject.preHandle(
                        txn, txn.getTransactionID().getAccountID(), keyLookup, readableTokenStore);

        assertFalse(meta.failed());
        assertEquals(OK, meta.status());
        assertEquals(List.of(NO_RECEIVER_SIG_KT.asKey()), sanityRestored(List.of(meta.payerKey())));
        assertEquals(1, meta.requiredNonPayerKeys().size());
        assertThat(
                sanityRestored(meta.requiredNonPayerKeys()),
                contains(TOKEN_FEE_SCHEDULE_KT.asKey()));
    }

    @Test
    void tokenFeeScheduleUpdateWithFeeScheduleKeyAndInvalidFeeCollector() {
        final var txn = txnFrom(UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR);
        final var meta =
                subject.preHandle(
                        txn, txn.getTransactionID().getAccountID(), keyLookup, readableTokenStore);

        assertTrue(meta.failed());
        assertEquals(INVALID_CUSTOM_FEE_COLLECTOR, meta.status());
    }
}
