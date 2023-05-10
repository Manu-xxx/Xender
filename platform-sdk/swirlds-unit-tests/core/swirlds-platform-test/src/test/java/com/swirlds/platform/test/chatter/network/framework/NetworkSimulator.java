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

package com.swirlds.platform.test.chatter.network.framework;

import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.common.utility.DurationUtils;
import com.swirlds.platform.test.simulated.config.NetworkConfig;
import java.time.Duration;
import java.time.Instant;

/**
 * Executes a simulated chatter network
 */
public class NetworkSimulator {

    /**
     * Simulates a chatter network.
     *
     * @param network the network to use in the simulation
     * @param params  defines the parameters of the simulation
     */
    public static void executeNetworkSimulation(final Network<?> network, final NetworkSimulatorParams params) {
        preflight(network);

        final FakeTime time = params.time();

        maybeUpdateNetworkConfig(network, params);

        System.out.println("Beginning Simulation");

        while (DurationUtils.isLonger(params.simulationTime(), time.elapsed())) {
            advanceNetworkOneStep(network, params);
            maybeUpdateNetworkConfig(network, params);
        }
    }

    private static void maybeUpdateNetworkConfig(final Network<?> network, final NetworkSimulatorParams params) {
        final FakeTime time = params.time();
        Instant lastConfigEffectiveTime = time.now().minusMillis(time.elapsed().toMillis());
        Duration prevConfigDuration = Duration.ZERO;

        for (final NetworkConfig config : params.networkConfigs()) {
            final Instant configEffectiveTime = lastConfigEffectiveTime.plus(prevConfigDuration);

            // if this is the first time step activating this config, move to the next config and return
            if ((time.now().isAfter(configEffectiveTime) || time.now().equals(configEffectiveTime))
                    && time.now().minus(params.simulationStep()).isBefore(configEffectiveTime)) {

                System.out.println("Applying network configuration " + config.name() + " at " + time.elapsed()
                        + " time into the test");
                network.applyConfig(config);
                return;
            }
            prevConfigDuration = config.duration();
        }
    }

    /**
     * Performs all pre-flight actions that must occur before test execution can start.
     *
     * @param network the network to prepare for test execution
     */
    public static void preflight(final Network<?> network) {
        // set communication state to allow chatter in all peers in all nodes
        network.enableChatter();
    }

    /**
     * Advance the network a single time step, including creating events, handling events, and gossiping events.
     *
     * @param network the network of nodes to advance one simulation step
     * @param params  defines the parameters of the simulation
     */
    public static void advanceNetworkOneStep(final Network<?> network, final NetworkSimulatorParams params) {
        network.forEachChatterInstance(chatterInstance -> {
            chatterInstance.maybeCreateEvent();
            chatterInstance.maybeHandleEvents();
            network.gossip().gossipPayloads(chatterInstance.getMessagesToGossip());
        });
        network.gossip().distribute();
        params.time().tick(params.simulationStep());
    }
}
