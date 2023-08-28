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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.service.file.impl.FileServiceImpl.BLOBS_KEY;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.ConfigProviderImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.hapi.utils.sysfiles.validation.ExpectedCustomThrottles;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.ThrottleManager;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple facility that notifies interested parties when a special file is updated.
 *
 * <p>This is a temporary solution. In the future we want to have specific transactions
 * to update the data that is currently transmitted in these files.
 */
public class SystemFileUpdateFacility {

    private static final Logger logger = LogManager.getLogger(SystemFileUpdateFacility.class);
    private static final Set<HederaFunctionality> expectedOps = ExpectedCustomThrottles.ACTIVE_OPS;
    private static final Function<
                    ThrottleDefinitions, com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions>
            toPojo = com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions::fromProto;

    private final ConfigProviderImpl configProvider;
    private final ThrottleManager throttleManager;
    private final ExchangeRateManager exchangeRateManager;

    /**
     * Creates a new instance of this class.
     *
     * @param configProvider the configuration provider
     */
    public SystemFileUpdateFacility(
            @NonNull final ConfigProviderImpl configProvider,
            @NonNull final ThrottleManager throttleManager,
            @NonNull final ExchangeRateManager exchangeRateManager) {
        this.configProvider = requireNonNull(configProvider, "configProvider must not be null");
        this.throttleManager = requireNonNull(throttleManager, " throttleManager must not be null");
        this.exchangeRateManager = requireNonNull(exchangeRateManager, "exchangeRateManager must not be null");
    }

    /**
     * Checks whether the given transaction body is a file update or file append of a special file and eventually
     * notifies the registered facility.
     *
     * @param state the current state (the updated file content needs to be committed to the state)
     * @param txBody the transaction body
     */
    public void handleTxBody(
            @NonNull final HederaState state,
            @NonNull final TransactionBody txBody,
            // TODO: export
            @NonNull SingleTransactionRecordBuilderImpl recordBuilder) {
        requireNonNull(state, "state must not be null");
        requireNonNull(txBody, "txBody must not be null");
        requireNonNull(recordBuilder, "recordBuilder must not be null");

        // Try to extract the file ID from the transaction body, if it is FileUpdate or FileAppend.
        final FileID fileID;
        if (txBody.hasFileUpdate()) {
            fileID = txBody.fileUpdateOrThrow().fileIDOrThrow();
        } else if (txBody.hasFileAppend()) {
            fileID = txBody.fileAppendOrThrow().fileIDOrThrow();
        } else {
            return;
        }

        // Check if the file is a special file
        final var configuration = configProvider.getConfiguration();
        final var ledgerConfig = configuration.getConfigData(LedgerConfig.class);
        final var fileNum = fileID.fileNum();
        if (fileNum > ledgerConfig.numReservedSystemEntities()) {
            return;
        }

        // If it is a special file, call the updater.
        // We load the file only, if there is an updater for it.
        final var config = configuration.getConfigData(FilesConfig.class);
        try {
            if (fileNum == config.addressBook()) {
                logger.error("Update of address book not implemented");
            } else if (fileNum == config.nodeDetails()) {
                logger.error("Update of node details not implemented");
            } else if (fileNum == config.feeSchedules()) {
                logger.error("Update of fee schedules not implemented");
            } else if (fileNum == config.exchangeRates()) {
                exchangeRateManager.update(getFileContent(state, fileID));
            } else if (fileNum == config.networkProperties()) {
                configProvider.update(getFileContent(state, fileID));
            } else if (fileNum == config.hapiPermissions()) {
                logger.error("Update of HAPI permissions not implemented");
            } else if (fileNum == config.throttleDefinitions()) {
                throttleManager.update(getFileContent(state, fileID));
                throttleValidations(recordBuilder);
            } else if (fileNum == config.upgradeFileNumber()) {
                logger.error("Update of file number not implemented");
            }
        } catch (final RuntimeException e) {
            logger.warn(
                    "Exception while calling updater for file {}. " + "If the file is incomplete, this is expected.",
                    fileID,
                    e);
        }
    }

    private void throttleValidations(SingleTransactionRecordBuilderImpl recordBuilder) {
        final var defs = toPojo.apply(throttleManager.throttleDefinitionsProto());
        try {
            checkForMissingExpectedOperations(defs);
            checkForZeroOpsPerSec(defs);
            checkForRepeatedOperations(defs);
        } catch (IllegalStateException e) {
            recordBuilder.status(ResponseCodeEnum.valueOf(e.getMessage()));
        }
    }

    private void checkForMissingExpectedOperations(
            com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions defs) {
        Set<HederaFunctionality> customizedOps = new HashSet<>();
        for (var bucket : defs.getBuckets()) {
            for (var group : bucket.getThrottleGroups()) {
                customizedOps.addAll(group.getOperations());
            }
        }
        if (!expectedOps.equals(EnumSet.copyOf(customizedOps))) {
            throw new IllegalStateException(ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION.name());
        }
    }

    private void checkForZeroOpsPerSec(
            com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions defs) {
        for (var bucket : defs.getBuckets()) {
            for (var group : bucket.getThrottleGroups()) {
                if (group.impliedMilliOpsPerSec() == 0) {
                    throw new IllegalStateException(ResponseCodeEnum.THROTTLE_GROUP_HAS_ZERO_OPS_PER_SEC.name());
                }
            }
        }
    }

    private void checkForRepeatedOperations(
            com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions defs) {
        final Set<HederaFunctionality> seenSoFar = new HashSet<>();
        for (var bucket : defs.getBuckets()) {
            for (var group : bucket.getThrottleGroups()) {
                final var functions = group.getOperations();
                if (!Collections.disjoint(seenSoFar, functions)) {
                    throw new IllegalStateException(ResponseCodeEnum.OPERATION_REPEATED_IN_BUCKET_GROUPS.name());
                }
                seenSoFar.addAll(functions);
            }
        }
    }

    @NonNull
    @VisibleForTesting
    static Bytes getFileContent(@NonNull final HederaState state, @NonNull final FileID fileID) {
        final var states = state.createReadableStates(FileService.NAME);
        final var filesMap = states.<FileID, File>get(BLOBS_KEY);
        final var file = filesMap.get(fileID);
        return file != null ? file.contents() : Bytes.EMPTY;
    }
}
