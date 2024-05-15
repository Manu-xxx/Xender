/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.wiring;

import static com.swirlds.common.wiring.model.diagram.HyperlinkBuilder.platformCoreHyperlink;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.stream.RunningEventHashOverride;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.platform.StateSigner;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.StateSavingResult;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import com.swirlds.platform.util.HashLogger;
import com.swirlds.platform.wiring.components.StateAndRound;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The {@link TaskScheduler}s used by the platform.
 * <p>
 * This class is being phased out. Do not add additional schedulers to this class!
 *
 * @param signedStateFileManagerScheduler           the scheduler for the signed state file manager
 * @param stateSignerScheduler                      the scheduler for the state signer
 * @param pcesReplayerScheduler                     the scheduler for the pces replayer
 * @param consensusRoundHandlerScheduler            the scheduler for the consensus round handler
 * @param runningHashUpdateScheduler                the scheduler for the running hash updater
 * @param issHandlerScheduler                       the scheduler for the iss handler
 * @param hashLoggerScheduler                       the scheduler for the hash logger
 * @param latestCompleteStateNotifierScheduler      the scheduler for the latest complete state notifier
 */
public record PlatformSchedulers(
        @NonNull TaskScheduler<StateSavingResult> signedStateFileManagerScheduler,
        @NonNull TaskScheduler<ConsensusTransactionImpl> stateSignerScheduler,
        @NonNull TaskScheduler<NoInput> pcesReplayerScheduler,
        @NonNull TaskScheduler<StateAndRound> consensusRoundHandlerScheduler,
        @NonNull TaskScheduler<RunningEventHashOverride> runningHashUpdateScheduler,
        @NonNull TaskScheduler<Void> issHandlerScheduler,
        @NonNull TaskScheduler<Void> hashLoggerScheduler,
        @NonNull TaskScheduler<Void> latestCompleteStateNotifierScheduler) {

    /**
     * Instantiate the schedulers for the platform, for the given wiring model
     *
     * @param context              the platform context
     * @param model                the wiring model
     * @return the instantiated platform schedulers
     */
    public static PlatformSchedulers create(@NonNull final PlatformContext context, @NonNull final WiringModel model) {
        final PlatformSchedulersConfig config =
                context.getConfiguration().getConfigData(PlatformSchedulersConfig.class);

        return new PlatformSchedulers(
                model.schedulerBuilder("signedStateFileManager")
                        .withType(config.signedStateFileManagerSchedulerType())
                        .withUnhandledTaskCapacity(config.signedStateFileManagerUnhandledCapacity())
                        .withUnhandledTaskMetricEnabled(true)
                        .withHyperlink(platformCoreHyperlink(SignedStateFileManager.class))
                        .build()
                        .cast(),
                model.schedulerBuilder("stateSigner")
                        .withType(config.stateSignerSchedulerType())
                        .withUnhandledTaskCapacity(config.stateSignerUnhandledCapacity())
                        .withUnhandledTaskMetricEnabled(true)
                        .withHyperlink(platformCoreHyperlink(StateSigner.class))
                        .build()
                        .cast(),
                model.schedulerBuilder("pcesReplayer")
                        .withType(TaskSchedulerType.DIRECT)
                        .withHyperlink(platformCoreHyperlink(PcesReplayer.class))
                        .build()
                        .cast(),
                // the literal "consensusRoundHandler" is used by the app to log on the transaction handling thread.
                // Do not modify, unless you also change the TRANSACTION_HANDLING_THREAD_NAME constant
                model.schedulerBuilder("consensusRoundHandler")
                        .withType(config.consensusRoundHandlerSchedulerType())
                        .withUnhandledTaskCapacity(config.consensusRoundHandlerUnhandledCapacity())
                        .withUnhandledTaskMetricEnabled(true)
                        .withBusyFractionMetricsEnabled(true)
                        .withFlushingEnabled(true)
                        .withSquelchingEnabled(true)
                        .withHyperlink(platformCoreHyperlink(ConsensusRoundHandler.class))
                        .build()
                        .cast(),
                model.schedulerBuilder("RunningEventHashOverride")
                        .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                        .build()
                        .cast(),
                model.schedulerBuilder("issHandler")
                        .withType(TaskSchedulerType.DIRECT)
                        .withHyperlink(platformCoreHyperlink(IssHandler.class))
                        .build()
                        .cast(),
                model.schedulerBuilder("hashLogger")
                        .withType(config.hashLoggerSchedulerType())
                        .withUnhandledTaskCapacity(config.hashLoggerUnhandledTaskCapacity())
                        .withUnhandledTaskMetricEnabled(true)
                        .withHyperlink(platformCoreHyperlink(HashLogger.class))
                        .build()
                        .cast(),
                model.schedulerBuilder("latestCompleteStateNotifier")
                        .withType(TaskSchedulerType.SEQUENTIAL_THREAD)
                        .withUnhandledTaskCapacity(config.completeStateNotifierUnhandledCapacity())
                        .withUnhandledTaskMetricEnabled(true)
                        .build()
                        .cast());
    }
}
