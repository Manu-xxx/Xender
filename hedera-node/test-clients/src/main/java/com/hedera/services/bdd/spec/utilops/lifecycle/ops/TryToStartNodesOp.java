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

package com.hedera.services.bdd.spec.utilops.lifecycle.ops;

import static com.hedera.services.bdd.junit.hedera.ExternalPath.UPGRADE_ARTIFACTS_DIR;
import static com.hedera.services.bdd.junit.hedera.utils.WorkingDirUtils.JAR_FILE;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.junit.hedera.HederaNode;
import com.hedera.services.bdd.junit.hedera.NodeSelector;
import com.hedera.services.bdd.junit.hedera.subprocess.SubProcessNetwork;
import com.hedera.services.bdd.junit.hedera.subprocess.SubProcessNode;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.lifecycle.AbstractLifecycleOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;

/**
 * Shuts down the selected node or nodes specified by the {@link NodeSelector}.
 */
public class TryToStartNodesOp extends AbstractLifecycleOp {
    private static final Logger log = LogManager.getLogger(TryToStartNodesOp.class);

    public enum UseUpgradeJar {
        YES,
        NO
    }

    public enum ReassignPorts {
        YES,
        NO
    }

    private final UseUpgradeJar useUpgradeJar;
    private final ReassignPorts reassignPorts;

    public TryToStartNodesOp(@NonNull final NodeSelector selector) {
        this(selector, UseUpgradeJar.NO, ReassignPorts.NO);
    }

    public TryToStartNodesOp(
            @NonNull final NodeSelector selector,
            @NonNull final UseUpgradeJar useUpgradeJar,
            @NonNull final ReassignPorts reassignPorts) {
        super(selector);
        this.useUpgradeJar = requireNonNull(useUpgradeJar);
        this.reassignPorts = requireNonNull(reassignPorts);
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        if (reassignPorts == ReassignPorts.YES) {
            if (!(spec.targetNetworkOrThrow() instanceof SubProcessNetwork subProcessNetwork)) {
                throw new IllegalStateException("Can only reassign ports for a SubProcessNetwork");
            }
            subProcessNetwork.assignNewPorts();
        }
        return super.submitOp(spec);
    }

    @Override
    protected void run(@NonNull final HederaNode node) {
        log.info("Starting node '{}'", node.getName());
        try {
            switch (useUpgradeJar) {
                case YES -> {
                    if (!(node instanceof SubProcessNode subProcessNode)) {
                        throw new IllegalStateException("Node is not a SubProcessNode");
                    }
                    final var upgradeJar =
                            node.getExternalPath(UPGRADE_ARTIFACTS_DIR).resolve(JAR_FILE);
                    subProcessNode.startWithJar(upgradeJar);
                }
                case NO -> node.start();
            }
        } catch (Exception e) {
            log.error("Node '{}' failed to start", node, e);
            Assertions.fail("Node " + node + " failed to start (" + e.getMessage() + ")");
        }
        log.info("Node '{}' has started", node.getName());
    }
}
