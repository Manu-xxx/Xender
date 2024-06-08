/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow.records;

import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.PRECEDING;
import static com.hedera.node.app.workflows.handle.HandleContextImpl.PrecedingTransactionCategory.LIMITED_CHILD_RECORDS;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provider of the child record builder based on the dispatched child transaction category
 */
@Singleton
public class ChildRecordBuilderFactory {
    private static final Logger logger = LogManager.getLogger(ChildRecordBuilderFactory.class);
    private final ChildRecordInitializer childRecordInitializer;

    @Inject
    public ChildRecordBuilderFactory(final ChildRecordInitializer childRecordInitializer) {
        this.childRecordInitializer = childRecordInitializer;
    }

    /**
     * Provides the record builder for the child transaction category and initializes it.
     * The record builder is created based on the child category and the reversing behavior.
     * @param txnInfo the transaction info
     * @param recordListBuilder the record list builder
     * @param configuration the configuration
     * @param childCategory the child category
     * @param reversingBehavior the reversing behavior
     * @param customizer the externalized record customizer
     * @return the record builder
     */
    public SingleTransactionRecordBuilderImpl recordBuilderFor(
            TransactionInfo txnInfo,
            final RecordListBuilder recordListBuilder,
            final Configuration configuration,
            HandleContext.TransactionCategory childCategory,
            SingleTransactionRecordBuilderImpl.ReversingBehavior reversingBehavior,
            @Nullable final ExternalizedRecordCustomizer customizer) {
        logger.info(
                "Creating record builder for child category: {} and reversing behavior: {} , "
                        + "using recordListBuilder {}",
                childCategory,
                reversingBehavior,
                System.identityHashCode(recordListBuilder));
        final SingleTransactionRecordBuilderImpl recordBuilder;
        if (childCategory == PRECEDING) {
            recordBuilder = switch (reversingBehavior) {
                case REMOVABLE -> recordListBuilder.addRemovablePreceding(configuration);
                case REVERSIBLE -> recordListBuilder.addReversiblePreceding(configuration);
                default -> recordListBuilder.addPreceding(configuration, LIMITED_CHILD_RECORDS);};
        } else if (childCategory == CHILD) {
            recordBuilder = switch (reversingBehavior) {
                case REMOVABLE -> recordListBuilder.addRemovableChildWithExternalizationCustomizer(
                        configuration, requireNonNull(customizer));
                case REVERSIBLE -> recordListBuilder.addChild(configuration, childCategory);
                default -> throw new IllegalArgumentException("Unsupported reversing behavior: " + reversingBehavior
                        + " for child category: " + childCategory);};
        } else {
            recordBuilder = recordListBuilder.addChild(configuration, childCategory);
        }
        childRecordInitializer.initializeUserRecord(recordBuilder, txnInfo);
        return recordBuilder;
    }
}
