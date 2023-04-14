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

import com.hedera.services.stream.proto.SidecarType;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.system.address.Address;
import java.util.Set;

/**
 * This class contains the properties that are part of the {@code GlobalDynamicProperties} class in the mono-service
 * module.
 */
public record GlobalDynamicConfig(
        int maxNftMetadataBytes,
        int maxBatchSizeBurn,
        int maxBatchSizeMint,
        int maxNftTransfersLen,
        int maxBatchSizeWipe,
        long maxNftQueryRange,
        boolean allowTreasuryToOwnNfts,
        int maxTokensPerAccount,
        int maxTokenRelsPerInfoQuery,
        int maxCustomFeesAllowed,
        int maxTokenSymbolUtf8Bytes,
        int maxTokenNameUtf8Bytes,
        int maxFileSizeKb,
        int cacheRecordsTtl,
        int balancesExportPeriodSecs,
        int ratesIntradayChangeLimitPercent,
        long nodeBalanceWarningThreshold,
        String pathToBalancesExportDir,
        boolean shouldExportBalances,
        boolean shouldExportTokenBalances,
        AccountID fundingAccount,
        // Address fundingAccountAddress,
        int maxTransfersLen,
        int maxTokenTransfersLen,
        int maxMemoUtf8Bytes,
        long maxTxnDuration,
        long minTxnDuration,
        int minValidityBuffer,
        long maxGasPerSec,
        // byte[] chainIdBytes,
        // Bytes32 chainIdBytes32,
        long defaultContractLifetime,
        String evmVersion,
        boolean dynamicEvmVersion,
        int feesTokenTransferUsageMultiplier,
        // boolean atLeastOneAutoRenewTargetType,
        // boolean expireAccounts,
        // boolean expireContracts,
        int autoRenewNumberOfEntitiesToScan,
        int autoRenewMaxNumberOfEntitiesToRenewOrDelete,
        long autoRenewGracePeriod,
        long maxAutoRenewDuration,
        long minAutoRenewDuration,
        // Duration grpcMinAutoRenewDuration,
        int localCallEstRetBytes,
        boolean schedulingLongTermEnabled,
        long schedulingMaxTxnPerSecond,
        long schedulingMaxExpirationFutureSeconds,
        int scheduledTxExpiryTimeSecs,
        int messageMaxBytesAllowed,
        long maxPrecedingRecords,
        long maxFollowingRecords,
        Set<HederaFunctionality> schedulingWhitelist,
        Set<HederaFunctionality> systemContractsWithTopLevelSigsAccess,
        // CongestionMultipliers congestionMultipliers,
        int feesMinCongestionPeriod,
        boolean areNftsEnabled,
        long maxNftMints,
        int maxXferBalanceChanges,
        int maxCustomFeeDepth,
        // ScaleFactor nftMintScaleFactor,
        String upgradeArtifactsLoc,
        boolean throttleByGas,
        int contractMaxRefundPercentOfGasLimit,
        long scheduleThrottleMaxGasLimit,
        long htsDefaultGasCost,
        int changeHistorianMemorySecs,
        boolean autoCreationEnabled,
        boolean expandSigsFromImmutableState,
        long maxAggregateContractKvPairs,
        int maxIndividualContractKvPairs,
        int maxMostRecentQueryableRecords,
        int maxAllowanceLimitPerTransaction,
        int maxAllowanceLimitPerAccount,
        boolean exportPrecompileResults,
        boolean create2Enabled,
        boolean redirectTokenCalls,
        boolean enableAllowances,
        boolean limitTokenAssociations,
        boolean enableHTSPrecompileCreate,
        boolean atomicCryptoTransferEnabled,
        // KnownBlockValues knownBlockValues,
        long exchangeRateGasReq,
        long stakingRewardRate,
        long stakingStartThreshold,
        int nodeRewardPercent,
        int stakingRewardPercent,
        boolean contractAutoAssociationsEnabled,
        boolean stakingEnabled,
        long maxDailyStakeRewardThPerH,
        int recordFileVersion,
        int recordSignatureFileVersion,
        long maxNumAccounts,
        long maxNumContracts,
        long maxNumFiles,
        long maxNumTokens,
        long maxNumTokenRels,
        long maxNumTopics,
        long maxNumSchedules,
        boolean utilPrngEnabled,
        Set<SidecarType> enabledSidecars,
        boolean sidecarValidationEnabled,
        boolean requireMinStakeToReward,
        // Map<Long, Long> nodeMaxMinStakeRatios,
        int sidecarMaxSizeMb,
        boolean itemizeStorageFees,
        // ContractStoragePriceTiers storagePriceTiers,
        boolean compressRecordFilesOnCreation,
        boolean tokenAutoCreationsEnabled,
        boolean doTraceabilityExport,
        boolean compressAccountBalanceFilesOnCreation,
        long traceabilityMaxExportsPerConsSec,
        long traceabilityMinFreeToUsedGasThrottleRatio,
        boolean lazyCreationEnabled,
        boolean cryptoCreateWithAliasEnabled,
        boolean enforceContractCreationThrottle,
        Set<Address> permittedDelegateCallers,
        // EntityScaleFactors entityScaleFactors,
        long maxNumWithHapiSigsAccess,
        // LegacyContractIdActivations legacyContractIdActivations,
        Set<Address> contractsWithSpecialHapiSigsAccess) {
}
