/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation.schedule.txns;

import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.schedule.ScheduleOpsUsage;
import com.hedera.node.app.hapi.utils.exception.InvalidTxBodyException;
import com.hedera.node.app.hapi.utils.fee.SigValueObj;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ScheduleDeleteResourceUsage implements TxnResourceUsageEstimator {
    private final ScheduleOpsUsage scheduleOpsUsage;
    private final GlobalDynamicProperties properties;

    @Inject
    public ScheduleDeleteResourceUsage(
            final ScheduleOpsUsage scheduleOpsUsage, final GlobalDynamicProperties properties) {
        this.scheduleOpsUsage = scheduleOpsUsage;
        this.properties = properties;
    }

    @Override
    public boolean applicableTo(final TransactionBody txn) {
        return txn.hasScheduleDelete();
    }

    @Override
    public FeeData usageGiven(
            final TransactionBody txn, final SigValueObj svo, final StateView view)
            throws InvalidTxBodyException {
        final var op = txn.getScheduleDelete();
        final var sigUsage =
                new SigUsage(
                        svo.getTotalSigCount(), svo.getSignatureSize(), svo.getPayerAcctSigCount());

        final var optionalInfo = view.infoForSchedule(op.getScheduleID());
        if (optionalInfo.isPresent()) {
            final var info = optionalInfo.get();
            return scheduleOpsUsage.scheduleDeleteUsage(
                    txn, sigUsage, info.getExpirationTime().getSeconds());
        } else {
            final long latestExpiry =
                    txn.getTransactionID().getTransactionValidStart().getSeconds()
                            + properties.scheduledTxExpiryTimeSecs();
            return scheduleOpsUsage.scheduleDeleteUsage(txn, sigUsage, latestExpiry);
        }
    }
}
