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

package com.swirlds.platform.config;

import com.swirlds.common.config.BasicCommonConfig;
import com.swirlds.common.config.ConfigurationExtension;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.io.config.RecycleBinConfig;
import com.swirlds.common.io.config.TemporaryFileConfig;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.metrics.platform.prometheus.PrometheusConfig;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.platform.consensus.ConsensusConfig;
import com.swirlds.platform.event.creation.EventCreationConfig;
import com.swirlds.platform.event.preconsensus.PcesConfig;
import com.swirlds.platform.eventhandling.EventConfig;
import com.swirlds.platform.gossip.ProtocolConfig;
import com.swirlds.platform.gossip.chatter.config.ChatterConfig;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.health.OSHealthCheckConfig;
import com.swirlds.platform.network.SocketConfig;
import com.swirlds.platform.system.status.PlatformStatusConfig;
import com.swirlds.platform.uptime.UptimeConfig;
import com.swirlds.platform.wiring.PlatformSchedulersConfig;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Registers configuration types for the platform.
 */
public class PlatformConfigurationExtension implements ConfigurationExtension {

    /**
     * {@inheritDoc}
     */
    @Override
    public void extendConfiguration(@NonNull final ConfigurationBuilder builder) {

        // Please keep lists in this method alphabetized (enforced by unit test).

        // Load Configuration Definitions
        builder.withConfigDataType(AddressBookConfig.class)
                .withConfigDataType(BasicCommonConfig.class)
                .withConfigDataType(BasicConfig.class)
                .withConfigDataType(ChatterConfig.class)
                .withConfigDataType(ConsensusConfig.class)
                .withConfigDataType(CryptoConfig.class)
                .withConfigDataType(EventConfig.class)
                .withConfigDataType(EventCreationConfig.class)
                .withConfigDataType(MerkleDbConfig.class)
                .withConfigDataType(MetricsConfig.class)
                .withConfigDataType(OSHealthCheckConfig.class)
                .withConfigDataType(PathsConfig.class)
                .withConfigDataType(PcesConfig.class)
                .withConfigDataType(PlatformSchedulersConfig.class)
                .withConfigDataType(PlatformStatusConfig.class)
                .withConfigDataType(PrometheusConfig.class)
                .withConfigDataType(ProtocolConfig.class)
                .withConfigDataType(ReconnectConfig.class)
                .withConfigDataType(RecycleBinConfig.class)
                .withConfigDataType(SocketConfig.class)
                .withConfigDataType(StateCommonConfig.class)
                .withConfigDataType(StateConfig.class)
                .withConfigDataType(SyncConfig.class)
                .withConfigDataType(TemporaryFileConfig.class)
                .withConfigDataType(ThreadConfig.class)
                .withConfigDataType(TransactionConfig.class)
                .withConfigDataType(UptimeConfig.class)
                .withConfigDataType(VirtualMapConfig.class);
    }
}
