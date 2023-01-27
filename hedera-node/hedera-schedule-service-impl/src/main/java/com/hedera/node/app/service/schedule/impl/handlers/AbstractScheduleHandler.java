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
package com.hedera.node.app.service.schedule.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static com.hedera.node.app.spi.HapiUtils.QUERY_FUNCTIONS;
import static com.hedera.node.app.spi.HapiUtils.functionOf;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.PreHandleDispatcher;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.meta.InvalidTransactionMetadata;
import com.hedera.node.app.spi.meta.TransactionMetadata;

/**
 * Provides some implementation support needed for both the {@link ScheduleCreateHandler} and {@link
 * ScheduleSignHandler}.
 */
abstract class AbstractScheduleHandler {
    protected TransactionMetadata preHandleScheduledTxn(
            final TransactionBody scheduledTxn,
            final AccountID payerForNested,
            final PreHandleDispatcher dispatcher) {
        final HederaFunctionality scheduledFunction;
        try {
            scheduledFunction = functionOf(scheduledTxn);
        } catch (UnknownHederaFunctionality ex) {
            return new InvalidTransactionMetadata(
                    scheduledTxn, payerForNested, SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        }

        if (!isSchedulable(scheduledFunction)) {
            return new InvalidTransactionMetadata(
                    scheduledTxn, payerForNested, SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        }

        final var meta = dispatcher.dispatch(scheduledTxn, payerForNested);
        if (meta.failed()) {
            return new InvalidTransactionMetadata(
                    scheduledTxn, payerForNested, UNRESOLVABLE_REQUIRED_SIGNERS);
        }
        return meta;
    }

    /**
     * @param functionality any {@link HederaFunctionality}
     * @return true if the functionality could possibly be allowed to be scheduled. Some
     *     functionally may not be in {@link SchedulableTransactionBody} yet but could be in the
     *     future. The scheduling.whitelist configuration property is separate from this and
     *     provides the final list of functionality that can be scheduled.
     */
    public static boolean isSchedulable(final HederaFunctionality functionality) {
        if (functionality == null) {
            return false;
        }
        return switch (functionality) {
            case SCHEDULE_CREATE, SCHEDULE_SIGN -> false;
            default -> !QUERY_FUNCTIONS.contains(functionality);
        };
    }
}
