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

package com.hedera.node.app.spi.config;

import java.util.List;

/**
 * This class contains the properties that are part of the {@code NodeLocalProperties} class in the mono-service
 * module.
 */
public record NodeConfig(
        int port,
        int tlsPort,
        long hapiOpStatsUpdateIntervalMs,
        long entityUtilStatsUpdateIntervalMs,
        long throttleUtilStatsUpdateIntervalMs,
        Profile activeProfile,
        double statsSpeedometerHalfLifeSecs,
        double statsRunningAvgHalfLifeSecs,
        String recordLogDir,
        long recordLogPeriod,
        boolean recordStreamEnabled,
        int recordStreamQueueCapacity,
        int queryBlobLookupRetries,
        long nettyProdKeepAliveTime,
        String nettyTlsCrtPath,
        String nettyTlsKeyPath,
        long nettyProdKeepAliveTimeout,
        long nettyMaxConnectionAge,
        long nettyMaxConnectionAgeGrace,
        long nettyMaxConnectionIdle,
        int nettyMaxConcurrentCalls,
        int nettyFlowControlWindow,
        String devListeningAccount,
        boolean devOnlyDefaultNodeListens,
        String accountsExportPath,
        boolean exportAccountsOnStartup,
        Profile nettyMode,
        int nettyStartRetries,
        long nettyStartRetryIntervalMs,
        int numExecutionTimesToTrack,
        int issResetPeriod,
        int issRoundsToLog,
        int prefetchQueueCapacity,
        int prefetchThreadPoolSize,
        int prefetchCodeCacheTtlSecs,
        List<String> consThrottlesToSample,
        List<String> hapiThrottlesToSample,
        String sidecarDir,
        int workflowsPort,
        int workflowsTlsPort) {
}
