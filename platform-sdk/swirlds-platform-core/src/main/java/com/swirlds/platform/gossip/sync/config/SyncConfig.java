/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.sync.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Configuration of the sync gossip algorithm
 *
 * @param syncSleepAfterFailedNegotiation  the number of milliseconds to sleep after a failed negotiation when running
 *                                         the sync-as-a-protocol algorithm
 * @param syncProtocolPermitCount          the number of permits to use when running the sync algorithm
 * @param onePermitPerPeer                 if true, allocate exactly one sync permit per peer, ignoring
 *                                         {@link #syncProtocolPermitCount()}. Otherwise, allocate permits according to
 *                                         {@link #syncProtocolPermitCount()}.
 * @param syncProtocolHeartbeatPeriod      the period at which the heartbeat protocol runs when the sync algorithm is
 *                                         active (milliseconds)
 * @param hashOnGossipThreads              if true, hash events on gossip threads. If false, events are hashed on the
 *                                         event intake thread.
 * @param waitForEventsInIntake            if true, wait for events to be processed by the intake pipeline before
 *                                         starting a new sync
 * @param maximumPermissibleEventsInIntake ignored if {@link #waitForEventsInIntake} is false or if turbo sync in
 *                                         disabled. If true, then if the number of events in the intake pipeline is
 *                                         seen to be greater than this value, then the ongoing sync will be aborted
 *                                         until the number of events in the intake pipeline goes down to zero.
 * @param filterLikelyDuplicates           if true then do not send events that are likely to be duplicates when they
 *                                         are received by the peer, this setting is ignored if turbo sync is enabled
 * @param nonAncestorFilterThreshold       ignored if {@link #filterLikelyDuplicates} is false. For each event that is
 *                                         not a self event and is not an ancestor of a self event, we must know about
 *                                         the event for at least this amount of time before the event is eligible to be
 *                                         sent
 * @param syncKeepalivePeriod              send a keepalive message every this many milliseconds when reading events
 *                                         during a sync
 * @param maxSyncTime                      the maximum amount of time to spend syncing with a peer, syncs that take
 *                                         longer than this will be aborted
 * @param turbo                            if true then use the turbo sync protocol, if false then use the traditional
 *                                         sequential sync protocol
 */
@ConfigData("sync")
public record SyncConfig(
        @ConfigProperty(defaultValue = "25") int syncSleepAfterFailedNegotiation,
        @ConfigProperty(defaultValue = "17") int syncProtocolPermitCount,
        @ConfigProperty(defaultValue = "true") boolean onePermitPerPeer,
        @ConfigProperty(defaultValue = "1000") int syncProtocolHeartbeatPeriod,
        @ConfigProperty(defaultValue = "true") boolean hashOnGossipThreads,
        @ConfigProperty(defaultValue = "true") boolean waitForEventsInIntake,
        @ConfigProperty(defaultValue = "200") int maximumPermissibleEventsInIntake,
        @ConfigProperty(defaultValue = "true") boolean filterLikelyDuplicates,
        @ConfigProperty(defaultValue = "3s") Duration nonAncestorFilterThreshold,
        @ConfigProperty(defaultValue = "500ms") Duration syncKeepalivePeriod,
        @ConfigProperty(defaultValue = "1m") Duration maxSyncTime,
        @ConfigProperty(defaultValue = "true") boolean turbo) {}
