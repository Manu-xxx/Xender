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

package com.hedera.node.app.throttle.impl;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.node.app.service.mono.utils.MiscUtils.safeResetThrottles;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.congestion.CongestionLevelStarts;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshot;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.congestion.MonoMultiplierSources;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.service.mono.fees.congestion.MultiplierSources;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.CongestionThrottleService;
import com.hedera.node.app.throttle.HandleThrottleAccumulator;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Arrays;
import javax.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implementation of {@link NetworkUtilizationManager}  that delegates to injected {@link HandleThrottleAccumulator} and {@link
 * MultiplierSources}.
 */
public class NetworkUtilizationManagerImpl implements NetworkUtilizationManager {
    private static final Logger log = LogManager.getLogger(NetworkUtilizationManagerImpl.class);
    // Used to update network utilization after a user-submitted transaction fails the signature
    // validity
    // screen; the stand-in is a CryptoTransfer because it best reflects the work done charging fees
    static final TransactionInfo STAND_IN_CRYPTO_TRANSFER = new TransactionInfo(
            Transaction.DEFAULT,
            TransactionBody.DEFAULT,
            TransactionID.DEFAULT,
            AccountID.DEFAULT,
            SignatureMap.DEFAULT,
            Bytes.EMPTY,
            CRYPTO_TRANSFER);

    private final HandleThrottleAccumulator handleThrottling;

    private final MonoMultiplierSources multiplierSources;

    @Inject
    public NetworkUtilizationManagerImpl(
            @NonNull final HandleThrottleAccumulator handleThrottling,
            @NonNull final MonoMultiplierSources multiplierSources) {
        this.handleThrottling = requireNonNull(handleThrottling, "handleThrottling must not be null");
        this.multiplierSources = requireNonNull(multiplierSources, "multiplierSources must not be null");
    }

    @Override
    public void trackTxn(@NonNull final TransactionInfo txnInfo, Instant consensusTime, HederaState state) {
        track(txnInfo, consensusTime, state);
    }

    @Override
    public void trackFeePayments(Instant consensusNow, HederaState state) {
        track(STAND_IN_CRYPTO_TRANSFER, consensusNow, state);
    }

    @Override
    public void resetFrom(final @NonNull HederaState state) {
        final var activeThrottles = handleThrottling.allActiveThrottles();
        final var states = state.createReadableStates(CongestionThrottleService.NAME);
        final var throttleSnapshots = states.<ThrottleUsageSnapshots>getSingleton(
                        CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY)
                .get();
        final var usageSnapshots = throttleSnapshots.tpsThrottles().stream()
                .map(this::fromPbj)
                .toArray(DeterministicThrottle.UsageSnapshot[]::new);

        if (activeThrottles.size() != usageSnapshots.length) {
            log.warn(
                    "There are {} active throttles, but {} usage snapshots from saved state. "
                            + "Not performing a reset!",
                    activeThrottles.size(),
                    usageSnapshots.length);
            return;
        }

        safeResetThrottles(activeThrottles, usageSnapshots, "handle");

        final var activeGasThrottle = handleThrottling.gasLimitThrottle();
        final var currGasThrottleUsageSnapshot = activeGasThrottle.usageSnapshot();
        try {
            final var gasThrottleUsageSnapshot = fromPbj(throttleSnapshots.gasThrottle());
            activeGasThrottle.resetUsageTo(gasThrottleUsageSnapshot);
            log.info("Reset {} with saved gas throttle usage snapshot", gasThrottleUsageSnapshot);
        } catch (final IllegalArgumentException e) {
            log.warn(String.format(
                    "Saved gas throttle usage snapshot was not compatible with the"
                            + " corresponding active throttle (%s); not performing a reset!",
                    e.getMessage()));
            activeGasThrottle.resetUsageTo(currGasThrottleUsageSnapshot);
        }

        final var congestionLevelStarts = states.<CongestionLevelStarts>getSingleton(
                        CongestionThrottleService.CONGESTION_LEVEL_STARTS_STATE_KEY)
                .get();
        if (!congestionLevelStarts.genericLevelStarts().isEmpty()) {
            final var genericLevelStarts = congestionLevelStarts.genericLevelStarts().stream()
                    .map(ts -> Instant.ofEpochSecond(ts.seconds(), ts.nanos()))
                    .toArray(Instant[]::new);
            multiplierSources.resetGenericCongestionLevelStarts(genericLevelStarts);
        }

        if (!congestionLevelStarts.gasLevelStarts().isEmpty()) {
            final var gasLevelStarts = congestionLevelStarts.gasLevelStarts().stream()
                    .map(ts -> Instant.ofEpochSecond(ts.seconds(), ts.nanos()))
                    .toArray(Instant[]::new);
            multiplierSources.resetGasCongestionLevelStarts(gasLevelStarts);
        }
    }

    @Override
    public void saveTo(final @NonNull HederaState state) {
        final var states = state.createWritableStates(CongestionThrottleService.NAME);
        final var throttleSnapshotsState = states.<ThrottleUsageSnapshots>getSingleton(
                CongestionThrottleService.THROTTLE_USAGE_SNAPSHOTS_STATE_KEY);
        final var tpsThrottleUsageSnapshots = handleThrottling.allActiveThrottles().stream()
                .map(DeterministicThrottle::usageSnapshot)
                .map(this::toPbj)
                .toList();

        final var throttleUsageSnapshots = ThrottleUsageSnapshots.newBuilder()
                .tpsThrottles(tpsThrottleUsageSnapshots)
                .gasThrottle(toPbj(handleThrottling.gasLimitThrottle().usageSnapshot()))
                .build();

        throttleSnapshotsState.put(throttleUsageSnapshots);

        final var congestionLevelStartsState =
                states.<CongestionLevelStarts>getSingleton(CongestionThrottleService.CONGESTION_LEVEL_STARTS_STATE_KEY);
        final var genericCongestionStarts = Arrays.stream(multiplierSources.genericCongestionStarts())
                .map(inst -> new Timestamp(inst.getEpochSecond(), inst.getNano()))
                .toList();
        final var gasCongestionStarts = Arrays.stream(multiplierSources.gasCongestionStarts())
                .map(inst -> new Timestamp(inst.getEpochSecond(), inst.getNano()))
                .toList();

        final var congestionLevelStarts = CongestionLevelStarts.newBuilder()
                .genericLevelStarts(genericCongestionStarts)
                .gasLevelStarts(gasCongestionStarts)
                .build();

        congestionLevelStartsState.put(congestionLevelStarts);
    }

    private DeterministicThrottle.UsageSnapshot fromPbj(ThrottleUsageSnapshot snapshot) {
        final var lastDecisionTime = snapshot.lastDecisionTime();
        return new DeterministicThrottle.UsageSnapshot(
                snapshot.used(), Instant.ofEpochSecond(lastDecisionTime.seconds(), lastDecisionTime.nanos()));
    }

    private ThrottleUsageSnapshot toPbj(DeterministicThrottle.UsageSnapshot snapshot) {
        final var lastDecisionTime = snapshot.lastDecisionTime();
        return new ThrottleUsageSnapshot(
                snapshot.used(), new Timestamp(lastDecisionTime.getEpochSecond(), lastDecisionTime.getNano()));
    }

    private void track(@NonNull TransactionInfo txnInfo, Instant consensusTime, HederaState state) {
        handleThrottling.shouldThrottle(txnInfo, consensusTime, state);
        multiplierSources.updateMultiplier(consensusTime);
    }

    @Override
    public boolean wasLastTxnGasThrottled() {
        return handleThrottling.wasLastTxnGasThrottled();
    }
}
